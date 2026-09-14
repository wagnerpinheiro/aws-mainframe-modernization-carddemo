package com.carddemo.batch.eod;

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
import com.carddemo.domain.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterization tests for CBACT04C → InterestCalculationJob.
 *
 * <h2>Oracle</h2>
 * The legacy COBOL program CBACT04C.cbl (652 LOC) defines the ground truth.
 *
 * <h2>Algorithm replicated</h2>
 * <ol>
 *   <li>Read TCATBAL sequentially ordered by (accountId, typeCode, categoryCode).</li>
 *   <li>Group by accountId.</li>
 *   <li>For each account: load account + card (via XREF alternate key);
 *       for each category balance: look up DISCGRP rate; if rate != 0, compute
 *       monthly interest = (balance × rate / 1200) with RoundingMode.DOWN (RULE-007);
 *       write interest TransactionEntity.</li>
 *   <li>Update account: balance += totalInterest (RULE-008);
 *       reset currCycleCredit = 0 and currCycleDebit = 0 (RULE-009).</li>
 * </ol>
 *
 * <h2>DISCGRP fallback</h2>
 * If no DISCGRP record for the account's groupId, retry with groupId="DEFAULT"
 * (CBACT04C 1200-A-GET-DEFAULT-INT-RATE).
 *
 * <h2>1400-COMPUTE-FEES</h2>
 * A no-op stub in the COBOL ("To be implemented"). Not tested; no Java equivalent.
 */
@SpringBatchTest
@SpringBootTest(properties = {
    "spring.main.allow-bean-definition-overriding=true",
    "spring.main.banner-mode=off"
})
@ActiveProfiles("test")
class InterestCalculationJobTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    @Qualifier("interestCalculationJob")
    private Job interestCalculationJob;

    @Autowired private AccountRepository        accountRepository;
    @Autowired private CardXRefRepository       cardXRefRepository;
    @Autowired private TranCatBalanceRepository tranCatBalanceRepository;
    @Autowired private DiscountGroupRepository  discountGroupRepository;
    @Autowired private TransactionRepository    transactionRepository;

    private JobExecution lastExecution;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(interestCalculationJob);
        jobRepositoryTestUtils.removeJobExecutions();

        transactionRepository.deleteAll();
        tranCatBalanceRepository.deleteAll();
        discountGroupRepository.deleteAll();
        cardXRefRepository.deleteAll();
        accountRepository.deleteAll();

        lastExecution = null;
    }

    // =========================================================================
    // Case 1 — Single account, single category, positive interest
    // =========================================================================

    /**
     * One account with one TCATBAL entry and a non-zero DISCGRP rate.
     *
     * <p>COBOL oracle (1300-COMPUTE-INTEREST):
     * {@code COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200}
     *
     * <p>Concrete values: balance=$1200.00, rate=15.00 → interest = 1200×15/1200 = $15.00.
     *
     * <p>Asserts: TransactionEntity saved with correct amount; account balance updated.
     */
    @Test
    void singleAccount_interestComputedAndTransactionSaved() throws Exception {
        long acctId = 10001L;
        seedAccount(acctId, "500.00", "500.00");
        seedXRef("1001000100010001", 9001L, acctId);
        seedBalance(acctId, "01", 1, "1200.00");
        seedRate("TESTGRP", "01", 1, "15.00");
        // account groupId = "TESTGRP" → direct lookup succeeds

        run();

        assertEquals(BatchStatus.COMPLETED, lastExecution.getStatus());
        assertEquals(1L, transactionRepository.count(),
            "One interest transaction must be written");

        var tran = transactionRepository.findAll().get(0);
        assertThat(tran.getAmount()).isEqualByComparingTo("15.00");
        assertEquals("01", tran.getTypeCode());
        assertEquals(5, tran.getCategoryCode());
        assertEquals("System", tran.getSource());
        assertThat(tran.getDescription()).contains("Int. for a/c");

        var acct = accountRepository.findById(acctId).orElseThrow();
        assertThat(acct.getCurrentBalance()).isEqualByComparingTo("515.00"); // 500 + 15
    }

    // =========================================================================
    // Case 2 — RULE-007: RoundingMode.DOWN (truncate toward zero)
    // =========================================================================

    /**
     * Verifies that the interest formula uses {@link java.math.RoundingMode#DOWN},
     * matching COBOL COMPUTE without ROUNDED (truncates toward zero).
     *
     * <p>COBOL oracle: COMPUTE WS-MONTHLY-INT = (100.00 × 10.00) / 1200
     *   = 1000.00 / 1200 = 0.8333... → truncated to 0.83 (not rounded to 0.84).
     *
     * <p>This test pins the rounding decision documented in {@link InterestCalculationTasklet}:
     * RoundingMode.DOWN replicates COBOL; TODO(prod) marks the gap for production review.
     */
    @Test
    void rule007_roundingDown_notHalfUp() throws Exception {
        long acctId = 20001L;
        seedAccount(acctId, "100.00", "100.00");
        seedXRef("2001000200020002", 9002L, acctId);
        seedBalance(acctId, "01", 1, "100.00");
        seedRate("TESTGRP", "01", 1, "10.00"); // 100 × 10 / 1200 = 0.8333... → DOWN = 0.83

        run();

        assertEquals(BatchStatus.COMPLETED, lastExecution.getStatus());
        var tran = transactionRepository.findAll().get(0);
        assertThat(tran.getAmount())
            .as("RULE-007: 100×10/1200 must truncate to 0.83, not round to 0.84")
            .isEqualByComparingTo("0.83");
    }

    // =========================================================================
    // Case 3 — RULE-008: account balance updated after interest run
    // =========================================================================

    /**
     * Confirms RULE-008: ACCT-CURR-BAL += WS-TOTAL-INT after the interest run.
     *
     * <p>COBOL oracle (1050-UPDATE-ACCOUNT): {@code ADD WS-TOTAL-INT TO ACCT-CURR-BAL}.
     */
    @Test
    void rule008_accountBalanceUpdated() throws Exception {
        long acctId = 30001L;
        seedAccount(acctId, "1000.00", "500.00");
        seedXRef("3001000300030003", 9003L, acctId);
        seedBalance(acctId, "01", 1, "600.00");
        seedRate("TESTGRP", "01", 1, "12.00"); // 600 × 12 / 1200 = 6.00

        run();

        assertEquals(BatchStatus.COMPLETED, lastExecution.getStatus());
        var acct = accountRepository.findById(acctId).orElseThrow();
        assertThat(acct.getCurrentBalance())
            .as("RULE-008: balance must be original ($1000) + interest ($6)")
            .isEqualByComparingTo("1006.00");
    }

    // =========================================================================
    // Case 4 — RULE-009: cycle accumulators reset to zero
    // =========================================================================

    /**
     * Confirms RULE-009: ACCT-CURR-CYC-CREDIT and ACCT-CURR-CYC-DEBIT both set to 0
     * after the interest run, regardless of their pre-run values.
     *
     * <p>COBOL oracle (1050-UPDATE-ACCOUNT):
     * {@code MOVE 0 TO ACCT-CURR-CYC-CREDIT}
     * {@code MOVE 0 TO ACCT-CURR-CYC-DEBIT}
     */
    @Test
    void rule009_cycleAccumulatorsReset() throws Exception {
        long acctId = 40001L;
        // Seed account with non-zero cycle accumulators
        accountRepository.save(new AccountEntity(
            acctId, "Y",
            new BigDecimal("800.00"), new BigDecimal("5000.00"), new BigDecimal("2500.00"),
            "2020-01-01", "2030-01-01", "2025-01-01",
            new BigDecimal("300.00"),  // currCycleCredit — must be reset to 0
            new BigDecimal("-150.00"), // currCycleDebit — must be reset to 0
            "12345", "CYCLEGRP"
        ));
        seedXRef("4001000400040004", 9004L, acctId);
        seedBalance(acctId, "01", 1, "100.00");
        seedRate("CYCLEGRP", "01", 1, "15.00");

        run();

        assertEquals(BatchStatus.COMPLETED, lastExecution.getStatus());
        var acct = accountRepository.findById(acctId).orElseThrow();
        assertThat(acct.getCurrCycleCredit())
            .as("RULE-009: currCycleCredit must be reset to 0 after interest run")
            .isEqualByComparingTo("0.00");
        assertThat(acct.getCurrCycleDebit())
            .as("RULE-009: currCycleDebit must be reset to 0 after interest run")
            .isEqualByComparingTo("0.00");
    }

    // =========================================================================
    // Case 5 — Zero rate: no interest transaction written
    // =========================================================================

    /**
     * If DIS-INT-RATE = 0, CBACT04C skips interest computation (1300/1300-B).
     *
     * <p>COBOL oracle: {@code IF DIS-INT-RATE NOT = 0 PERFORM 1300-COMPUTE-INTEREST}.
     * Rate = 0 → condition is false → no interest transaction and no write to TRANSACT-FILE.
     * Account cycle accumulators are still reset (1050-UPDATE-ACCOUNT always runs).
     */
    @Test
    void zeroRate_noInterestTransaction_butCycleStillReset() throws Exception {
        long acctId = 50001L;
        accountRepository.save(new AccountEntity(
            acctId, "Y",
            new BigDecimal("200.00"), new BigDecimal("5000.00"), new BigDecimal("2500.00"),
            "2020-01-01", "2030-01-01", "2025-01-01",
            new BigDecimal("100.00"), new BigDecimal("-50.00"),
            "00000", "ZEROAPR"
        ));
        seedXRef("5001000500050005", 9005L, acctId);
        seedBalance(acctId, "01", 1, "500.00");
        seedRate("ZEROAPR", "01", 1, "0.00"); // zero rate → no interest

        run();

        assertEquals(BatchStatus.COMPLETED, lastExecution.getStatus());
        assertEquals(0L, transactionRepository.count(),
            "No interest transaction must be written when DIS-INT-RATE = 0");

        var acct = accountRepository.findById(acctId).orElseThrow();
        assertThat(acct.getCurrentBalance()).isEqualByComparingTo("200.00"); // unchanged
        assertThat(acct.getCurrCycleCredit()).isEqualByComparingTo("0.00");  // RULE-009 reset
        assertThat(acct.getCurrCycleDebit()).isEqualByComparingTo("0.00");   // RULE-009 reset
    }

    // =========================================================================
    // Case 6 — DEFAULT group fallback (1200-A-GET-DEFAULT-INT-RATE)
    // =========================================================================

    /**
     * When no DISCGRP record exists for the account's specific group, CBACT04C retries
     * with groupId="DEFAULT".
     *
     * <p>COBOL oracle (1200-GET-INTEREST-RATE):
     * {@code IF DISCGRP-STATUS = '23' → MOVE 'DEFAULT' TO FD-DIS-ACCT-GROUP-ID →
     * PERFORM 1200-A-GET-DEFAULT-INT-RATE → READ DISCGRP-FILE}
     *
     * <p>Seed: account groupId="UNKNOWN", no DISCGRP for "UNKNOWN",
     * but DEFAULT rate = 20.00% exists. Interest = 120 × 20 / 1200 = 2.00.
     */
    @Test
    void defaultGroupFallback_usedWhenSpecificGroupMissing() throws Exception {
        long acctId = 60001L;
        seedAccount(acctId, "300.00", "300.00"); // groupId = "TESTGRP" by default in seedAccount
        // Override to use "UNKNOWN" group — not in DISCGRP
        accountRepository.deleteById(acctId);
        accountRepository.save(new AccountEntity(
            acctId, "Y",
            new BigDecimal("300.00"), new BigDecimal("5000.00"), new BigDecimal("2500.00"),
            "2020-01-01", "2030-01-01", "2025-01-01",
            BigDecimal.ZERO, BigDecimal.ZERO, "00000", "UNKNOWN"
        ));
        seedXRef("6001000600060006", 9006L, acctId);
        seedBalance(acctId, "01", 1, "120.00");
        // No DISCGRP for "UNKNOWN" group — fallback to DEFAULT
        seedRate("DEFAULT", "01", 1, "20.00"); // 120 × 20 / 1200 = 2.00

        run();

        assertEquals(BatchStatus.COMPLETED, lastExecution.getStatus());
        assertEquals(1L, transactionRepository.count(),
            "DEFAULT fallback must produce one interest transaction");
        assertThat(transactionRepository.findAll().get(0).getAmount())
            .isEqualByComparingTo("2.00");
    }

    // =========================================================================
    // Case 7 — Multiple categories: interest summed correctly
    // =========================================================================

    /**
     * One account with two TCATBAL entries (different categories).
     * Both have non-zero rates. Total interest = sum of both.
     *
     * <p>COBOL oracle: WS-TOTAL-INT accumulates across all categories for the account.
     * Two separate TransactionEntity rows are written (one per category with DIS-INT-RATE != 0).
     * The account balance increases by the total.
     *
     * <p>Values:
     * <ul>
     *   <li>cat 0001: balance=$1200, rate=15% → 15.00</li>
     *   <li>cat 0002: balance=$2400, rate=25% → 50.00</li>
     *   <li>total interest = $65.00</li>
     * </ul>
     */
    @Test
    void multipleCategories_interestSummedCorrectly() throws Exception {
        long acctId = 70001L;
        seedAccount(acctId, "1000.00", "1000.00");
        seedXRef("7001000700070007", 9007L, acctId);
        seedBalance(acctId, "01", 1, "1200.00"); // cat 0001
        seedBalance(acctId, "01", 2, "2400.00"); // cat 0002
        seedRate("TESTGRP", "01", 1, "15.00");   // 15.00
        seedRate("TESTGRP", "01", 2, "25.00");   // 50.00

        run();

        assertEquals(BatchStatus.COMPLETED, lastExecution.getStatus());
        assertEquals(2L, transactionRepository.count(),
            "Two interest transactions must be written — one per category");

        var acct = accountRepository.findById(acctId).orElseThrow();
        assertThat(acct.getCurrentBalance())
            .as("Account balance must increase by 15.00 + 50.00 = 65.00")
            .isEqualByComparingTo("1065.00");
    }

    // =========================================================================
    // Case 8 — Empty TCATBAL: job completes with no changes
    // =========================================================================

    /**
     * No TCATBAL records → job COMPLETED, no interest transactions, no account changes.
     *
     * <p>COBOL oracle: the main PERFORM UNTIL loop exits on first read returning EOF.
     * No 1050-UPDATE-ACCOUNT, no 1300-B-WRITE-TX.  GOBACK reached cleanly.
     */
    @Test
    void emptyTcatbal_noInterestComputed() throws Exception {
        // No tranCatBalance records seeded
        seedAccount(10002L, "500.00", "500.00");

        run();

        assertEquals(BatchStatus.COMPLETED, lastExecution.getStatus());
        assertEquals(0L, transactionRepository.count());

        // Account balance unchanged — no interest run
        var acct = accountRepository.findById(10002L).orElseThrow();
        assertThat(acct.getCurrentBalance()).isEqualByComparingTo("500.00");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void run() throws Exception {
        JobParameters params = new JobParametersBuilder()
            .addLong("run.id", System.currentTimeMillis())
            .toJobParameters();
        lastExecution = jobLauncherTestUtils.launchJob(params);
    }

    private void seedAccount(long id, String balance, String creditLimit) {
        accountRepository.save(new AccountEntity(
            id, "Y",
            new BigDecimal(balance), new BigDecimal(creditLimit), new BigDecimal(creditLimit),
            "2020-01-01", "2030-01-01", "2025-01-01",
            BigDecimal.ZERO, BigDecimal.ZERO, "00000", "TESTGRP"
        ));
    }

    private void seedXRef(String cardNumber, long customerId, long accountId) {
        cardXRefRepository.save(new CardXRefEntity(cardNumber, customerId, accountId));
    }

    private void seedBalance(long accountId, String typeCode, int catCode, String balance) {
        tranCatBalanceRepository.save(new TranCatBalanceEntity(
            new TranCatBalanceId(accountId, typeCode, catCode),
            new BigDecimal(balance)
        ));
    }

    private void seedRate(String groupId, String typeCode, int catCode, String rate) {
        discountGroupRepository.save(new DiscountGroupEntity(
            new DiscountGroupId(groupId, typeCode, catCode),
            new BigDecimal(rate)
        ));
    }
}
