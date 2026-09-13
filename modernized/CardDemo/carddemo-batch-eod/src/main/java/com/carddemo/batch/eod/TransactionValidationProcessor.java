package com.carddemo.batch.eod;

import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXRefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Validates each DALYTRAN record against XREF-FILE and ACCOUNT-FILE.
 *
 * Legacy: CBTRN01C.cbl paragraphs 2000-LOOKUP-XREF (lines 227–239) and
 * 3000-READ-ACCOUNT (lines 241–250).
 *
 * RULE-063: card must exist in XREF → ValidationStatus.CARD_NOT_FOUND if not found
 * RULE-064: account must exist in ACCOUNT_FILE → ValidationStatus.ACCOUNT_NOT_FOUND if not found
 */
@Component
public class TransactionValidationProcessor
        implements ItemProcessor<DailyTransactionRecord, TransactionValidationResult> {

    private static final Logger log = LoggerFactory.getLogger(TransactionValidationProcessor.class);

    private final CardXRefRepository cardXRefRepository;
    private final AccountRepository accountRepository;

    public TransactionValidationProcessor(CardXRefRepository cardXRefRepository,
                                          AccountRepository accountRepository) {
        this.cardXRefRepository = cardXRefRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Replicates CBTRN01C paragraphs 2000-LOOKUP-XREF and 3000-READ-ACCOUNT.
     *
     * COBOL branching:
     *   INVALID KEY on XREF → WS-XREF-READ-STATUS=4 → DISPLAY "COULD NOT BE VERIFIED"
     *   INVALID KEY on ACCOUNT → WS-ACCT-READ-STATUS=4 → DISPLAY "ACCOUNT … NOT FOUND"
     *   Both found → DISPLAY "SUCCESSFUL READ OF ACCOUNT FILE"
     */
    @Override
    public TransactionValidationResult process(DailyTransactionRecord record) {
        // COBOL 2000-LOOKUP-XREF: READ XREF-FILE KEY IS FD-XREF-CARD-NUM
        var xref = cardXRefRepository.findById(record.cardNumber());
        if (xref.isEmpty()) {
            log.warn("XREF lookup failed (RULE-063): cardNumber={}, transactionId={}",
                record.cardNumber(), record.transactionId());
            return new TransactionValidationResult(record, ValidationStatus.CARD_NOT_FOUND, null);
        }

        // COBOL 3000-READ-ACCOUNT: READ ACCOUNT-FILE KEY IS FD-ACCT-ID
        Long accountId = xref.get().getAccountId();
        var account = accountRepository.findById(accountId);
        if (account.isEmpty()) {
            log.warn("Account lookup failed (RULE-064): accountId={}, transactionId={}",
                accountId, record.transactionId());
            return new TransactionValidationResult(record, ValidationStatus.ACCOUNT_NOT_FOUND, accountId);
        }

        log.debug("Transaction valid: transactionId={}, cardNumber={}, accountId={}",
            record.transactionId(), record.cardNumber(), accountId);
        return new TransactionValidationResult(record, ValidationStatus.VALID, accountId);
    }
}
