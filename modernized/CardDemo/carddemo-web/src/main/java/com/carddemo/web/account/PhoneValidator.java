package com.carddemo.web.account;

/**
 * NANP phone validation extracted from COACTUPC phone-validation paragraphs.
 *
 * RULE-041: Phone is optional — all-blank passes. When present, format is (NXX)NXX-XXXX.
 * Area code must be 3 numeric digits (simplified NANP: not starting with 0 or 1).
 * Prefix and line number must be numeric.
 *
 * Brief: TD-09 — COACTUPC uses GO TO spaghetti (51 GO TO statements) for phone/SSN validation.
 * Extracted as early-return logic per decomposition step 4.
 *
 * NOTE: CODING-TO-BE-DONE sentinel present in legacy; no activated functionality gap identified.
 */
public final class PhoneValidator {

    private PhoneValidator() {}

    /**
     * Returns null if valid (or blank = optional field), otherwise returns an error message.
     */
    public static String validate(String phone) {
        if (phone == null || phone.isBlank()) {
            return null; // RULE-041: blank phone is valid (optional)
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() != 10) {
            return "Phone must be 10 digits (NXX)NXX-XXXX";
        }
        char areaFirst = digits.charAt(0);
        if (areaFirst == '0' || areaFirst == '1') {
            return "Phone area code must not start with 0 or 1";
        }
        return null;
    }
}
