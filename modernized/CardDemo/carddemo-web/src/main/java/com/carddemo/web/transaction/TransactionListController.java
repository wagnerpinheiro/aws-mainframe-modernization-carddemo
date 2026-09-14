package com.carddemo.web.transaction;

import org.springframework.security.access.prepost.PreAuthorize;
import com.carddemo.domain.repository.CardXRefRepository;
import com.carddemo.domain.repository.TransactionRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Replicates COTRN00C — transaction list screen.
 * Lists transactions for a given card number.
 *
 * SEC-018: Restricted to ROLE_ADMIN until user→card ownership link is implemented.
 * Production path: resolve principal's cardNumber via CardXRefRepository and assert
 * the requested cardNumber belongs to the authenticated user.
 */
@Controller
@RequestMapping("/transactions")
@PreAuthorize("hasRole('ADMIN')")
public class TransactionListController {

    private final TransactionService transactionService;

    public TransactionListController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String cardNumber, Model model) {
        if (cardNumber != null && !cardNumber.isBlank()) {
            model.addAttribute("transactions", transactionService.findByCard(cardNumber));
            model.addAttribute("cardNumber", cardNumber);
        }
        return "transactions/list";
    }
}
