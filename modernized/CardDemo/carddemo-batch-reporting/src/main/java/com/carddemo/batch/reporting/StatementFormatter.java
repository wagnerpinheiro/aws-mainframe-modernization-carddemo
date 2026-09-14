package com.carddemo.batch.reporting;

import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.CustomerEntity;
import com.carddemo.domain.entity.TransactionEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Formats account statements in plain text and HTML.
 *
 * Replicates CBSTM03A paragraphs 5000-CREATE-STATEMENT, 5100-WRITE-HTML-HEADER,
 * 5200-WRITE-HTML-NMADBS, 4000-TRNXFILE-GET, and 6000-WRITE-TRANS.
 *
 * Brief risk (Phase 2): "13 calls to CBSTM03B per account become 13 method calls here;
 * these are 13 I/O operations for a single account's statement, not a batch of 13 items."
 */
public final class StatementFormatter {

    // Replicates ST-LINE0 / ST-LINE15 (CBSTM03A WS lines 86-89, 143-146)
    private static final String TEXT_HEADER =
        "*".repeat(31) + "START OF STATEMENT" + "*".repeat(31);
    private static final String TEXT_FOOTER =
        "*".repeat(32) + "END OF STATEMENT" + "*".repeat(32);
    private static final String DASHES = "-".repeat(80);
    private static final String TEXT_BASIC   = center("Basic Details", 80);
    private static final String TEXT_TXN_HDR = center("TRANSACTION SUMMARY ", 80);
    private static final String COL_HEADERS  =
        "Tran ID          " + "Tran Details                                       " + "   Tran Amount";

    private StatementFormatter() {}

    /**
     * Produces plain-text statement lines (80 chars each).
     * Replicates CBSTM03A 5000-CREATE-STATEMENT + 4000-TRNXFILE-GET + 6000-WRITE-TRANS.
     */
    public static List<String> formatText(CustomerEntity cust, AccountEntity acct,
                                           List<TransactionEntity> transactions) {
        List<String> lines = new ArrayList<>();

        // ST-LINE0
        lines.add(TEXT_HEADER);

        // ST-LINE1: customer name (CUST-FIRST-NAME + CUST-MIDDLE-NAME + CUST-LAST-NAME)
        String name = buildName(cust);
        lines.add(padRight(name, 80));

        // ST-LINE2/3/4: address
        lines.add(padRight(cust.getAddrLine1() != null ? cust.getAddrLine1().strip() : "", 80));
        lines.add(padRight(cust.getAddrLine2() != null ? cust.getAddrLine2().strip() : "", 80));
        String addr3 = buildAddr3(cust);
        lines.add(padRight(addr3, 80));

        // ST-LINE5, 6, 5 (dashes / Basic Details / dashes)
        lines.add(DASHES);
        lines.add(TEXT_BASIC);
        lines.add(DASHES);

        // ST-LINE7/8/9: account details
        lines.add(String.format("%-20s%-60s", "Account ID         :", acct.getId()));
        lines.add(String.format("%-20s%-60s", "Current Balance    :", formatAmount(acct.getCurrentBalance())));
        lines.add(String.format("%-20s%-60s", "FICO Score         :", cust.getFicoCreditScore()));

        // ST-LINE10/11/12/13/12
        lines.add(DASHES);
        lines.add(TEXT_TXN_HDR);
        lines.add(DASHES);
        lines.add(COL_HEADERS);
        lines.add(DASHES);

        // ST-LINE14: one line per transaction (6000-WRITE-TRANS)
        BigDecimal total = BigDecimal.ZERO;
        for (TransactionEntity tran : transactions) {
            String desc = tran.getDescription() != null ? tran.getDescription() : "";
            if (desc.length() > 49) desc = desc.substring(0, 49);
            lines.add(String.format("%-16s %-49s $%-12s",
                tran.getId(),
                desc,
                formatAmount(tran.getAmount())));
            total = total.add(tran.getAmount());
        }

        // ST-LINE12, ST-LINE14A, ST-LINE15
        lines.add(DASHES);
        lines.add(String.format("%-67s $%-12s", "Total EXP:", formatAmount(total)));
        lines.add(TEXT_FOOTER);

        return lines;
    }

    /**
     * Produces HTML statement lines.
     * Replicates CBSTM03A 5100-WRITE-HTML-HEADER, 5200-WRITE-HTML-NMADBS,
     * 4000-TRNXFILE-GET (HTML side), 6000-WRITE-TRANS (HTML side).
     */
    public static List<String> formatHtml(CustomerEntity cust, AccountEntity acct,
                                           List<TransactionEntity> transactions) {
        List<String> lines = new ArrayList<>();

        // 5100-WRITE-HTML-HEADER
        lines.add("<!DOCTYPE html>");
        lines.add("<html lang=\"en\">");
        lines.add("<head>");
        lines.add("<meta charset=\"utf-8\">");
        lines.add("<title>HTML Table Layout</title>");
        lines.add("</head>");
        lines.add("<body style=\"margin:0px;\">");
        lines.add("<table  align=\"center\" frame=\"box\" style=\"width:70%; font:12px Segoe UI,sans-serif;\">");
        lines.add("<tr>");
        lines.add("<td colspan=\"3\" style=\"padding:0px 5px; background-color:#1d1d96b3;\">");
        lines.add(String.format("<h3>Statement for Account Number: %-20s</h3>", acct.getId()));
        lines.add("</td>");
        lines.add("</tr>");
        lines.add("<tr>");
        lines.add("<td colspan=\"3\" style=\"padding:0px 5px; background-color:#FFAF33;\">");
        lines.add("<p style=\"font-size:16px\">Bank of XYZ</p>");
        lines.add("<p>410 Terry Ave N</p>");
        lines.add("<p>Seattle WA 99999</p>");
        lines.add("</td>");
        lines.add("</tr>");
        lines.add("<tr>");
        lines.add("<td colspan=\"3\" style=\"padding:0px 5px; background-color:#f2f2f2;\">");

        // 5200-WRITE-HTML-NMADBS: name + address
        String name = buildName(cust);
        lines.add(String.format("<p style=\"font-size:16px\">%s</p>", escapeHtml(name)));
        lines.add(String.format("<p>%s</p>", escapeHtml(nullSafe(cust.getAddrLine1()))));
        lines.add(String.format("<p>%s</p>", escapeHtml(nullSafe(cust.getAddrLine2()))));
        lines.add(String.format("<p>%s</p>", escapeHtml(buildAddr3(cust))));
        lines.add("</td>");
        lines.add("</tr>");

        // Basic Details section
        lines.add("<tr>");
        lines.add("<td colspan=\"3\" style=\"padding:0px 5px; background-color:#f2f2f2;\">");
        lines.add("<p style=\"font-size:16px\">Basic Details</p>");
        lines.add("</td>");
        lines.add("</tr>");
        lines.add("<tr>");
        lines.add("<td colspan=\"3\" style=\"padding:0px 5px; background-color:#f2f2f2;\">");
        lines.add(String.format("<p>Account ID         : %s</p>", acct.getId()));
        lines.add(String.format("<p>Current Balance    : %s</p>", formatAmount(acct.getCurrentBalance())));
        lines.add(String.format("<p>FICO Score         : %s</p>", cust.getFicoCreditScore()));
        lines.add("</td>");
        lines.add("</tr>");

        // Transaction Summary header
        lines.add("<tr>");
        lines.add("<td colspan=\"3\" style=\"padding:0px 5px; background-color:#33FFD1; text-align:center;\">");
        lines.add("<p style=\"font-size:16px\">Transaction Summary</p>");
        lines.add("</td>");
        lines.add("</tr>");
        lines.add("<tr>");
        lines.add("<td style=\"width:25%; padding:0px 5px; background-color:#33FF5E; text-align:left;\">");
        lines.add("<p style=\"font-size:16px\">Tran ID</p>");
        lines.add("</td>");
        lines.add("<td style=\"width:55%; padding:0px 5px; background-color:#33FF5E; text-align:left;\">");
        lines.add("<p style=\"font-size:16px\">Tran Details</p>");
        lines.add("</td>");
        lines.add("<td style=\"width:20%; padding:0px 5px; background-color:#33FF5E; text-align:right;\">");
        lines.add("<p style=\"font-size:16px\">Amount</p>");
        lines.add("</td>");
        lines.add("</tr>");

        // 6000-WRITE-TRANS (HTML): one row per transaction
        for (TransactionEntity tran : transactions) {
            lines.add("<tr>");
            lines.add("<td style=\"width:25%; padding:0px 5px; background-color:#f2f2f2; text-align:left;\">");
            lines.add(String.format("<p>%s</p>", escapeHtml(tran.getId())));
            lines.add("</td>");
            lines.add("<td style=\"width:55%; padding:0px 5px; background-color:#f2f2f2; text-align:left;\">");
            lines.add(String.format("<p>%s</p>", escapeHtml(nullSafe(tran.getDescription()))));
            lines.add("</td>");
            lines.add("<td style=\"width:20%; padding:0px 5px; background-color:#f2f2f2; text-align:right;\">");
            lines.add(String.format("<p>%s</p>", formatAmount(tran.getAmount())));
            lines.add("</td>");
            lines.add("</tr>");
        }

        // Footer
        lines.add("<tr>");
        lines.add("<td colspan=\"3\" style=\"padding:0px 5px; background-color:#1d1d96b3;\">");
        lines.add("<h3>End of Statement</h3>");
        lines.add("</td>");
        lines.add("</tr>");
        lines.add("</table>");
        lines.add("</body>");
        lines.add("</html>");

        return lines;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String buildName(CustomerEntity cust) {
        StringBuilder sb = new StringBuilder();
        if (cust.getFirstName() != null && !cust.getFirstName().isBlank())
            sb.append(cust.getFirstName().strip()).append(' ');
        if (cust.getMiddleName() != null && !cust.getMiddleName().isBlank())
            sb.append(cust.getMiddleName().strip()).append(' ');
        if (cust.getLastName() != null && !cust.getLastName().isBlank())
            sb.append(cust.getLastName().strip());
        return sb.toString().strip();
    }

    private static String buildAddr3(CustomerEntity cust) {
        StringBuilder sb = new StringBuilder();
        appendToken(sb, cust.getAddrLine3());
        appendToken(sb, cust.getAddrStateCd());
        appendToken(sb, cust.getAddrCountryCd());
        appendToken(sb, cust.getAddrZip());
        return sb.toString().strip();
    }

    private static void appendToken(StringBuilder sb, String v) {
        if (v != null && !v.isBlank()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(v.strip());
        }
    }

    private static String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        // Locale.US: decimal point='.', thousands separator=',' — matches COBOL PICTURE 9.99
        return String.format(Locale.US, "%,.2f", amount);
    }

    private static String padRight(String s, int width) {
        if (s == null) s = "";
        if (s.length() >= width) return s.substring(0, width);
        return String.format("%-" + width + "s", s);
    }

    private static String center(String s, int width) {
        int pad = (width - s.length()) / 2;
        return " ".repeat(Math.max(0, pad)) + s;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String nullSafe(String s) {
        return s != null ? s.strip() : "";
    }
}
