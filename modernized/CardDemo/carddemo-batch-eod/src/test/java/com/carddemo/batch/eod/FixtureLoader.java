package com.carddemo.batch.eod;

import com.carddemo.common.CobolDisplayParser;
import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.CardXRefEntity;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXRefRepository;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Test-only fixture loader for CBTRN01C characterization tests.
 *
 * Parses the fixed-width ASCII fixture files (same encoding that the legacy COBOL
 * program consumed from VSAM) and seeds the H2 repositories via JPA.
 *
 * File layouts (all 1-indexed, matching COBOL COPY books):
 *
 *   cardxref.txt  — CVACT03Y, 36 bytes/record (no FILLER in ASCII file):
 *     XREF-CARD-NUM  : 1–16  X(16)
 *     XREF-CUST-ID   : 17–25 9(9)
 *     XREF-ACCT-ID   : 26–36 9(11)
 *
 *   acctdata.txt  — CVACT01Y, 300 bytes/record:
 *     ACCT-ID                 :   1–11  9(11)
 *     ACCT-ACTIVE-STATUS      :  12     X(1)
 *     ACCT-CURR-BAL           :  13–24  S9(10)V99 sign-overpunch
 *     ACCT-CREDIT-LIMIT       :  25–36  S9(10)V99 sign-overpunch
 *     ACCT-CASH-CREDIT-LIMIT  :  37–48  S9(10)V99 sign-overpunch
 *     ACCT-OPEN-DATE          :  49–58  X(10)
 *     ACCT-EXPIRATION-DATE    :  59–68  X(10)
 *     ACCT-REISSUE-DATE       :  69–78  X(10)
 *     ACCT-CURR-CYC-CREDIT    :  79–90  S9(10)V99 sign-overpunch
 *     ACCT-CURR-CYC-DEBIT     :  91–102 S9(10)V99 sign-overpunch
 *     ACCT-ADDR-ZIP           : 103–112 X(10)
 *     ACCT-GROUP-ID           : 113–122 X(10)
 *     FILLER                  : 123–300 (ignored)
 */
public final class FixtureLoader {

    private FixtureLoader() {}

    /**
     * Clears and reloads both repositories from the standard fixture classpath paths.
     * Equivalent to the COBOL program opening XREF-FILE and ACCOUNT-FILE.
     */
    public static void loadFullFixtures(
            CardXRefRepository cardXRefRepository,
            AccountRepository accountRepository) throws IOException {

        cardXRefRepository.deleteAll();
        accountRepository.deleteAll();
        loadCardXRef(cardXRefRepository, "fixtures/cardxref.txt");
        loadAccounts(accountRepository, "fixtures/acctdata.txt");
    }

    /**
     * Parses {@code fixtures/cardxref.txt} (36 bytes per line, no FILLER) and saves
     * all entries to the given repository.
     *
     * @param repo             the CardXRef JPA repository backed by H2
     * @param classpathPath    classpath-relative path to the file
     */
    static void loadCardXRef(CardXRefRepository repo, String classpathPath) throws IOException {
        List<CardXRefEntity> entities = new ArrayList<>();
        for (String line : readLines(classpathPath)) {
            if (line.isBlank()) continue;
            // 0-indexed byte ranges (matching 1-indexed COBOL layout minus 1):
            //   cardNumber : 0–15  (16 chars)
            //   customerId : 16–24 ( 9 chars)
            //   accountId  : 25–35 (11 chars)
            String cardNumber = line.substring(0, 16);
            long customerId   = Long.parseLong(line.substring(16, 25).strip());
            long accountId    = Long.parseLong(line.substring(25, 36).strip());
            entities.add(new CardXRefEntity(cardNumber, customerId, accountId));
        }
        repo.saveAll(entities);
    }

    /**
     * Parses {@code fixtures/acctdata.txt} (300 bytes per line per CVACT01Y) and saves
     * all entries to the given repository.  COBOL sign-overpunch amounts are decoded via
     * {@link CobolDisplayParser#parseSignedAmount(String, int)} with 2 implied decimals.
     *
     * @param repo          the Account JPA repository backed by H2
     * @param classpathPath classpath-relative path to the file
     */
    static void loadAccounts(AccountRepository repo, String classpathPath) throws IOException {
        List<AccountEntity> entities = new ArrayList<>();
        for (String line : readLines(classpathPath)) {
            if (line.isBlank()) continue;

            // 0-indexed byte ranges:
            long       acctId            = Long.parseLong(line.substring(0, 11).strip());
            String     activeStatus      = line.substring(11, 12);
            BigDecimal currentBalance    = CobolDisplayParser.parseSignedAmount(line.substring(12, 24), 2);
            BigDecimal creditLimit       = CobolDisplayParser.parseSignedAmount(line.substring(24, 36), 2);
            BigDecimal cashCreditLimit   = CobolDisplayParser.parseSignedAmount(line.substring(36, 48), 2);
            String     openDate          = line.substring(48, 58).strip();
            String     expirationDate    = line.substring(58, 68).strip();
            String     reissueDate       = line.substring(68, 78).strip();
            BigDecimal currCycleCredit   = CobolDisplayParser.parseSignedAmount(line.substring(78, 90), 2);
            BigDecimal currCycleDebit    = CobolDisplayParser.parseSignedAmount(line.substring(90, 102), 2);
            String     addrZip           = line.substring(102, 112).strip();
            String     groupId           = line.length() > 122
                                           ? line.substring(112, 122).strip()
                                           : "";

            entities.add(new AccountEntity(
                acctId, activeStatus, currentBalance, creditLimit, cashCreditLimit,
                openDate, expirationDate, reissueDate,
                currCycleCredit, currCycleDebit, addrZip, groupId
            ));
        }
        repo.saveAll(entities);
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    private static List<String> readLines(String classpathPath) throws IOException {
        var resource = new ClassPathResource(classpathPath);
        try (var is = resource.getInputStream();
             var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.US_ASCII))) {
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return lines;
        }
    }
}
