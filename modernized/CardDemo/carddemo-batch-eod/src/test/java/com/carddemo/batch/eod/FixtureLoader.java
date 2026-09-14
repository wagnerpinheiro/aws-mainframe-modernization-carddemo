package com.carddemo.batch.eod;

import com.carddemo.common.CobolDisplayParser;
import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.CardXRefEntity;
import com.carddemo.domain.entity.DiscountGroupEntity;
import com.carddemo.domain.entity.DiscountGroupId;
import com.carddemo.domain.entity.TranCatBalanceEntity;
import com.carddemo.domain.entity.TranCatBalanceId;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXRefRepository;
import com.carddemo.domain.repository.DiscountGroupRepository;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Test-only fixture loader for CBTRN01C and CBTRN02C characterization tests.
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
 *
 *   tcatbal.txt   — CVTRA01Y, 50 bytes/record:
 *     TRANCAT-ACCT-ID  :  1–11  9(11)
 *     TRANCAT-TYPE-CD  : 12–13  X(2)
 *     TRANCAT-CD       : 14–17  9(4)
 *     TRAN-CAT-BAL     : 18–28  S9(9)V99 sign-overpunch (11 chars)
 *     FILLER           : 29–50  (ignored)
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

    /**
     * Parses {@code fixtures/tcatbal.txt} (50 bytes per line per CVTRA01Y) and saves
     * all entries to the given repository.  Used by {@link TransactionPostingJobTest}
     * to pre-populate TCATBAL so that paragraph 2700-UPDATE-TCATBAL exercises the
     * "found → balance += amount" branch as well as the "not found → create" branch.
     *
     * <p>Layout (0-indexed byte ranges, Java):
     * <ul>
     *   <li>[0:11]  TRANCAT-ACCT-ID — parsed as {@code Long}</li>
     *   <li>[11:13] TRANCAT-TYPE-CD — stored as {@code String(2)}</li>
     *   <li>[13:17] TRANCAT-CD — parsed as {@code Integer}</li>
     *   <li>[17:28] TRAN-CAT-BAL — S9(9)V99 sign-overpunch, 11 chars, 2 implied decimals</li>
     *   <li>[28:50] FILLER — skipped</li>
     * </ul>
     *
     * @param repo          the TranCatBalance JPA repository backed by H2
     * @param classpathPath classpath-relative path (e.g., {@code "fixtures/tcatbal.txt"})
     */
    public static void loadTcatbal(TranCatBalanceRepository repo,
                                   String classpathPath) throws IOException {
        List<TranCatBalanceEntity> entities = new ArrayList<>();
        for (String line : readLines(classpathPath)) {
            if (line.isBlank()) continue;
            // 0-indexed byte ranges (matching 1-indexed COBOL layout minus 1):
            //   accountId  :  0–10 (11 chars)
            //   typeCode   : 11–12 ( 2 chars)
            //   categoryCode: 13–16 ( 4 chars)
            //   balance    : 17–27 (11 chars) — S9(9)V99 sign-overpunch, 2 implied decimals
            //   filler     : 28–49 (ignored)
            long       accountId    = Long.parseLong(line.substring(0, 11).strip());
            String     typeCode     = line.substring(11, 13);
            int        categoryCode = Integer.parseInt(line.substring(13, 17).strip());
            BigDecimal balance      = CobolDisplayParser.parseSignedAmount(
                                          line.substring(17, 28), 2);

            TranCatBalanceId id = new TranCatBalanceId(accountId, typeCode, categoryCode);
            entities.add(new TranCatBalanceEntity(id, balance));
        }
        repo.saveAll(entities);
    }

    /**
     * Parses {@code fixtures/discgrp.txt} (50 bytes per line per CVTRA02Y) and saves
     * all entries to the given repository.  Used by {@link InterestCalculationJobTest}.
     *
     * <p>Layout (0-indexed byte ranges):
     * <ul>
     *   <li>[0:10]  DIS-ACCT-GROUP-ID — stored stripped (trailing spaces removed)</li>
     *   <li>[10:12] DIS-TRAN-TYPE-CD</li>
     *   <li>[12:16] DIS-TRAN-CAT-CD — parsed as {@code Integer}</li>
     *   <li>[16:22] DIS-INT-RATE — S9(4)V99 sign-overpunch, 2 implied decimals</li>
     *   <li>[22:50] FILLER — skipped</li>
     * </ul>
     */
    public static void loadDiscountGroups(DiscountGroupRepository repo,
                                          String classpathPath) throws IOException {
        List<DiscountGroupEntity> entities = new ArrayList<>();
        for (String line : readLines(classpathPath)) {
            if (line.isBlank()) continue;
            String groupId      = line.substring(0, 10).stripTrailing();
            String typeCode     = line.substring(10, 12);
            int    categoryCode = Integer.parseInt(line.substring(12, 16).strip());
            BigDecimal rate     = CobolDisplayParser.parseSignedAmount(line.substring(16, 22), 2);

            entities.add(new DiscountGroupEntity(
                new DiscountGroupId(groupId, typeCode, categoryCode), rate));
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
