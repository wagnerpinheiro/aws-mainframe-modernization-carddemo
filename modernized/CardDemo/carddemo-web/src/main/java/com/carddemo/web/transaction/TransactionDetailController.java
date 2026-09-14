package com.carddemo.web.transaction;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Replicates COTRN01C — single transaction detail screen.
 */
@Controller
@RequestMapping("/transactions")
public class TransactionDetailController {

    private final TransactionService transactionService;

    public TransactionDetailController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/{tranId}")
    public String detail(@PathVariable String tranId, Model model) {
        try {
            model.addAttribute("transaction", transactionService.findById(tranId));
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "transactions/detail";
    }
}
