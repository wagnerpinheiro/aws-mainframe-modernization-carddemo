package com.carddemo.web.transaction;

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
 * Lists transactions for a given card number (RULE-022 page size not implemented for PoC).
 */
@Controller
@RequestMapping("/transactions")
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
