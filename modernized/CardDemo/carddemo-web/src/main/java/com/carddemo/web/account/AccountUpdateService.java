package com.carddemo.web.account;

import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.CustomerEntity;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomically saves account + customer in one @Transactional method.
 *
 * RULE-061 (TD-07): COACTUPC performs two sequential CICS REWRITEs (ACCTDAT then CUSTDAT).
 * If the second REWRITE fails, EXEC CICS SYNCPOINT ROLLBACK undoes both.
 * Java fix: both saves are in one @Transactional — if any save fails, both roll back.
 *
 * Extracted as decomposition step 3, before AccountUpdateController is written.
 *
 * NOTE: CODING-TO-BE-DONE sentinel present in legacy; no activated functionality gap identified.
 */
@Service
public class AccountUpdateService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public AccountUpdateService(AccountRepository accountRepository,
                                CustomerRepository customerRepository) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    /**
     * Saves account and customer atomically.
     * Throws {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * if the account version has changed since the form was loaded (RULE-053).
     */
    @Transactional
    public void saveAccountAndCustomer(AccountEntity account, CustomerEntity customer) {
        accountRepository.save(account);
        if (customer != null) {
            customerRepository.save(customer);
        }
    }
}
