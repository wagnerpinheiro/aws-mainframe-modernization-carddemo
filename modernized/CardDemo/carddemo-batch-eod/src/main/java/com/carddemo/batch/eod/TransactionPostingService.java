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

    // TODO(step2): implement post() method
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void post(TransactionPostingResult result) {
        throw new UnsupportedOperationException("TransactionPostingService.post() not yet implemented");
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
        var account = result.accountToUpdate();
        account.setCurrentBalance(account.getCurrentBalance().add(result.source().amount()));
        if (result.source().amount().compareTo(java.math.BigDecimal.ZERO) >= 0) {
            account.setCurrCycleCredit(account.getCurrCycleCredit().add(result.source().amount()));
        } else {
            account.setCurrCycleDebit(account.getCurrCycleDebit().add(result.source().amount()));
        }
        accountRepository.save(account);
    }
}
