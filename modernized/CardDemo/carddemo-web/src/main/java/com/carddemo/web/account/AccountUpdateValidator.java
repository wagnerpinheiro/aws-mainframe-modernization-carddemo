package com.carddemo.web.account;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates account + customer update fields per COACTUPC validation paragraphs.
 *
 * Implements RULE-024 through RULE-045 (field-type and cross-field validators).
 * Extracted as decomposition step 2 (before AccountUpdateController is written).
 *
 * NOTE: CODING-TO-BE-DONE sentinel present in legacy; no activated functionality gap identified.
 */
public final class AccountUpdateValidator {

    // RULE-037: valid US state codes (simplified subset; full list from CSLKPCDY copybook)
    private static final Set<String> VALID_STATES = Set.of(
        "AL","AK","AZ","AR","CA","CO","CT","DE","FL","GA","HI","ID","IL","IN","IA",
        "KS","KY","LA","ME","MD","MA","MI","MN","MS","MO","MT","NE","NV","NH","NJ",
        "NM","NY","NC","ND","OH","OK","OR","PA","RI","SC","SD","TN","TX","UT","VT",
        "VA","WA","WV","WI","WY","DC","PR","VI","GU","AS","MP"
    );

    private AccountUpdateValidator() {}

    /** Returns list of error messages; empty = valid. */
    public static List<String> validate(AccountUpdateForm form) {
        List<String> errors = new ArrayList<>();

        // RULE-032: account active status Y or N
        if (!isYorN(form.activeStatus())) {
            errors.add("RULE-032: Active status must be Y or N");
        }

        // RULE-045: credit limit numeric
        if (form.creditLimit() == null) {
            errors.add("RULE-045: Credit limit is required");
        } else if (form.creditLimit().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("SEC-021: Credit limit must be zero or greater");
        } else if (form.creditLimit().compareTo(new BigDecimal("9999999999.99")) > 0) {
            errors.add("RULE-045: Credit limit exceeds maximum (PIC S9(10)V99)");
        }
        if (form.cashCreditLimit() == null) {
            errors.add("RULE-045: Cash credit limit is required");
        } else if (form.cashCreditLimit().compareTo(BigDecimal.ZERO) < 0) {
            errors.add("SEC-021: Cash credit limit must be zero or greater");
        } else if (form.cashCreditLimit().compareTo(new BigDecimal("9999999999.99")) > 0) {
            errors.add("RULE-045: Cash credit limit exceeds maximum (PIC S9(10)V99)");
        }

        // RULE-034: FICO 300–850
        if (form.ficoCreditScore() != null) {
            int fico = form.ficoCreditScore();
            if (fico < 300 || fico > 850) {
                errors.add("RULE-034: FICO score must be 300–850");
            }
        }

        // RULE-035: first name alpha-only, mandatory
        if (!isAlpha(form.firstName(), false)) {
            errors.add("RULE-035: First name must be non-blank and contain only letters");
        }
        // middle name optional but alpha-only if present
        if (form.middleName() != null && !form.middleName().isBlank() && !isAlpha(form.middleName(), true)) {
            errors.add("RULE-035: Middle name must contain only letters");
        }
        // last name mandatory alpha
        if (!isAlpha(form.lastName(), false)) {
            errors.add("RULE-035: Last name must be non-blank and contain only letters");
        }

        // RULE-036: address line 1 mandatory
        if (form.addrLine1() == null || form.addrLine1().isBlank()) {
            errors.add("RULE-036: Address line 1 is required");
        }

        // RULE-037: state code 2-alpha valid
        if (form.addrStateCd() == null || form.addrStateCd().isBlank()) {
            errors.add("RULE-037: State code is required");
        } else if (!VALID_STATES.contains(form.addrStateCd().toUpperCase())) {
            errors.add("RULE-037: Invalid US state code: " + form.addrStateCd());
        }

        // RULE-038: zip 5-digit numeric non-zero
        if (!isZip(form.addrZip())) {
            errors.add("RULE-038: Zip code must be 5 numeric digits, non-zero");
        }

        // RULE-039: city alpha-only
        if (!isAlpha(form.addrCity(), false)) {
            errors.add("RULE-039: City must be non-blank and contain only letters");
        }

        // RULE-040: country code 3-alpha
        if (form.addrCountryCd() == null || form.addrCountryCd().isBlank()
                || form.addrCountryCd().length() != 3
                || !form.addrCountryCd().chars().allMatch(Character::isLetter)) {
            errors.add("RULE-040: Country code must be exactly 3 alphabetic characters");
        }

        // RULE-041: phone optional, NANP when present
        String phoneErr1 = PhoneValidator.validate(form.phoneNum1());
        if (phoneErr1 != null) errors.add("RULE-041 phone1: " + phoneErr1);
        String phoneErr2 = PhoneValidator.validate(form.phoneNum2());
        if (phoneErr2 != null) errors.add("RULE-041 phone2: " + phoneErr2);

        // RULE-043: EFT account ID 10-digit numeric non-zero
        if (!isNumericNonZero(form.eftAccountId(), 10)) {
            errors.add("RULE-043: EFT account ID must be 10 numeric digits, non-zero");
        }

        // RULE-044: primary cardholder indicator Y or N
        if (!isYorN(form.priCardHolderInd())) {
            errors.add("RULE-044: Primary cardholder indicator must be Y or N");
        }

        return errors;
    }

    private static boolean isYorN(String v) {
        return "Y".equals(v) || "N".equals(v);
    }

    private static boolean isAlpha(String v, boolean allowBlank) {
        if (v == null || v.isBlank()) return allowBlank;
        return v.strip().chars().allMatch(Character::isLetter);
    }

    private static boolean isZip(String v) {
        if (v == null || v.isBlank() || v.length() != 5) return false;
        if (!v.chars().allMatch(Character::isDigit)) return false;
        return !v.equals("00000");
    }

    private static boolean isNumericNonZero(String v, int len) {
        if (v == null || v.isBlank()) return false;
        String s = v.strip();
        if (s.length() != len) return false;
        if (!s.chars().allMatch(Character::isDigit)) return false;
        return !s.chars().allMatch(c -> c == '0');
    }
}
