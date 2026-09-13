package com.carddemo.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Parses COBOL DISPLAY-format signed numeric fields (zone/overpunch encoding).
 * In COBOL, PIC S9(n)V99 stored in DISPLAY format uses sign overpunch on the
 * rightmost character. The implied decimal (V) is not stored — it is positional.
 */
public final class CobolDisplayParser {

    // Zone overpunch map: overpunched char → (digit, isNegative)
    private static final Map<Character, int[]> ZONE_MAP = Map.ofEntries(
        Map.entry('{', new int[]{0, 0}),  // positive 0
        Map.entry('A', new int[]{1, 0}),  // positive 1
        Map.entry('B', new int[]{2, 0}),
        Map.entry('C', new int[]{3, 0}),
        Map.entry('D', new int[]{4, 0}),
        Map.entry('E', new int[]{5, 0}),
        Map.entry('F', new int[]{6, 0}),
        Map.entry('G', new int[]{7, 0}),
        Map.entry('H', new int[]{8, 0}),
        Map.entry('I', new int[]{9, 0}),  // positive 9
        Map.entry('}', new int[]{0, 1}),  // negative 0
        Map.entry('J', new int[]{1, 1}),  // negative 1
        Map.entry('K', new int[]{2, 1}),
        Map.entry('L', new int[]{3, 1}),
        Map.entry('M', new int[]{4, 1}),
        Map.entry('N', new int[]{5, 1}),
        Map.entry('O', new int[]{6, 1}),
        Map.entry('P', new int[]{7, 1}),
        Map.entry('Q', new int[]{8, 1}),
        Map.entry('R', new int[]{9, 1})   // negative 9
    );

    private CobolDisplayParser() {}

    /**
     * Parses a COBOL signed display field with implied decimal places.
     *
     * @param raw           raw string from the fixed-width record (may have trailing spaces)
     * @param impliedDecimals number of implied decimal places (the "99" in V99)
     * @return parsed BigDecimal
     * @throws InvalidDataException if the raw value is blank or not a valid COBOL display field
     */
    public static BigDecimal parseSignedAmount(String raw, int impliedDecimals) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidDataException("Amount field is blank");
        }
        String trimmed = raw.stripTrailing();
        if (trimmed.isEmpty()) {
            throw new InvalidDataException("Amount field contains only spaces: [" + raw + "]");
        }

        char lastChar = trimmed.charAt(trimmed.length() - 1);
        String digits;
        boolean negative;

        int[] zoneEntry = ZONE_MAP.get(lastChar);
        if (zoneEntry != null) {
            // Overpunched last digit
            digits = trimmed.substring(0, trimmed.length() - 1) + zoneEntry[0];
            negative = zoneEntry[1] == 1;
        } else if (Character.isDigit(lastChar)) {
            // No sign overpunch — treat as positive
            digits = trimmed;
            negative = false;
        } else {
            throw new InvalidDataException("Unrecognized COBOL sign overpunch character '" + lastChar
                + "' in field: [" + raw + "]");
        }

        BigDecimal value = new BigDecimal(digits)
            .setScale(impliedDecimals, RoundingMode.UNNECESSARY)
            .movePointLeft(impliedDecimals);

        return negative ? value.negate() : value;
    }

    /**
     * Parses a COBOL unsigned display numeric field (PIC 9(n)).
     */
    public static long parseUnsignedLong(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidDataException("Numeric field is blank");
        }
        return Long.parseLong(raw.strip());
    }
}
