package com.carddemo.batch.eod;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Test-only builder that constructs synthetic 350-byte DALYTRAN fixed-width records
 * (CVTRA06Y layout) and writes them to a temp file for use as job input.
 *
 * All field positions are 1-indexed here, matching the FixedLengthTokenizer ranges
 * configured in {@link TransactionValidationJobConfig}:
 *
 *   1–16    transactionId   X(16)
 *  17–18    typeCode        X(2)
 *  19–22    categoryCode    9(4)
 *  23–32    source          X(10)
 *  33–132   description     X(100)
 * 133–143   amount          S9(9)V99 sign-overpunch (11 chars)
 * 144–152   merchantId      9(9)
 * 153–202   merchantName    X(50)
 * 203–252   merchantCity    X(50)
 * 253–262   merchantZip     X(10)
 * 263–278   cardNumber      X(16)   ← key field tested by CBTRN01C/XREF lookup
 * 279–304   originTs        X(26)
 * 305–330   processTs       X(26)
 * 331–350   filler          X(20)
 *
 * The oracle for the sign-overpunch encoding is
 * {@link com.carddemo.common.CobolDisplayParser#parseSignedAmount}:
 * positive last digit 0→{ 1→A … 9→I; negative 0→} 1→J … 9→R.
 */
public final class SyntheticDalytranBuilder {

    /** Placeholder amount used when no specific amount is needed: $100.00 → "0000001000{" */
    private static final BigDecimal DEFAULT_AMOUNT = new BigDecimal("100.00");

    private SyntheticDalytranBuilder() {}

    // -------------------------------------------------------------------------
    // Public factory methods
    // -------------------------------------------------------------------------

    /**
     * Builds one 350-character DALYTRAN record with {@code DEFAULT_AMOUNT} ($100.00).
     *
     * @param transactionId 1–16-character transaction identifier (left-zero-padded)
     * @param cardNumber    exactly 16-character card number (the field CBTRN01C looks up)
     * @return 350-character string; no trailing newline
     */
    public static String buildRecord(String transactionId, String cardNumber) {
        return buildRecord(transactionId, cardNumber, DEFAULT_AMOUNT);
    }

    /**
     * Builds one 350-character DALYTRAN record with a specific amount.
     *
     * @param transactionId 1–16-character transaction identifier (left-zero-padded)
     * @param cardNumber    exactly 16-character card number
     * @param amount        transaction amount (encoded as S9(9)V99 sign-overpunch)
     * @return 350-character string; no trailing newline
     */
    public static String buildRecord(String transactionId, String cardNumber, BigDecimal amount) {
        var sb = new StringBuilder(350);

        // 1–16   transactionId  (left-zero-padded, truncated to 16)
        sb.append(leftPad(transactionId, 16, '0'));
        // 17–18  typeCode
        sb.append("01");
        // 19–22  categoryCode
        sb.append("0001");
        // 23–32  source (right-space-padded)
        sb.append(rightPad("POS TERM", 10));
        // 33–132 description (right-space-padded to 100 chars)
        sb.append(rightPad("Synthetic characterization test record", 100));
        // 133–143 amount  S9(9)V99 — 9 integer digits + 2 decimal digits = 11 chars
        sb.append(formatSignedAmount(amount, 9, 2));
        // 144–152 merchantId  9(9)
        sb.append("000000001");
        // 153–202 merchantName  X(50)
        sb.append(rightPad("Test Merchant", 50));
        // 203–252 merchantCity  X(50)
        sb.append(rightPad("Test City", 50));
        // 253–262 merchantZip  X(10)
        sb.append(rightPad("00000", 10));
        // 263–278 cardNumber  X(16)  — the XREF lookup key
        sb.append(rightPad(cardNumber, 16));
        // 279–304 originTs  X(26)
        sb.append(rightPad("2024-01-01 00:00:00.000000", 26));
        // 305–330 processTs  X(26)
        sb.append(rightPad("2024-01-01 00:00:00.000000", 26));
        // 331–350 filler  X(20)
        sb.append(rightPad("", 20));

        if (sb.length() != 350) {
            throw new IllegalStateException(
                "BUG in SyntheticDalytranBuilder: expected 350 chars, got " + sb.length());
        }
        return sb.toString();
    }

    /**
     * Writes the given records to a new temp file (one record per line).
     * The file is registered for deletion on JVM exit.
     *
     * @param records list of 350-character record strings (no trailing newlines)
     * @return path to the written temp file
     */
    public static Path writeToTempFile(List<String> records) throws IOException {
        Path tempFile = Files.createTempFile("cbtrn01c-test-", ".txt");
        Files.write(tempFile, records, StandardCharsets.US_ASCII);
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }

    /**
     * Creates a 0-byte temp file (simulates empty DALYTRAN — no records to process).
     *
     * @return path to the empty temp file
     */
    public static Path emptyTempFile() throws IOException {
        Path tempFile = Files.createTempFile("cbtrn01c-empty-", ".txt");
        tempFile.toFile().deleteOnExit();
        return tempFile;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Right-pads {@code s} with spaces to {@code width}, truncating if longer.
     */
    private static String rightPad(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        return String.format("%-" + width + "s", s);
    }

    /**
     * Left-pads {@code s} with {@code padChar} to {@code width}, truncating if longer.
     */
    private static String leftPad(String s, int width, char padChar) {
        if (s.length() >= width) return s.substring(0, width);
        return String.valueOf(padChar).repeat(width - s.length()) + s;
    }

    /**
     * Encodes {@code amount} as a COBOL S9({@code intDigits})V{@code fracDigits}
     * sign-overpunch string ({@code intDigits + fracDigits} characters total).
     *
     * Overpunch table (rightmost character of the field):
     *   Positive: 0→{  1→A  2→B  3→C  4→D  5→E  6→F  7→G  8→H  9→I
     *   Negative: 0→}  1→J  2→K  3→L  4→M  5→N  6→O  7→P  8→Q  9→R
     *
     * This matches the decoding logic in CobolDisplayParser.parseSignedAmount,
     * which is the Oracle for this encoding.
     */
    static String formatSignedAmount(BigDecimal amount, int intDigits, int fracDigits) {
        boolean negative = amount.signum() < 0;
        long rawLong = amount.abs()
                             .multiply(BigDecimal.TEN.pow(fracDigits))
                             .setScale(0, RoundingMode.HALF_UP)
                             .longValueExact();

        int totalWidth = intDigits + fracDigits;
        String digits = String.format("%0" + totalWidth + "d", rawLong);
        if (digits.length() > totalWidth) {
            throw new IllegalArgumentException(
                "Amount " + amount + " overflows S9(" + intDigits + ")V" + fracDigits);
        }

        char lastDigit = digits.charAt(totalWidth - 1);
        char overpunched;
        if (!negative) {
            overpunched = (lastDigit == '0') ? '{' : (char) ('A' + (lastDigit - '1'));
        } else {
            overpunched = (lastDigit == '0') ? '}' : (char) ('J' + (lastDigit - '1'));
        }
        return digits.substring(0, totalWidth - 1) + overpunched;
    }
}
