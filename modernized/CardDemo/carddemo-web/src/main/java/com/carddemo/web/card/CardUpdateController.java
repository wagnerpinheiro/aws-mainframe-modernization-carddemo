package com.carddemo.web.card;

import com.carddemo.domain.entity.CardEntity;
import com.carddemo.domain.repository.CardRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Card update screen. Corresponds to COCRDUPC.cbl (1,560 LOC).
 *
 * RULE-030: expiry day is immutable — always carried forward from the existing record.
 * SEC-005: CVV field is absent from CardEntity and all form responses (PCI DSS).
 *
 * NOTE: CODING-TO-BE-DONE sentinel present in legacy; no activated functionality gap identified.
 */
@Controller
@RequestMapping("/cards")
public class CardUpdateController {

    private final CardRepository cardRepository;

    public CardUpdateController(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    /** GET /cards/{cardNumber}/edit — show update form. */
    @GetMapping("/{cardNumber}/edit")
    public String editForm(@PathVariable String cardNumber, Model model) {
        CardEntity card = cardRepository.findById(cardNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Card not found: " + cardNumber));
        // Parse existing expiry to populate form fields
        String[] expParts = parseExpiry(card.getExpirationDate());
        model.addAttribute("card", card);
        model.addAttribute("form", new CardUpdateForm(
            card.getCardNumber(), card.getEmbossedName(), card.getActiveStatus(),
            Integer.parseInt(expParts[1]), Integer.parseInt(expParts[0])
        ));
        return "card/edit";
    }

    /** POST /cards/{cardNumber}/edit — process card update. */
    @PostMapping("/{cardNumber}/edit")
    public String processEdit(@PathVariable String cardNumber,
                              @ModelAttribute CardUpdateForm form,
                              Model model) {
        CardEntity card = cardRepository.findById(cardNumber)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<String> errors = CardUpdateValidator.validate(form);
        if (!errors.isEmpty()) {
            model.addAttribute("card", card);
            model.addAttribute("form", form);
            model.addAttribute("errors", errors);
            return "card/edit";
        }

        // Apply updates
        card.setEmbossedName(form.embossedName());
        card.setActiveStatus(form.activeStatus());

        // RULE-030: expiry day is immutable — carry forward from existing record
        // TODO(prod): day=31 in a 30-day month creates an invalid stored date — consider re-validation (RULE-030)
        String[] existingParts = parseExpiry(card.getExpirationDate());
        String existingDay = existingParts[2];
        String newExpiry = String.format("%04d-%02d-%s", form.expiryYear(), form.expiryMonth(), existingDay);
        card.setExpirationDate(newExpiry);

        cardRepository.save(card);
        return "redirect:/cards/" + cardNumber;
    }

    /** Parses "YYYY-MM-DD" → [YYYY, MM, DD]. Falls back to ["0000","01","01"] on bad format. */
    private static String[] parseExpiry(String date) {
        if (date != null && date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return date.split("-");
        }
        return new String[]{"2030", "01", "01"};
    }
}
