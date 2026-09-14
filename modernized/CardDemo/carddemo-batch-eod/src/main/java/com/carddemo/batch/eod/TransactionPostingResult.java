package com.carddemo.batch.eod;

import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.TransactionEntity;

/**
 * Carries the outcome of {@link TransactionPostingProcessor} for one DALYTRAN record.
 *
 * For valid records: contains the prepared {@link TransactionEntity} and the loaded
 * {@link AccountEntity} (with updated balances pre-computed) for atomic persistence.
 * For invalid records: contains the reject code and description only.
 *
 * COBOL analogy: WS-VALIDATION-FAIL-REASON + REJECT-RECORD vs. 2000-POST-TRANSACTION path.
 */
public record TransactionPostingResult(
    DailyTransactionRecord source,
    ValidationStatus status,
    int rejectCode,            // 0=valid, 100/101/102/103
    String rejectDescription,  // "" when valid
    TransactionEntity preparedTransaction, // null when invalid
    AccountEntity accountToUpdate,         // null when invalid
    Long xrefAccountId                     // null when CARD_NOT_FOUND
) {
    public boolean isValid() { return status == ValidationStatus.VALID; }
}
