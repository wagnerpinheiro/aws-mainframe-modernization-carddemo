package com.carddemo.web.card;

import org.springframework.security.access.prepost.PreAuthorize;
import com.carddemo.domain.repository.CardRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Read-only card detail screen. Corresponds to COCRDSLC.cbl.
 *
 * SEC-016: Restricted to ROLE_ADMIN until user→card ownership link is implemented.
 * RULE-031: card must exist in CARDDAT before detail is shown.
 * SEC-005: CVV field absent — not queried, not displayed.
 *
 * NOTE: CODING-TO-BE-DONE sentinel present in legacy; no activated functionality gap identified.
 */
@Controller
@RequestMapping("/cards")
@PreAuthorize("hasRole('ADMIN')")
public class CardDetailController {

    private final CardRepository cardRepository;

    public CardDetailController(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    /** GET /cards/{cardNumber} — display card detail (COCRDSLC). */
    @GetMapping("/{cardNumber}")
    public String detail(@PathVariable String cardNumber, Model model) {
        // RULE-031: card must exist
        var card = cardRepository.findById(cardNumber);
        if (card.isEmpty()) {
            model.addAttribute("error", "Card not found: " + cardNumber);
            return "card/not-found";
        }
        model.addAttribute("card", card.get());
        return "card/detail";
    }
}
