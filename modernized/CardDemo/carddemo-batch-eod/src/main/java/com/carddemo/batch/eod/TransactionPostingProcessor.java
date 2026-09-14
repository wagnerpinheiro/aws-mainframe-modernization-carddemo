package com.carddemo.batch.eod;

import com.carddemo.common.InvalidDataException;
import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.TransactionEntity;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXRefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Replicates CBTRN02C paragraph 1500-VALIDATE-TRAN and 2000-POST-TRANSACTION
 * (data preparation only — persistence is in {@link TransactionPostingService}).
 *
 * Validation sequence (matches COBOL exactly — both checks always run, no short-circuit):
 *   1500-A: card in XREF? No → reject 100; stop validation
 *   1500-B: account exists? No → reject 101
 *            RULE-065 overlimit? → set reject 102 (may be overwritten)
 *            RULE-066 expired?   → set reject 103 (overwrites 102 if both fail — COBOL last-write-wins)
 *
 * RULE-059: ACCT-ACTIVE-STATUS is NOT checked (legacy behavior replicated).
 * TODO(prod): should inactive accounts ('N') block posting? (RULE-059)
 */
@Component
public class TransactionPostingProcessor
        implements ItemProcessor<DailyTransactionRecord, TransactionPostingResult> {

    private static final Logger log = LoggerFactory.getLogger(TransactionPostingProcessor.class);
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter PROC_TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.000000");

    private final CardXRefRepository cardXRefRepository;
    private final AccountRepository accountRepository;

    public TransactionPostingProcessor(CardXRefRepository cardXRefRepository,
                                       AccountRepository accountRepository) {
        this.cardXRefRepository = cardXRefRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Replicates CBTRN02C paragraphs 1500-A-LOOKUP-XREF, 1500-B-LOOKUP-ACCT,
     * and 2000-POST-TRANSACTION (data preparation only).
     */
    @Override
    public TransactionPostingResult process(DailyTransactionRecord record) {
        // 1500-A-LOOKUP-XREF: READ XREF-FILE KEY IS FD-XREF-CARD-NUM INVALID KEY...
        var xref = cardXRefRepository.findById(record.cardNumber());
        if (xref.isEmpty()) {
            log.warn("XREF lookup failed (reject 100 — RULE-063): cardNumber={}, transactionId={}",
                record.cardNumber(), record.transactionId());
            return reject(record, 100, "INVALID CARD NUMBER FOUND",
                ValidationStatus.CARD_NOT_FOUND, null);
        }

        Long accountId = xref.get().getAccountId();

        // 1500-B-LOOKUP-ACCT: READ ACCOUNT-FILE KEY IS FD-ACCT-ID INVALID KEY...
        var accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            log.warn("Account lookup failed (reject 101 — RULE-064): accountId={}, transactionId={}",
                accountId, record.transactionId());
            return reject(record, 101, "ACCOUNT RECORD NOT FOUND",
                ValidationStatus.ACCOUNT_NOT_FOUND, accountId);
        }

        AccountEntity account = accountOpt.get();
        int rejectCode = 0;
        String rejectDesc = "";
        ValidationStatus rejectStatus = ValidationStatus.VALID;

        // RULE-065 overlimit check — always runs when account is found
        // WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT − ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT
        // IF ACCT-CREDIT-LIMIT >= WS-TEMP-BAL CONTINUE ELSE reject 102
        BigDecimal tempBal = account.getCurrCycleCredit()
            .subtract(account.getCurrCycleDebit())
            .add(record.amount());
        if (account.getCreditLimit().compareTo(tempBal) < 0) {
            rejectCode = 102;
            rejectDesc = "OVERLIMIT TRANSACTION";
            rejectStatus = ValidationStatus.OVERLIMIT;
        }

        // RULE-066 expiry check — always runs; overwrites 102 if both fail (COBOL last-write-wins)
        // IF ACCT-EXPIRAION-DATE >= DALYTRAN-ORIG-TS(1:10) CONTINUE ELSE reject 103
        // Per Q5 premise: strict LocalDate parsing; malformed date → InvalidDataException
        try {
            LocalDate expiryDate = LocalDate.parse(account.getExpirationDate(), ISO_DATE);
            LocalDate originDate = LocalDate.parse(record.originTimestamp().substring(0, 10), ISO_DATE);
            if (expiryDate.isBefore(originDate)) {
                rejectCode = 103;
                rejectDesc = "TRANSACTION RECEIVED AFTER ACCT EXPIRATION";
                rejectStatus = ValidationStatus.EXPIRED;
            }
        } catch (DateTimeParseException e) {
            throw new InvalidDataException(
                "Invalid date in expiry/origin: expirationDate=[" + account.getExpirationDate()
                + "] originTs(0:10)=[" + record.originTimestamp().substring(0, 10) + "]", e);
        }

        if (rejectCode != 0) {
            log.warn("Validation reject (code {} — {}): transactionId={}",
                rejectCode, rejectDesc, record.transactionId());
            return reject(record, rejectCode, rejectDesc, rejectStatus, accountId);
        }

        // RULE-059: ACCT-ACTIVE-STATUS='N' does not block posting (legacy behavior).
        // TODO(prod): confirm active-status policy for batch posting. (RULE-059)

        log.debug("Transaction valid: transactionId={}, cardNumber={}, accountId={}",
            record.transactionId(), record.cardNumber(), accountId);

        return new TransactionPostingResult(
            record, ValidationStatus.VALID, 0, "",
            buildTransaction(record), account, accountId);
    }

    private TransactionPostingResult reject(DailyTransactionRecord record, int code,
                                             String desc, ValidationStatus status, Long accountId) {
        return new TransactionPostingResult(record, status, code, desc, null, null, accountId);
    }

    private TransactionEntity buildTransaction(DailyTransactionRecord record) {
        // Z-GET-DB2-FORMAT-TIMESTAMP: set processing timestamp at posting time
        String procTs = LocalDateTime.now().format(PROC_TS_FMT);
        return new TransactionEntity(
            record.transactionId(), record.typeCode(), record.categoryCode(),
            record.source(), record.description(), record.amount(),
            record.merchantId(), record.merchantName(), record.merchantCity(),
            record.merchantZip(), record.cardNumber(), record.originTimestamp(), procTs);
    }
}
