package com.carddemo.web.billing;

import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.TransactionEntity;
import com.carddemo.domain.repository.AccountRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * Replicates COBIL00C — online bill payment.
 *
 * Flow (mirrors COBOL PROCESS-ENTER-KEY logic):
 *   GET /billing           → show form
 *   POST /billing/confirm  → validate, require accountId, show confirmation
 *   POST /billing/pay      → execute payment (RULE-010), block if zero balance (RULE-011)
 *
 * RULE-010: payment = full current balance.
 * RULE-011: zero balance → HTTP 400 / form error.
 * RULE-012: transaction ID from DB sequence (in BillPaymentService).
 */
@Controller
@RequestMapping("/billing")
public class BillPaymentController {

    private final BillPaymentService billPaymentService;
    private final AccountRepository accountRepository;

    public BillPaymentController(BillPaymentService billPaymentService,
                                 AccountRepository accountRepository) {
        this.billPaymentService = billPaymentService;
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public String showForm(Model model) {
        return "billing/pay";
    }

    /**
     * Confirmation step: show account balance before committing payment.
     * Mirrors COBOL "Confirm to make a bill payment..." message when CONF-PAY-NO.
     */
    @PostMapping("/confirm")
    public String confirm(@RequestParam Long accountId, Model model) {
        Optional<AccountEntity> account = accountRepository.findById(accountId);
        if (account.isEmpty()) {
            model.addAttribute("error", "Account ID NOT found...");
            return "billing/pay";
        }
        model.addAttribute("account", account.get());
        model.addAttribute("accountId", accountId);
        return "billing/confirm";
    }

    /**
     * Execute payment after user confirms with Y (RULE-010/011/012).
     */
    @PostMapping("/pay")
    public String pay(@RequestParam Long accountId,
                      @RequestParam(defaultValue = "N") String confirmed,
                      Model model,
                      RedirectAttributes redirectAttrs) {
        if (!"Y".equalsIgnoreCase(confirmed)) {
            model.addAttribute("error", "Confirm to make a bill payment...");
            model.addAttribute("accountId", accountId);
            return "billing/pay";
        }
        try {
            TransactionEntity tran = billPaymentService.pay(accountId);
            redirectAttrs.addFlashAttribute("success",
                "Payment successful. Your Transaction ID is " + tran.getId());
            return "redirect:/billing";
        } catch (ZeroBalanceException e) {
            model.addAttribute("error", e.getMessage()); // RULE-011
            return "billing/pay";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", "Account ID NOT found...");
            return "billing/pay";
        }
    }
}
