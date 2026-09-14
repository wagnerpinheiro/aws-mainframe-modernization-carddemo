package com.carddemo.web.account;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Read-only account view. Corresponds to COACTVWC.cbl (941 LOC).
 *
 * COACTVWC reads ACCTDATA (random) and CUSTFILE (via XREF) and displays the result.
 * No write logic, no validation. Simplest D3 program — used as Phase 4 pilot.
 *
 * NOTE: CODING-TO-BE-DONE sentinel present in legacy; no activated functionality gap identified.
 */
@Controller
@RequestMapping("/accounts")
public class AccountViewController {

    private final AccountQueryService accountQueryService;

    public AccountViewController(AccountQueryService accountQueryService) {
        this.accountQueryService = accountQueryService;
    }

    /** GET /accounts/{id} — display account details (COACTVWC 1000-GET-ACCT-DATA). */
    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        var account = accountQueryService.findAccount(id);
        if (account.isEmpty()) {
            model.addAttribute("error", "Account not found: " + id);
            return "account/not-found";
        }
        var customer = accountQueryService.findCustomerForAccount(id);
        ScreenRenderer.populateAccountView(model, account.get(), customer.orElse(null));
        return "account/view";
    }
}
