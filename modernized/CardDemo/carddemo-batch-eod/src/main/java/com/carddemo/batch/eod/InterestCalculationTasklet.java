package com.carddemo.batch.eod;

import com.carddemo.domain.entity.DiscountGroupEntity;
import com.carddemo.domain.entity.DiscountGroupId;
import com.carddemo.domain.entity.TranCatBalanceEntity;
import com.carddemo.domain.entity.TransactionEntity;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXRefRepository;
import com.carddemo.domain.repository.DiscountGroupRepository;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import com.carddemo.domain.repository.TransactionRepository;
import com.carddemo.domain.entity.AccountEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring Batch Tasklet equivalent of CBACT04C.cbl (652 LOC).
 *
 * Algorithm (replicates COBOL sequential TCATBAL processing grouped by account):
 * 1. Read all TCATBAL records ordered by (accountId, typeCode, categoryCode).
 * 2. Group by accountId (preserving insertion order).
 * 3. For each account group:
 *    a. Load account (1100-GET-ACCT-DATA) and card number via XREF alternate key (1110-GET-XREF-DATA).
 *    b. For each category balance entry:
 *       - Look up DISCGRP rate by (groupId, typeCode, categoryCode) (1200-GET-INTEREST-RATE).
 *       - If not found: retry with groupId="DEFAULT" (1200-A-GET-DEFAULT-INT-RATE).
 *       - If rate != 0: compute monthly interest and write interest TransactionEntity (1300/1300-B).
 *    c. Update account: ACCT-CURR-BAL += totalInterest, reset cycle accumulators (1050-UPDATE-ACCOUNT).
 *
 * 1400-COMPUTE-FEES is a no-op stub in the COBOL ("To be implemented").
 *
 * RULE-007: RoundingMode.DOWN — replicates COBOL COMPUTE without ROUNDED (truncates toward zero).
 *   TODO(prod): production policy may require HALF_UP or HALF_EVEN. (Q2 premise)
 * RULE-008: account balance updated after interest run.
 * RULE-009: ACCT-CURR-CYC-CREDIT and ACCT-CURR-CYC-DEBIT reset to 0 after interest run.
 */
@Component
public class InterestCalculationTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(InterestCalculationTasklet.class);
    private static final DateTimeFormatter PROC_TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.000000");
    private static final String DEFAULT_GROUP = "DEFAULT";

    private final TranCatBalanceRepository tranCatBalanceRepository;
    private final AccountRepository accountRepository;
    private final CardXRefRepository cardXRefRepository;
    private final DiscountGroupRepository discountGroupRepository;
    private final TransactionRepository transactionRepository;

    public InterestCalculationTasklet(TranCatBalanceRepository tranCatBalanceRepository,
                                      AccountRepository accountRepository,
                                      CardXRefRepository cardXRefRepository,
                                      DiscountGroupRepository discountGroupRepository,
                                      TransactionRepository transactionRepository) {
        this.tranCatBalanceRepository = tranCatBalanceRepository;
        this.accountRepository = accountRepository;
        this.cardXRefRepository = cardXRefRepository;
        this.discountGroupRepository = discountGroupRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<TranCatBalanceEntity> allBalances =
            tranCatBalanceRepository.findAllByOrderByIdAccountIdAscIdTypeCodeAscIdCategoryCodeAsc();

        if (allBalances.isEmpty()) {
            log.info("No TCATBAL records found — nothing to process (CBACT04C early exit)");
            return RepeatStatus.FINISHED;
        }

        // Group by accountId in order (replicates COBOL sequential read with account-change detection)
        Map<Long, List<TranCatBalanceEntity>> byAccount = new LinkedHashMap<>();
        for (TranCatBalanceEntity bal : allBalances) {
            byAccount.computeIfAbsent(bal.getId().getAccountId(), k -> new ArrayList<>()).add(bal);
        }

        // Z-GET-DB2-FORMAT-TIMESTAMP: generate date parameter for transaction IDs
        String dateParam = LocalDate.now().toString(); // YYYY-MM-DD (10 chars)
        AtomicInteger suffix = new AtomicInteger(0);  // WS-TRANID-SUFFIX 9(6)

        for (Map.Entry<Long, List<TranCatBalanceEntity>> entry : byAccount.entrySet()) {
            processAccount(entry.getKey(), entry.getValue(), dateParam, suffix);
        }

        log.info("Interest calculation complete. Accounts processed: {}, transactions written: {}",
            byAccount.size(), suffix.get());
        return RepeatStatus.FINISHED;
    }

    private void processAccount(Long accountId, List<TranCatBalanceEntity> balances,
                                 String dateParam, AtomicInteger suffix) {
        // 1100-GET-ACCT-DATA
        var account = accountRepository.findById(accountId).orElseThrow(
            () -> new IllegalStateException("Account not found during interest calc: " + accountId));

        // 1110-GET-XREF-DATA: read XREF by alternate key ACCT-ID
        String cardNumber = cardXRefRepository.findByAccountId(accountId)
            .map(xref -> xref.getCardNumber())
            .orElse("");

        BigDecimal totalInterest = BigDecimal.ZERO;
        String groupId = account.getGroupId() != null ? account.getGroupId().strip() : "";

        for (TranCatBalanceEntity bal : balances) {
            // 1200-GET-INTEREST-RATE
            BigDecimal rate = lookupRate(groupId,
                bal.getId().getTypeCode(), bal.getId().getCategoryCode());

            // IF DIS-INT-RATE NOT = 0
            if (rate.compareTo(BigDecimal.ZERO) != 0) {
                // 1300-COMPUTE-INTEREST
                // RULE-007: RoundingMode.DOWN matches COBOL COMPUTE without ROUNDED.
                // TODO(prod): production policy may require HALF_UP or HALF_EVEN. (Q2 premise)
                BigDecimal monthlyInterest = bal.getBalance()
                    .multiply(rate)
                    .divide(new BigDecimal("1200"), 2, RoundingMode.DOWN);

                totalInterest = totalInterest.add(monthlyInterest);

                // 1300-B-WRITE-TX
                writInterestTransaction(accountId, cardNumber, monthlyInterest, dateParam, suffix);
            }
            // 1400-COMPUTE-FEES — no-op stub in COBOL ("To be implemented")
        }

        // 1050-UPDATE-ACCOUNT: ADD WS-TOTAL-INT TO ACCT-CURR-BAL; reset cycle accumulators
        account.setCurrentBalance(account.getCurrentBalance().add(totalInterest)); // RULE-008
        account.setCurrCycleCredit(BigDecimal.ZERO);  // RULE-009
        account.setCurrCycleDebit(BigDecimal.ZERO);   // RULE-009
        accountRepository.save(account);

        log.debug("Account {} processed: totalInterest={}, transactions={}",
            accountId, totalInterest, suffix.get());
    }

    /**
     * Replicates 1200-GET-INTEREST-RATE + 1200-A-GET-DEFAULT-INT-RATE.
     * Tries (groupId, typeCode, catCode) first; if not found, retries with "DEFAULT" group.
     */
    private BigDecimal lookupRate(String groupId, String typeCode, Integer categoryCode) {
        Optional<DiscountGroupEntity> found = discountGroupRepository
            .findById(new DiscountGroupId(groupId, typeCode, categoryCode));

        if (found.isEmpty() && !DEFAULT_GROUP.equals(groupId)) {
            // 1200-A-GET-DEFAULT-INT-RATE: MOVE 'DEFAULT' TO FD-DIS-ACCT-GROUP-ID; READ DISCGRP-FILE
            found = discountGroupRepository
                .findById(new DiscountGroupId(DEFAULT_GROUP, typeCode, categoryCode));
        }

        return found.map(DiscountGroupEntity::getInterestRate).orElse(BigDecimal.ZERO);
    }

    /**
     * Replicates CBACT04C 1300-B-WRITE-TX: builds and saves the interest TransactionEntity.
     *
     * TRAN-ID = PARM-DATE (YYYY-MM-DD, 10 chars) + WS-TRANID-SUFFIX (6-digit counter).
     * TRAN-TYPE-CD = '01', TRAN-CAT-CD = 5 (MOVE '05' TO TRAN-CAT-CD).
     * TRAN-SOURCE = 'System', TRAN-DESC = 'Int. for a/c XXXXXXXXXX'.
     */
    private void writInterestTransaction(Long accountId, String cardNumber,
                                          BigDecimal amount, String dateParam,
                                          AtomicInteger suffix) {
        int seq = suffix.incrementAndGet();
        String tranId = String.format("%s%06d", dateParam, seq); // 10 + 6 = 16 chars
        String procTs = LocalDateTime.now().format(PROC_TS_FMT);
        String desc = String.format("Int. for a/c %011d", accountId);

        transactionRepository.save(new TransactionEntity(
            tranId, "01", 5, "System", desc,
            amount, 0L, "", "", "",
            cardNumber, procTs, procTs));
    }
}
