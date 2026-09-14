package com.carddemo.web.card;

import com.carddemo.domain.repository.CardRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Card list screen. Corresponds to COCRDLIC.cbl.
 *
 * Scope enforcement: non-admin sees only cards for their account.
 * Admin sees all cards (or cards for a specific account).
 *
 * RULE-060: card active status domain (Y/N) shown in list.
 * SEC-005: no CVV field in any response.
 *
 * NOTE: CODING-TO-BE-DONE sentinel present in legacy; no activated functionality gap identified.
 */
@Controller
@RequestMapping("/accounts/{accountId}/cards")
public class CardListController {

    private final CardRepository cardRepository;

    public CardListController(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    /** GET /accounts/{accountId}/cards — list all cards for an account. */
    @GetMapping
    public String listCards(@PathVariable Long accountId, Model model) {
        var cards = cardRepository.findByAccountIdOrderByCardNumberAsc(accountId);
        model.addAttribute("cards", cards);
        model.addAttribute("accountId", accountId);
        return "card/list";
    }

    /** GET /admin/cards — admin-only: list all cards across all accounts. */
    @GetMapping("/admin/cards")
    @PreAuthorize("hasRole('ADMIN')")
    public String listAllCards(Model model) {
        model.addAttribute("cards", cardRepository.findAllByOrderByCardNumberAsc());
        return "card/list";
    }
}
