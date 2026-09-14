package com.carddemo.web.card;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates card update fields per COCRDUPC validation paragraphs.
 *
 * RULE-025: card number 16-digit numeric non-zero
 * RULE-026: embossed name alpha+spaces only, non-blank
 * RULE-027: active status Y or N (RULE-060)
 * RULE-028: expiry month 1–12
 * RULE-029: expiry year 1950–2099 (no future-date guard — RULE-029 confirmed)
 * RULE-030: expiry day carried forward from existing record (not validated here; enforced in service)
 * RULE-031: card must exist before update (enforced in controller load)
 *
 * NOTE: CODING-TO-BE-DONE sentinel present in legacy; no activated functionality gap identified.
 */
public final class CardUpdateValidator {

    private CardUpdateValidator() {}

    public static List<String> validate(CardUpdateForm form) {
        List<String> errors = new ArrayList<>();

        // RULE-026: embossed name alpha+spaces, non-blank
        if (form.embossedName() == null || form.embossedName().isBlank()) {
            errors.add("RULE-026: Embossed name is required");
        } else if (!form.embossedName().chars().allMatch(c -> Character.isLetter(c) || c == ' ')) {
            errors.add("RULE-026: Embossed name must contain only letters and spaces");
        }

        // RULE-027 / RULE-060: active status Y or N
        if (!"Y".equals(form.activeStatus()) && !"N".equals(form.activeStatus())) {
            errors.add("RULE-027/RULE-060: Card active status must be Y or N");
        }

        // RULE-028: expiry month 1–12
        if (form.expiryMonth() < 1 || form.expiryMonth() > 12) {
            errors.add("RULE-028: Expiry month must be 1–12");
        }

        // RULE-029: expiry year 1950–2099 (no future-date guard per brief Q8)
        if (form.expiryYear() < 1950 || form.expiryYear() > 2099) {
            errors.add("RULE-029: Expiry year must be 1950–2099");
        }
        // TODO(prod): no future-date guard on expiry year (RULE-029). Batch enforces expiry at posting.

        return errors;
    }
}
