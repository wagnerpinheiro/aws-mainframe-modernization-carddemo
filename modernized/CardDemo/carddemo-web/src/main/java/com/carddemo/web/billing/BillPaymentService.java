package com.carddemo.web.billing;

import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.TransactionEntity;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXRefRepository;
import com.carddemo.domain.repository.TransactionRepository;
import com.carddemo.web.transaction.TransactionIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Replicates COBIL00C PROCESS-ENTER-KEY (CONF-PAY-YES branch).
 *
 * RULE-010: payment amount = full current balance (COMPUTE ACCT-CURR-BAL - TRAN-AMT).
 * RULE-011: zero or negative balance → payment blocked (checked before calling pay()).
 * RULE-012 fix: DB sequence replaces READPREV+increment for transaction ID generation.
 */
@Service
public class BillPaymentService {

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.000000");

    private final AccountRepository accountRepository;
    private final CardXRefRepository cardXRefRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionIdGenerator idGenerator;

    public BillPaymentService(AccountRepository accountRepository,
                              CardXRefRepository cardXRefRepository,
                              TransactionRepository transactionRepository,
                              TransactionIdGenerator idGenerator) {
        this.accountRepository = accountRepository;
        this.cardXRefRepository = cardXRefRepository;
        this.transactionRepository = transactionRepository;
        this.idGenerator = idGenerator;
    }

    /**
     * Pays the full account balance. Returns the saved TransactionEntity.
     *
     * COBOL: MOVE ACCT-CURR-BAL TO TRAN-AMT; COMPUTE ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT.
     */
    @Transactional
    public TransactionEntity pay(Long accountId) {
        AccountEntity account = accountRepository.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        // RULE-011: zero balance guard
        if (account.getCurrentBalance().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ZeroBalanceException("You have nothing to pay (balance = "
                + account.getCurrentBalance() + ")");
        }

        BigDecimal paymentAmount = account.getCurrentBalance(); // RULE-010: full balance

        String cardNumber = cardXRefRepository.findByAccountId(accountId)
            .map(x -> x.getCardNumber())
            .orElse("0000000000000000");

        String ts = LocalDateTime.now().format(TS_FMT);
        // RULE-012 fix: DB sequence, not READPREV+increment
        String tranId = idGenerator.nextId();

        // COBOL: MOVE '02' TO TRAN-TYPE-CD; MOVE 2 TO TRAN-CAT-CD
        TransactionEntity tran = new TransactionEntity(
            tranId, "02", 2, "POS TERM", "BILL PAYMENT - ONLINE",
            paymentAmount, 999999999L, "BILL PAYMENT", "N/A", "N/A",
            cardNumber, ts, ts
        );
        transactionRepository.save(tran);

        // COBOL: COMPUTE ACCT-CURR-BAL = ACCT-CURR-BAL - TRAN-AMT → 0
        account.setCurrentBalance(account.getCurrentBalance().subtract(paymentAmount));
        accountRepository.save(account);

        return tran;
    }
}
