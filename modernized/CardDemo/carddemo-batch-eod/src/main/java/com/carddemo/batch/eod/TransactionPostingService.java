package com.carddemo.batch.eod;

import com.carddemo.domain.entity.TranCatBalanceEntity;
import com.carddemo.domain.entity.TranCatBalanceId;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import com.carddemo.domain.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomically persists one validated transaction: TCATBAL update, account balance
 * update, and transaction record write — in the same order as CBTRN02C paragraphs
 * 2700/2800/2900, but wrapped in a single {@code @Transactional}.
 *
 * Isolation: SERIALIZABLE replicates the VSAM exclusive-lock semantics of
 * CBTRN02C's ACCOUNT-FILE I-O open (batch held the file exclusively).
 *
 * RULE-061 fix (TD-07): COBOL sequences TCATBAL→ACCOUNT→TRANSACT without rollback.
 * A REWRITE failure on ACCOUNT left TCATBAL already updated (partial state).
 * This method makes all three writes atomic: if any fails, all roll back.
 */
@Service
public class TransactionPostingService {

    private final TranCatBalanceRepository tranCatBalanceRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransactionPostingService(TranCatBalanceRepository tranCatBalanceRepository,
                                     AccountRepository accountRepository,
                                     TransactionRepository transactionRepository) {
        this.tranCatBalanceRepository = tranCatBalanceRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Atomically persists one validated DALYTRAN record:
     * TCATBAL update → account update → transaction write (CBTRN02C order: 2700/2800/2900).
     *
     * REQUIRES_NEW ensures SERIALIZABLE isolation takes effect even when called from
     * within the step's chunk transaction (which may use a different isolation level).
     * Each call is an independent unit-of-work; rollback affects only this one record.
     *
     * INSERT semantics for TransactionEntity (RULE-061): duplicate IDs throw
     * IllegalStateException, causing the entire sub-transaction to roll back — TCATBAL
     * and account balance are restored even though they were written before the exception.
     * This fixes the COBOL defect (TD-07) where REWRITE failure left partial state.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE,
                   propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void post(TransactionPostingResult result) {
        // 2700-UPDATE-TCATBAL: upsert (ACCT-ID + TYPE-CD + CAT-CD) key
        updateTcatBal(result);
        // 2800-UPDATE-ACCOUNT-REC: balance and cycle accumulator update
        updateAccount(result);
        // 2900-WRITE-TRANSACTION-FILE: INSERT only (VSAM WRITE — not REWRITE semantics)
        if (transactionRepository.existsById(result.preparedTransaction().getId())) {
            throw new IllegalStateException(
                "Duplicate transaction ID (RULE-061 rollback guard): "
                + result.preparedTransaction().getId());
        }
        transactionRepository.save(result.preparedTransaction());
    }

    private void updateTcatBal(TransactionPostingResult result) {
        TranCatBalanceId key = new TranCatBalanceId(
            result.xrefAccountId(),
            result.source().typeCode(),
            result.source().categoryCode()
        );
        tranCatBalanceRepository.findById(key).ifPresentOrElse(
            existing -> existing.setBalance(existing.getBalance().add(result.source().amount())),
            () -> tranCatBalanceRepository.save(new TranCatBalanceEntity(key, result.source().amount()))
        );
    }

    private void updateAccount(TransactionPostingResult result) {
        // Reload account INSIDE the REQUIRES_NEW transaction to get the current @Version.
        // The processor's snapshot may be stale if multiple transactions in one chunk target
        // the same account — reloading avoids OptimisticLockException across calls.
        var account = accountRepository.findById(result.xrefAccountId())
            .orElseThrow(() -> new IllegalStateException(
                "Account vanished during posting: " + result.xrefAccountId()));
        var amount = result.source().amount();
        account.setCurrentBalance(account.getCurrentBalance().add(amount));
        if (amount.compareTo(java.math.BigDecimal.ZERO) >= 0) {
            account.setCurrCycleCredit(account.getCurrCycleCredit().add(amount));
        } else {
            account.setCurrCycleDebit(account.getCurrCycleDebit().add(amount));
        }
        accountRepository.save(account);
    }
}
