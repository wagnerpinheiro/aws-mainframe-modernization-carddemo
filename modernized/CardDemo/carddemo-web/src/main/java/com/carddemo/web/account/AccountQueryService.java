package com.carddemo.web.account;

import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.CardXRefEntity;
import com.carddemo.domain.entity.CustomerEntity;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXRefRepository;
import com.carddemo.domain.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Read-only queries for the account view screen (COACTVWC).
 *
 * COACTVWC 1000-GET-ACCT-DATA: READ ACCOUNT-FILE (random by ACCT-ID)
 * COACTVWC 2000-GET-CUST-DATA: READ via XREF to get CUST-ID, then READ CUSTOMER-FILE
 *
 * NOTE: CODING-TO-BE-DONE sentinel present in legacy; no activated functionality gap identified.
 */
@Service
public class AccountQueryService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final CardXRefRepository cardXRefRepository;

    public AccountQueryService(AccountRepository accountRepository,
                               CustomerRepository customerRepository,
                               CardXRefRepository cardXRefRepository) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.cardXRefRepository = cardXRefRepository;
    }

    public Optional<AccountEntity> findAccount(Long accountId) {
        return accountRepository.findById(accountId);
    }

    /** Looks up customer via the account→XREF→customer chain. */
    public Optional<CustomerEntity> findCustomerForAccount(Long accountId) {
        return cardXRefRepository.findByAccountId(accountId)
            .flatMap(xref -> customerRepository.findById(xref.getCustomerId()));
    }
}
