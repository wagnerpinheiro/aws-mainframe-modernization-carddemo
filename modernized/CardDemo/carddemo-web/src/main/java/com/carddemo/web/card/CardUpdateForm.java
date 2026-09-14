package com.carddemo.web.card;

/**
 * Form DTO for COCRDUPC card update screen.
 * CVV field intentionally absent (SEC-005 / PCI DSS).
 */
public record CardUpdateForm(
    String cardNumber,   // RULE-025 (read-only in form; validated as non-zero 16-digit)
    String embossedName, // RULE-026
    String activeStatus, // RULE-027 / RULE-060
    int expiryMonth,     // RULE-028
    int expiryYear       // RULE-029 (day carried forward per RULE-030 — not in form)
) {}
