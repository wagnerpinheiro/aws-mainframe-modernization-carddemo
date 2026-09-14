package com.carddemo.web.account;

import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.CustomerEntity;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Account update screen. Corresponds to COACTUPC.cbl (4,236 LOC, CCN 122).
 *
 * COACTUPC decomposition (brief mandated order):
 *  1. ScreenRenderer — extracts BMS map population
 *  2. AccountUpdateValidator — extracts all field validators (RULE-024–045)
 *  3. AccountUpdateService.saveAccountAndCustomer() — @Transactional (RULE-061)
 *  4. PhoneValidator — early-return logic (TD-09 GO TO spaghetti)
 *  5. This controller — uses all extracted components
 *
 * RULE-053: optimistic locking — stale form version → HTTP 409 Conflict.
 *
 * NOTE: CODING-TO-BE-DONE sentinel present in legacy; no activated functionality gap identified.
 */
@Controller
@RequestMapping("/accounts")
public class AccountUpdateController {

    private static final Logger log = LoggerFactory.getLogger(AccountUpdateController.class);

    private final AccountQueryService accountQueryService;
    private final AccountUpdateService accountUpdateService;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public AccountUpdateController(AccountQueryService accountQueryService,
                                   AccountUpdateService accountUpdateService,
                                   AccountRepository accountRepository,
                                   CustomerRepository customerRepository) {
        this.accountQueryService = accountQueryService;
        this.accountUpdateService = accountUpdateService;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    /** GET /accounts/{id}/edit — show update form. */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        var account = accountQueryService.findAccount(id);
        if (account.isEmpty()) {
            model.addAttribute("error", "Account not found: " + id);
            return "account/not-found";
        }
        var customer = accountQueryService.findCustomerForAccount(id);
        ScreenRenderer.populateAccountEditForm(model, account.get(), customer.orElse(null));
        return "account/edit";
    }

    /**
     * POST /accounts/{id}/edit — process update.
     *
     * Validates (RULE-024–045), checks optimistic lock (RULE-053),
     * saves atomically (RULE-061), or returns 409 on concurrent modification.
     */
    @PostMapping("/{id}/edit")
    public String processEdit(@PathVariable Long id,
                              @ModelAttribute AccountUpdateForm form,
                              @RequestParam(name = "version", required = false) Long formVersion,
                              Model model) {
        // Validate fields (RULE-024–045)
        List<String> errors = AccountUpdateValidator.validate(form);
        if (!errors.isEmpty()) {
            var account = accountQueryService.findAccount(id).orElseThrow();
            var customer = accountQueryService.findCustomerForAccount(id).orElse(null);
            ScreenRenderer.populateAccountEditForm(model, account, customer);
            model.addAttribute("errors", errors);
            return "account/edit";
        }

        // Load current account
        AccountEntity account = accountRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // RULE-053: optimistic lock — check version matches what was loaded when form was shown
        // formVersion bound via @RequestParam (avoids Spring MVC record-binding ambiguity)
        if (formVersion != null && !formVersion.equals(account.getVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Account was modified by another user. Please refresh and try again.");
        }

        // Apply account changes
        account.setActiveStatus(form.activeStatus());
        account.setCreditLimit(form.creditLimit());
        account.setCashCreditLimit(form.cashCreditLimit());

        // Apply customer changes
        CustomerEntity customer = accountQueryService.findCustomerForAccount(id).orElse(null);
        if (customer != null) {
            customer.setFirstName(form.firstName());
            customer.setMiddleName(form.middleName());
            customer.setLastName(form.lastName());
            customer.setAddrLine1(form.addrLine1());
            customer.setAddrLine2(form.addrLine2());
            customer.setAddrLine3(form.addrLine3());
            customer.setAddrStateCd(form.addrStateCd());
            customer.setAddrZip(form.addrZip());
            customer.setAddrCountryCd(form.addrCountryCd());
            customer.setPhoneNum1(form.phoneNum1());
            customer.setPhoneNum2(form.phoneNum2());
            customer.setFicoCreditScore(form.ficoCreditScore());
            customer.setEftAccountId(form.eftAccountId());
            customer.setPriCardHolderInd(form.priCardHolderInd());
        }

        try {
            // RULE-061: atomic rollback — both saves in one @Transactional
            accountUpdateService.saveAccountAndCustomer(account, customer);
        } catch (ObjectOptimisticLockingFailureException e) {
            // RULE-053: concurrent update detected by JPA @Version
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Account was modified concurrently. Please refresh and try again.");
        }

        return "redirect:/accounts/" + id;
    }
}
