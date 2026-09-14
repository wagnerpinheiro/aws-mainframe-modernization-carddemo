package com.carddemo.batch.eod;

import com.carddemo.common.CobolDisplayParser;
import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.CardXRefEntity;
import com.carddemo.domain.entity.TransactionEntity;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXRefRepository;
import com.carddemo.domain.repository.TranCatBalanceRepository;
import com.carddemo.domain.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.item.file.transform.Range;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for CBTRN02C → TransactionPostingJob.
 *
 * <h2>Oracle</h2>
 * The legacy COBOL program CBTRN02C.cbl (731 LOC) defines the ground truth.
 * Every assertion records what the COBOL program <em>actually does</em>, not what
 * a specification says it should do.  Discrepancies from the specification are noted
 * inline as RULE-NNN references.
 *
 * <h2>COBOL validation sequence (1500-VALIDATE-TRAN) replicated exactly</h2>
 * <ol>
 *   <li>1500-A: card in XREF? No → reject 100 "INVALID CARD NUMBER FOUND" → stop</li>
 *   <li>1500-B: account exists? No → reject 101 "ACCOUNT RECORD NOT FOUND"</li>
 *   <li>1500-B (both checks always run, no short-circuit when account found):
 *     <ul>
 *       <li>WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT – ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT;
 *           if ACCT-CREDIT-LIMIT &lt; WS-TEMP-BAL → set reject 102 "OVERLIMIT TRANSACTION"</li>
 *       <li>String-compare ACCT-EXPIRAION-DATE vs DALYTRAN-ORIG-TS(1:10);
 *           if expired → set reject 103 "TRANSACTION RECEIVED AFTER ACCT EXPIRATION"</li>
 *       <li>Critical: both checks run; if both fail, 103 overwrites 102 (last-write wins — COBOL)</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h2>RULE-059 (inactive accounts)</h2>
 * ACCT-ACTIVE-STATUS is NOT checked in CBTRN02C.  Inactive accounts ('N') are
 * posted through.  This is legacy behaviour replicated with a TODO comment in the
 * implementation.
 *
 * <h2>RULE-061 (atomicity)</h2>
 * All three writes (TCATBAL, ACCOUNT, TRANSACT) are wrapped in
 * {@code @Transactional(isolation=SERIALIZABLE)}.  If any write fails, all roll back.
 * The COBOL programme had no such guarantee — REWRITE failures could leave partial state.
 *
 * <h2>Exit status</h2>
 * Job status is always {@code COMPLETED} even when rejects exist; only I/O errors
 * cause {@code FAILED}.  Rejects are handled within the step (written to a reject CSV).
 * This differs from COBOL {@code RETURN-CODE=4} when {@code WS-REJECT-COUNT > 0}.
 *
 * <h2>Current status</h2>
 * All tests <strong>FAIL</strong> until the stubs are implemented:
 * {@link TransactionPostingProcessor#process}, {@link TransactionPostingWriter#open},
 * {@link TransactionPostingWriter#write}, and {@link TransactionPostingService#post}.
 * {@link #emptyFileCompletesCleanly()} also fails because
 * {@link TransactionPostingWriter#open} is called even for a 0-record input.
 * All tests go GREEN when the stubs are replaced with real implementations.
 *
 * <h2>Input override mechanism</h2>
 * The production {@link TransactionPostingJobConfig} binds the reader to
 * {@code @Value("${carddemo.batch.dalytran-input}")}.  Tests that need a custom file
 * set {@link #TEST_INPUT_POSTING} before launching the job.  The inner
 * {@link OverridablePostingReaderConfig} supplies a {@code @StepScope @Primary}
 * override under the bean name {@code "dalytranPostingReader"} — deliberately
 * different from the validation job's {@code "dailyTranReader"} to avoid
 * cross-job interference when both jobs are in the same application context.
 */
@SpringBatchTest
@SpringBootTest(properties = {
    "spring.main.allow-bean-definition-overriding=true",
    "spring.main.banner-mode=off"
})
@ActiveProfiles("test")
class TransactionPostingJobTest {

    // -------------------------------------------------------------------------
    // Overridable reader
    // -------------------------------------------------------------------------

    /**
     * Mutable input resource.  Set before calling {@link #launchJob()}.
     * Reset to the full fixture in {@link #setUp()}.
     */
    static final AtomicReference<Resource> TEST_INPUT_POSTING =
        new AtomicReference<>(new ClassPathResource("fixtures/dailytran.txt"));

    /**
     * Overrides the production {@code dalytranPostingReader} bean with a
     * {@code @StepScope} variant that reads from {@link #TEST_INPUT_POSTING} at
     * step-start time.
     *
     * <p>Bean name {@code "dalytranPostingReader"} (from method name) shadows the
     * production bean of the same name; overriding is enabled via the
     * {@code spring.main.allow-bean-definition-overriding=true} property.
     *
     * <p>The tokenizer configuration is replicated intentionally — if the production
     * column ranges drift, this test catches the regression.
     */
    @TestConfiguration
    static class OverridablePostingReaderConfig {

        @Bean("dalytranPostingReader")
        @StepScope
        @Primary
        FlatFileItemReader<DailyTransactionRecord> dalytranPostingReader() {
            var tokenizer = new FixedLengthTokenizer();
            tokenizer.setNames(
                "transactionId", "typeCode", "categoryCode", "source", "description",
                "amount", "merchantId", "merchantName", "merchantCity", "merchantZip",
                "cardNumber", "originTs", "processTs"
            );
            // Column ranges must match TransactionPostingJobConfig exactly (CVTRA06Y).
            tokenizer.setColumns(
                new Range(1,   16),  // DALYTRAN-ID
                new Range(17,  18),  // DALYTRAN-TYPE-CD
                new Range(19,  22),  // DALYTRAN-CAT-CD
                new Range(23,  32),  // DALYTRAN-SOURCE
                new Range(33, 132),  // DALYTRAN-DESC
                new Range(133, 143), // DALYTRAN-AMT  S9(9)V99 sign-overpunch
                new Range(144, 152), // DALYTRAN-MERCHANT-ID
                new Range(153, 202), // DALYTRAN-MERCHANT-NAME
                new Range(203, 252), // DALYTRAN-MERCHANT-CITY
                new Range(253, 262), // DALYTRAN-MERCHANT-ZIP
                new Range(263, 278), // DALYTRAN-CARD-NUM  — XREF lookup key
                new Range(279, 304), // DALYTRAN-ORIG-TS   — first 10 chars used in expiry check
                new Range(305, 330)  // DALYTRAN-PROC-TS
            );
            tokenizer.setStrict(false);

            return new FlatFileItemReaderBuilder<DailyTransactionRecord>()
                .name("dalytranPostingReader")
                .resource(TEST_INPUT_POSTING.get())   // resolved at step-start time
                .lineTokenizer(tokenizer)
                .fieldSetMapper(fs -> new DailyTransactionRecord(
                    fs.readString("transactionId").trim(),
                    fs.readString("typeCode").trim(),
                    Integer.parseInt(fs.readString("categoryCode").trim()),
                    fs.readString("source").trim(),
                    fs.readString("description").trim(),
                    CobolDisplayParser.parseSignedAmount(fs.readString("amount"), 2),
                    Long.parseLong(fs.readString("merchantId").trim()),
                    fs.readString("merchantName").trim(),
                    fs.readString("merchantCity").trim(),
                    fs.readString("merchantZip").trim(),
                    fs.readString("cardNumber").trim(),
                    fs.readString("originTs").trim(),
                    fs.readString("processTs").trim()
                ))
                .build();
        }
    }

    // -------------------------------------------------------------------------
    // Test infrastructure
    // -------------------------------------------------------------------------

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    /**
     * Stores the most recent job execution from {@link #executePostingJob()}.
     * Declared as a field (rather than returned from the helper) so the helper's
     * return type can be {@code void} — this prevents
     * {@link org.springframework.batch.test.JobScopeTestExecutionListener} from
     * discovering the method via return-type scan ({@code JobExecution.class.isAssignableFrom})
     * and trying to invoke it during test-instance preparation before {@code @BeforeEach} runs.
     */
    private JobExecution lastExecution;

    /**
     * Injected by qualifier because both transactionPostingJob and transactionValidationJob
     * exist in the context. Without the qualifier, JobLauncherTestUtils autowiring is
     * ambiguous. The qualifier is resolved to the bean name defined in
     * {@link TransactionPostingJobConfig}.
     */
    @Autowired
    @Qualifier("transactionPostingJob")
    private Job postingJob;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CardXRefRepository cardXRefRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TranCatBalanceRepository tranCatBalanceRepository;

    /**
     * Reject CSV path from {@code src/test/resources/application-test.yml}:
     * {@code ${java.io.tmpdir}/carddemo-test-posting-rejects.csv}
     */
    @Value("${carddemo.batch.posting-reject-output}")
    private String postingRejectOutputPath;

    @BeforeEach
    void setUp() throws Exception {
        // Target the posting job when multiple Job beans exist in context.
        jobLauncherTestUtils.setJob(postingJob);

        // Clean Spring Batch metadata so the same job can be re-run.
        jobRepositoryTestUtils.removeJobExecutions();

        // Clear domain data in dependency order (transactions reference accounts).
        transactionRepository.deleteAll();
        tranCatBalanceRepository.deleteAll();
        cardXRefRepository.deleteAll();
        accountRepository.deleteAll();

        // Reset to full fixture input; individual tests override before calling executePostingJob().
        TEST_INPUT_POSTING.set(new ClassPathResource("fixtures/dailytran.txt"));

        // Delete any reject file left by the previous test.
        Files.deleteIfExists(Path.of(postingRejectOutputPath));

        lastExecution = null;
    }

    // =========================================================================
    // Case 1 — Golden-master: all 300 records valid
    // =========================================================================

    /**
     * Full fixture run — all 300 DALYTRAN records have matching XREF and account entries,
     * are within their credit limit, and have unexpired accounts.
     *
     * <p>COBOL oracle: every record reaches paragraph 2000-POST-TRANSACTION and executes
     * 2700-UPDATE-TCATBAL, 2800-UPDATE-ACCOUNT-REC, and 2900-WRITE-TRANSACTION-FILE.
     * No "COULD NOT BE VERIFIED" output; RETURN-CODE=0.
     *
     * <p>Derivation (static analysis of fixture files):
     * <ul>
     *   <li>dailytran.txt: 300 records; cards reference entries in cardxref.txt</li>
     *   <li>cardxref.txt: 50 entries; all account IDs present in acctdata.txt</li>
     *   <li>acctdata.txt: all ACCT-ACTIVE-STATUS='Y', credit limits well above cycle balances,
     *       expiration dates between 2024 and 2030 (all after the fixture originTs of
     *       2022-06-10 embedded in dailytran.txt)</li>
     * </ul>
     *
     * <p><strong>Currently FAILS</strong> — {@link TransactionPostingWriter#open} throws
     * {@link UnsupportedOperationException} before any records are processed.
     */
    @Test
    void happyPath_allValid() throws Exception {
        FixtureLoader.loadFullFixtures(cardXRefRepository, accountRepository);
        // TCATBAL starts empty; the job creates all rows via the insert path of
        // 2700-UPDATE-TCATBAL ("if not found → create with balance=amount").

        executePostingJob();
        JobExecution execution = lastExecution;

        // Job-level: always COMPLETED even with 0 rejects (Java exit-status policy).
        assertEquals(BatchStatus.COMPLETED, execution.getStatus(),
            "Job must COMPLETE when all 300 DALYTRAN records are valid");

        // Step-level: all 300 records read and written.
        Collection<StepExecution> steps = execution.getStepExecutions();
        assertThat(steps).hasSize(1);
        StepExecution step = steps.iterator().next();
        assertEquals(300, step.getReadCount(),
            "Reader must consume all 300 records from dailytran.txt");
        assertEquals(0, step.getSkipCount(),
            "No records should be skipped in the all-valid scenario");

        // Persistence: 300 TransactionEntity rows written (2900-WRITE-TRANSACTION-FILE × 300).
        assertEquals(300L, transactionRepository.count(),
            "300 TransactionEntity rows must be saved after posting all valid records");

        // Account balances: at least one account must have a non-zero cycle balance
        // (2800-UPDATE-ACCOUNT-REC updates ACCT-CURR-CYC-CREDIT or ACCT-CURR-CYC-DEBIT
        //  for every posted transaction).
        boolean anyAccountUpdated = accountRepository.findAll().stream()
            .anyMatch(a -> a.getCurrCycleCredit().compareTo(BigDecimal.ZERO) != 0
                       || a.getCurrCycleDebit().compareTo(BigDecimal.ZERO) != 0);
        assertTrue(anyAccountUpdated,
            "At least one account's cycle balance must be non-zero after posting 300 transactions");

        // TCATBAL: at least one row must have been created (2700-UPDATE-TCATBAL).
        assertThat(tranCatBalanceRepository.count())
            .as("TCATBAL must have at least one row after posting 300 transactions")
            .isGreaterThan(0L);

        // Reject CSV: no rows with reject codes (all records are valid).
        assertEquals(0L, countRejectRows(Path.of(postingRejectOutputPath)),
            "Reject CSV must have 0 data rows when all 300 records are valid");
    }

    // =========================================================================
    // Case 2 — 1500-A: card not in XREF → reject 100
    // =========================================================================

    /**
     * DALYTRAN with an unknown card number — the card is absent from XREF.
     *
     * <p>COBOL oracle (CBTRN02C 1500-A-LOOKUP-XREF, lines 380–392):
     * {@code XREF-FILE INVALID KEY → WS-XREF-READ-STATUS = 4} →
     * {@code WS-VALIDATION-FAIL-REASON = 100} "INVALID CARD NUMBER FOUND" →
     * paragraph 1500-B is skipped entirely → record goes to DALYREJS file.
     *
     * <p>Seed: one valid card/account pair (tables not empty); one synthetic DALYTRAN
     * with card {@code 9999999999999999} which has no XREF entry.
     *
     * <p><strong>Currently FAILS</strong> — writer stub throws.
     */
    @Test
    void rejectCode100_cardNotInXref() throws Exception {
        // One valid pair so the tables are not completely empty.
        accountRepository.save(validAccount(1001L));
        cardXRefRepository.save(new CardXRefEntity("1111111111111111", 9001L, 1001L));

        // 9999999999999999 (16 nines) is not seeded → triggers 1500-A INVALID KEY.
        final String unknownCard   = "9999999999999999";
        final String transactionId = "REJECTCARD000001";
        TEST_INPUT_POSTING.set(new FileSystemResource(
            SyntheticDalytranBuilder.writeToTempFile(List.of(
                SyntheticDalytranBuilder.buildRecord(transactionId, unknownCard)
            ))
        ));

        executePostingJob();
        JobExecution execution = lastExecution;

        // Job-level: COMPLETED (reject handled within step; no abend).
        assertEquals(BatchStatus.COMPLETED, execution.getStatus(),
            "Job must COMPLETE on XREF miss — COBOL skips with DISPLAY, does not abend");

        // Persistence: no transaction written (card validation failed).
        assertEquals(0L, transactionRepository.count(),
            "No TransactionEntity must be saved when the card is not in XREF");

        // Reject CSV: exactly 1 row containing reject code 100.
        List<String> rejectRows = rejectDataRows(Path.of(postingRejectOutputPath));
        assertThat(rejectRows)
            .as("Exactly 1 reject row for CARD_NOT_FOUND")
            .hasSize(1);
        assertThat(rejectRows.get(0))
            .as("Reject row must contain code 100 (INVALID CARD NUMBER FOUND)")
            .contains("100");
    }

    // =========================================================================
    // Case 3 — 1500-B: XREF found but account absent → reject 101
    // =========================================================================

    /**
     * XREF entry exists but the referenced account is absent from ACCOUNT-FILE.
     *
     * <p>COBOL oracle (CBTRN02C 1500-B-LOOKUP-ACCT, lines 393–421):
     * {@code ACCOUNT-FILE INVALID KEY → WS-ACCT-READ-STATUS = 4} →
     * {@code WS-VALIDATION-FAIL-REASON = 101} "ACCOUNT RECORD NOT FOUND".
     * The overlimit and expiry checks are skipped (account was not loaded).
     *
     * <p>Seed: XREF row pointing to account 88888, but no AccountEntity with id=88888.
     *
     * <p><strong>Currently FAILS</strong> — writer stub throws.
     */
    @Test
    void rejectCode101_accountNotFound() throws Exception {
        final String cardNumber    = "8888888888888888";
        final long   missingAcctId = 88888L;
        final String transactionId = "REJECTACCT000001";

        // XREF points to account 88888; no AccountEntity with that id → INVALID KEY.
        cardXRefRepository.save(new CardXRefEntity(cardNumber, 8001L, missingAcctId));
        // Deliberately no accountRepository.save() — missing account triggers reject 101.

        TEST_INPUT_POSTING.set(new FileSystemResource(
            SyntheticDalytranBuilder.writeToTempFile(List.of(
                SyntheticDalytranBuilder.buildRecord(transactionId, cardNumber)
            ))
        ));

        executePostingJob();
        JobExecution execution = lastExecution;

        assertEquals(BatchStatus.COMPLETED, execution.getStatus(),
            "Job must COMPLETE on missing account — COBOL DISPLAYs and continues, does not abend");

        assertEquals(0L, transactionRepository.count(),
            "No TransactionEntity must be saved when the account is absent");

        List<String> rejectRows = rejectDataRows(Path.of(postingRejectOutputPath));
        assertThat(rejectRows)
            .as("Exactly 1 reject row for ACCOUNT_NOT_FOUND")
            .hasSize(1);
        assertThat(rejectRows.get(0))
            .as("Reject row must contain code 101 (ACCOUNT RECORD NOT FOUND)")
            .contains("101");
    }

    // =========================================================================
    // Case 4 — 1500-B overlimit check → reject 102
    // =========================================================================

    /**
     * Account's projected cycle balance would exceed its credit limit.
     *
     * <p>COBOL oracle (CBTRN02C 1500-B-LOOKUP-ACCT):
     * {@code WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT – ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT}.
     * {@code IF ACCT-CREDIT-LIMIT < WS-TEMP-BAL → WS-VALIDATION-FAIL-REASON = 102
     * "OVERLIMIT TRANSACTION"}.
     *
     * <p>Concrete values:
     * <ul>
     *   <li>creditLimit = $50.00 (intentionally below the $100.00 default transaction amount)</li>
     *   <li>currCycleCredit = $0.00, currCycleDebit = $0.00</li>
     *   <li>DALYTRAN-AMT = $100.00 (SyntheticDalytranBuilder default)</li>
     *   <li>WS-TEMP-BAL = 0 – 0 + 100 = $100.00 &gt; creditLimit $50.00 → overlimit</li>
     * </ul>
     *
     * <p><strong>Currently FAILS</strong> — writer stub throws.
     */
    @Test
    void rejectCode102_overlimit() throws Exception {
        final String cardNumber    = "7777777777777777";
        final long   acctId        = 77701L;
        final String transactionId = "REJECTOVLM000001";

        // creditLimit $50.00 < (0 – 0 + $100.00) = $100.00 → OVERLIMIT.
        // expirationDate "2030-01-01" is safely after the synthetic originTs "2024-01-01"
        // so the expiry check passes; only the overlimit check fires → reject 102.
        accountRepository.save(new AccountEntity(
            acctId,
            "Y",
            new BigDecimal("0.00"),    // ACCT-CURR-BAL
            new BigDecimal("50.00"),   // ACCT-CREDIT-LIMIT — below $100 transaction
            new BigDecimal("25.00"),   // ACCT-CASH-CREDIT-LIMIT
            "2020-01-01",
            "2030-01-01",              // ACCT-EXPIRAION-DATE: not expired
            "2025-01-01",
            BigDecimal.ZERO,           // ACCT-CURR-CYC-CREDIT
            BigDecimal.ZERO,           // ACCT-CURR-CYC-DEBIT
            "00000",
            "TESTGRP"
        ));
        cardXRefRepository.save(new CardXRefEntity(cardNumber, 7001L, acctId));

        TEST_INPUT_POSTING.set(new FileSystemResource(
            SyntheticDalytranBuilder.writeToTempFile(List.of(
                // Default amount $100.00 → WS-TEMP-BAL $100 > creditLimit $50 → reject 102.
                SyntheticDalytranBuilder.buildRecord(transactionId, cardNumber)
            ))
        ));

        executePostingJob();
        JobExecution execution = lastExecution;

        assertEquals(BatchStatus.COMPLETED, execution.getStatus(),
            "Job must COMPLETE on overlimit — COBOL marks reject, continues, does not abend");

        assertEquals(0L, transactionRepository.count(),
            "No TransactionEntity must be saved for an overlimit transaction");

        List<String> rejectRows = rejectDataRows(Path.of(postingRejectOutputPath));
        assertThat(rejectRows)
            .as("Exactly 1 reject row for OVERLIMIT")
            .hasSize(1);
        assertThat(rejectRows.get(0))
            .as("Reject row must contain code 102 (OVERLIMIT TRANSACTION)")
            .contains("102");
    }

    // =========================================================================
    // Case 5 — 1500-B expiry check → reject 103
    // =========================================================================

    /**
     * Account's expiration date is before the transaction origin date.
     *
     * <p>COBOL oracle (CBTRN02C 1500-B-LOOKUP-ACCT):
     * String-compare {@code ACCT-EXPIRAION-DATE} (10 chars) with first 10 chars of
     * {@code DALYTRAN-ORIG-TS}.  If {@code ACCT-EXPIRAION-DATE &lt; DALYTRAN-ORIG-TS(1:10)}
     * → expired → {@code WS-VALIDATION-FAIL-REASON = 103}
     * "TRANSACTION RECEIVED AFTER ACCT EXPIRATION".
     *
     * <p>Concrete values:
     * <ul>
     *   <li>ACCT-EXPIRAION-DATE = "2023-12-31"</li>
     *   <li>DALYTRAN-ORIG-TS(1:10) = "2024-01-01" (SyntheticDalytranBuilder default)</li>
     *   <li>String compare: "2023-12-31" &lt; "2024-01-01" → account expired → reject 103</li>
     * </ul>
     * creditLimit is set large ($5000) to ensure the overlimit check passes.
     *
     * <p><strong>Currently FAILS</strong> — writer stub throws.
     */
    @Test
    void rejectCode103_accountExpired() throws Exception {
        final String cardNumber    = "6666666666666666";
        final long   acctId        = 66601L;
        final String transactionId = "REJECTEXPD000001";

        // creditLimit $5000 >> $100 transaction → overlimit check passes.
        // expirationDate "2023-12-31" < "2024-01-01" (first 10 chars of synthetic originTs) → expired.
        accountRepository.save(new AccountEntity(
            acctId,
            "Y",
            new BigDecimal("0.00"),
            new BigDecimal("5000.00"),  // ACCT-CREDIT-LIMIT: ample — overlimit check passes
            new BigDecimal("2500.00"),
            "2015-01-01",
            "2023-12-31",              // ACCT-EXPIRAION-DATE: before synthetic "2024-01-01"
            "2023-06-01",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "00000",
            "TESTGRP"
        ));
        cardXRefRepository.save(new CardXRefEntity(cardNumber, 6001L, acctId));

        TEST_INPUT_POSTING.set(new FileSystemResource(
            SyntheticDalytranBuilder.writeToTempFile(List.of(
                // originTs "2024-01-01 00:00:00.000000" (SyntheticDalytranBuilder default).
                SyntheticDalytranBuilder.buildRecord(transactionId, cardNumber)
            ))
        ));

        executePostingJob();
        JobExecution execution = lastExecution;

        assertEquals(BatchStatus.COMPLETED, execution.getStatus(),
            "Job must COMPLETE on expired account — COBOL marks reject, continues");

        assertEquals(0L, transactionRepository.count(),
            "No TransactionEntity must be saved for a transaction on an expired account");

        List<String> rejectRows = rejectDataRows(Path.of(postingRejectOutputPath));
        assertThat(rejectRows)
            .as("Exactly 1 reject row for EXPIRED")
            .hasSize(1);
        assertThat(rejectRows.get(0))
            .as("Reject row must contain code 103 (TRANSACTION RECEIVED AFTER ACCT EXPIRATION)")
            .contains("103");
    }

    // =========================================================================
    // Case 6 — Both overlimit AND expired → code 103 wins (COBOL last-write-wins)
    // =========================================================================

    /**
     * Both overlimit (would set 102) and expired (would set 103) conditions are true.
     *
     * <p>COBOL oracle (CBTRN02C 1500-B-LOOKUP-ACCT):
     * Both checks always execute without short-circuit.  The overlimit check runs first
     * and sets {@code WS-VALIDATION-FAIL-REASON = 102}.  The expiry check runs second
     * and sets {@code WS-VALIDATION-FAIL-REASON = 103}, overwriting 102.
     * The last assignment wins.  Final reject code = <strong>103</strong>.
     *
     * <p>This "last-write-wins" behaviour arises from the COBOL structure:
     * both IF branches modify the same working-storage field; no early exit is inserted.
     *
     * <p>Concrete values:
     * <ul>
     *   <li>creditLimit = $50.00 &lt; $100.00 transaction → overlimit → would set 102</li>
     *   <li>expirationDate "2023-12-31" &lt; "2024-01-01" → expired → sets 103 (overwrites)</li>
     * </ul>
     *
     * <p><strong>Currently FAILS</strong> — writer stub throws.
     */
    @Test
    void bothOverlimitAndExpired_code103wins() throws Exception {
        final String cardNumber    = "5555555555555555";
        final long   acctId        = 55501L;
        final String transactionId = "REJECTBOTH000001";

        // Both failure conditions active simultaneously.
        accountRepository.save(new AccountEntity(
            acctId,
            "Y",
            new BigDecimal("0.00"),
            new BigDecimal("50.00"),    // overlimit: $50 < $100 transaction → sets reject 102
            new BigDecimal("25.00"),
            "2015-01-01",
            "2023-12-31",              // expired: "2023-12-31" < "2024-01-01" → sets reject 103
            "2023-06-01",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "00000",
            "TESTGRP"
        ));
        cardXRefRepository.save(new CardXRefEntity(cardNumber, 5001L, acctId));

        TEST_INPUT_POSTING.set(new FileSystemResource(
            SyntheticDalytranBuilder.writeToTempFile(List.of(
                SyntheticDalytranBuilder.buildRecord(transactionId, cardNumber)
            ))
        ));

        executePostingJob();
        JobExecution execution = lastExecution;

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertEquals(0L, transactionRepository.count());

        List<String> rejectRows = rejectDataRows(Path.of(postingRejectOutputPath));
        assertThat(rejectRows)
            .as("Exactly 1 reject row — both conditions apply to the same record")
            .hasSize(1);

        // COBOL last-write-wins: expired check (103) executes after overlimit check (102),
        // so 103 is the final reject code.
        assertThat(rejectRows.get(0))
            .as("Reject code 103 must appear — it overwrites 102 (COBOL last-write-wins)")
            .contains("103");
        assertThat(rejectRows.get(0))
            .as("Reject code 102 must NOT appear — 103 is the final value (last-write-wins)")
            .doesNotContain("102");
    }

    // =========================================================================
    // Case 7 — RULE-059: inactive account is posted (ACCT-ACTIVE-STATUS not checked)
    // =========================================================================

    /**
     * Account with {@code ACCT-ACTIVE-STATUS='N'} but otherwise valid.
     *
     * <p>COBOL oracle (CBTRN02C.cbl, full programme): ACCT-ACTIVE-STATUS is read from
     * the account record but never tested in any IF or EVALUATE.  Paragraph
     * 1500-VALIDATE-TRAN checks only XREF presence, account presence, overlimit, and
     * expiry.  A status of 'N' falls through to 2000-POST-TRANSACTION unchanged.
     *
     * <p>This is a known discrepancy between the legacy programme and business intent,
     * captured as RULE-059.  The modern implementation replicates this behaviour and
     * adds a TODO comment rather than fixing it (fix is a separate decision).
     *
     * <p><strong>Currently FAILS</strong> — writer stub throws.
     */
    @Test
    void rule059_inactiveAccountIsPosted() throws Exception {
        final String cardNumber    = "4444444444444444";
        final long   acctId        = 44401L;
        final String transactionId = "RULE059TXN000001";

        // activeStatus = 'N' — inactive, but CBTRN02C does NOT gate on this field.
        accountRepository.save(new AccountEntity(
            acctId,
            "N",                        // ACCT-ACTIVE-STATUS='N' — must still post (RULE-059)
            new BigDecimal("0.00"),
            new BigDecimal("5000.00"),  // credit limit ample — overlimit check passes
            new BigDecimal("2500.00"),
            "2020-01-01",
            "2030-01-01",              // not expired
            "2025-01-01",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "00000",
            "TESTGRP"
        ));
        cardXRefRepository.save(new CardXRefEntity(cardNumber, 4001L, acctId));

        TEST_INPUT_POSTING.set(new FileSystemResource(
            SyntheticDalytranBuilder.writeToTempFile(List.of(
                SyntheticDalytranBuilder.buildRecord(transactionId, cardNumber)
            ))
        ));

        executePostingJob();
        JobExecution execution = lastExecution;

        assertEquals(BatchStatus.COMPLETED, execution.getStatus(),
            "Job must COMPLETE — inactive account is not a rejection condition in CBTRN02C");

        // RULE-059: inactive account does NOT block posting — 1 TransactionEntity saved.
        assertEquals(1L, transactionRepository.count(),
            "RULE-059: transaction must be saved for inactive account (CBTRN02C does not check ACCT-ACTIVE-STATUS)");

        assertEquals(0L, countRejectRows(Path.of(postingRejectOutputPath)),
            "No rejects expected — inactive account is treated identically to active in CBTRN02C");
    }

    // =========================================================================
    // Case 8 — RULE-061: atomicity — duplicate transaction ID triggers full rollback
    // =========================================================================

    /**
     * A duplicate transaction ID already in the database triggers a constraint violation
     * that rolls back all three writes atomically.
     *
     * <p>COBOL context (CBTRN02C 2900-WRITE-TRANSACTION-FILE):
     * COBOL WRITE on VSAM KSDS fails with status 22 (duplicate key).  In the legacy
     * programme this left TCATBAL and ACCOUNT partially updated (no rollback).
     * RULE-061 fixes this by wrapping all three writes in
     * {@code @Transactional(isolation=SERIALIZABLE)}.
     *
     * <p>Test mechanism:
     * <ol>
     *   <li>Pre-save a {@link TransactionEntity} with {@code id = "RULE061TXN000001"}.</li>
     *   <li>Feed a DALYTRAN record with the same transaction ID.</li>
     *   <li>The service calls {@code transactionRepository} with INSERT semantics (not merge);
     *       H2 raises a duplicate-key error.</li>
     *   <li>{@code @Transactional} rolls back: TCATBAL write and account balance write
     *       both roll back even though they were written before the transaction write.</li>
     * </ol>
     *
     * <p><strong>Implementation requirement:</strong> the service must use INSERT semantics
     * (e.g., {@code entityManager.persist()}, or an {@code existsById()} guard that throws
     * on duplicate) rather than {@code save()} (which does a merge/UPDATE on existing IDs
     * and would not trigger a constraint violation).  This replicates VSAM WRITE-not-REWRITE
     * semantics from CBTRN02C paragraph 2900.
     *
     * <p><strong>Currently FAILS</strong> — writer stub throws before any writes occur.
     */
    @Test
    void rule061_atomicRollback() throws Exception {
        final String cardNumber    = "3333333333333333";
        final long   acctId        = 33301L;
        final String transactionId = "RULE061TXN000001";

        AccountEntity account = validAccount(acctId);
        BigDecimal originalBalance = account.getCurrentBalance();
        accountRepository.save(account);
        cardXRefRepository.save(new CardXRefEntity(cardNumber, 3001L, acctId));

        // Pre-seed the exact transaction ID that the synthetic DALYTRAN record will use.
        // This forces a duplicate-key violation when the service attempts to insert the
        // same ID during 2900-WRITE-TRANSACTION-FILE, triggering the SERIALIZABLE rollback.
        // Credentials/secrets: none — all values are synthetic placeholders of the correct shape.
        TransactionEntity preExistingTransaction = new TransactionEntity(
            transactionId,              // tran_id X(16) — matches synthetic DALYTRAN record
            "01",
            1,
            "PRESEED",
            "Pre-existing transaction seeded for duplicate-key rollback test",
            new BigDecimal("50.00"),
            999000001L,                 // synthetic merchant ID (no real merchant; placeholder shape 9(9))
            "Pre-Merchant",
            "Pre-City",
            "00000",
            cardNumber,
            "2024-01-01 00:00:00.000000",
            "2024-01-01 00:00:00.000000"
        );
        transactionRepository.save(preExistingTransaction);

        TEST_INPUT_POSTING.set(new FileSystemResource(
            SyntheticDalytranBuilder.writeToTempFile(List.of(
                // This record has the same transactionId → duplicate-key on write.
                SyntheticDalytranBuilder.buildRecord(transactionId, cardNumber)
            ))
        ));

        // Job status may be FAILED (if the error propagates as step failure)
        // or COMPLETED (if the step has a skip policy for constraint violations).
        // Either way, RULE-061 requires that all three writes roll back.
        executePostingJob();

        // RULE-061 assertion 1: TransactionEntity count unchanged — only the pre-existing row.
        assertEquals(1L, transactionRepository.count(),
            "RULE-061: duplicate transaction ID must cause rollback; only the pre-seeded row remains");

        // RULE-061 assertion 2: account balance unchanged from pre-run value.
        AccountEntity reloadedAccount = accountRepository.findById(acctId).orElseThrow();
        assertEquals(0, originalBalance.compareTo(reloadedAccount.getCurrentBalance()),
            "RULE-061: account balance must not change when the transaction write rolls back");

        // RULE-061 assertion 3: no TCATBAL row created (the 2700 write rolled back with the rest).
        assertEquals(0L, tranCatBalanceRepository.count(),
            "RULE-061: TCATBAL update must be rolled back if the subsequent transaction write fails");
    }

    // =========================================================================
    // Case 9 — Empty DALYTRAN file → job completes with 0 reads
    // =========================================================================

    /**
     * Empty DALYTRAN input (0 bytes) → {@code COMPLETED} with {@code readCount=0}.
     *
     * <p>COBOL oracle: the main PERFORM UNTIL loop exits immediately when
     * DALYTRAN-STATUS='10' on the first READ (END-OF-DAILY-TRANS-FILE='Y').
     * No 2000-POST-TRANSACTION or reject writes; GOBACK reached cleanly.
     *
     * <p><strong>Currently FAILS</strong> — {@link TransactionPostingWriter#open} is
     * called even for 0-record input and throws {@link UnsupportedOperationException}.
     * Once the writer is implemented this test becomes the first to go GREEN.
     */
    @Test
    void emptyFileCompletesCleanly() throws Exception {
        // No domain data needed — no records will be read or processed.
        TEST_INPUT_POSTING.set(new FileSystemResource(SyntheticDalytranBuilder.emptyTempFile()));

        executePostingJob();
        JobExecution execution = lastExecution;

        assertEquals(BatchStatus.COMPLETED, execution.getStatus(),
            "Job must COMPLETE on empty input — mirrors COBOL clean exit with 0 records");

        Collection<StepExecution> steps = execution.getStepExecutions();
        assertThat(steps).hasSize(1);
        StepExecution step = steps.iterator().next();
        assertEquals(0, step.getReadCount(),
            "readCount must be 0 for an empty DALYTRAN file");
        assertEquals(0, step.getWriteCount(),
            "writeCount must be 0 — nothing to write");

        assertEquals(0L, transactionRepository.count());
        assertEquals(0L, tranCatBalanceRepository.count());
        assertEquals(0L, countRejectRows(Path.of(postingRejectOutputPath)),
            "Reject CSV must have 0 rows for an empty input file");
    }

    // =========================================================================
    // Case 10 — Mixed: 2 valid + 1 code-100 + 1 code-102 + 1 code-103
    // =========================================================================

    /**
     * Five DALYTRAN records with five distinct outcomes.
     *
     * <table border="1">
     *   <tr><th>Card</th><th>XREF?</th><th>Account?</th><th>Limit OK?</th><th>Expired?</th><th>Expected</th></tr>
     *   <tr><td>2222111100000001</td><td>Y</td><td>Y</td><td>Y</td><td>N</td><td>VALID → posted</td></tr>
     *   <tr><td>2222111100000002</td><td>Y</td><td>Y</td><td>Y</td><td>N</td><td>VALID → posted</td></tr>
     *   <tr><td>9998888877776666</td><td>N</td><td>N/A</td><td>N/A</td><td>N/A</td><td>reject 100</td></tr>
     *   <tr><td>2222111100000003</td><td>Y</td><td>Y</td><td>N (overlimit)</td><td>N</td><td>reject 102</td></tr>
     *   <tr><td>2222111100000004</td><td>Y</td><td>Y</td><td>Y</td><td>Y (expired)</td><td>reject 103</td></tr>
     * </table>
     *
     * <p>COBOL oracle: each record is processed independently in the PERFORM UNTIL loop.
     * Records with {@code WS-VALIDATION-FAIL-REASON = 0} go to 2000-POST-TRANSACTION;
     * others go to DALYREJS.  All five records are consumed without abend.
     *
     * <p><strong>Currently FAILS</strong> — writer stub throws.
     */
    @Test
    void mixedValidAndInvalid() throws Exception {
        // --- Valid record 1 ---
        final String validCard1   = "2222111100000001";
        final long   validAcctId1 = 22201L;
        accountRepository.save(validAccount(validAcctId1));
        cardXRefRepository.save(new CardXRefEntity(validCard1, 2001L, validAcctId1));

        // --- Valid record 2 ---
        final String validCard2   = "2222111100000002";
        final long   validAcctId2 = 22202L;
        accountRepository.save(validAccount(validAcctId2));
        cardXRefRepository.save(new CardXRefEntity(validCard2, 2002L, validAcctId2));

        // --- CARD_NOT_FOUND: no XREF entry → reject 100 ---
        final String cardNotFoundCard = "9998888877776666";
        // Deliberately no cardXRefRepository.save() for this card.

        // --- OVERLIMIT: creditLimit $50 < $100 transaction → reject 102 ---
        final String overlimitCard   = "2222111100000003";
        final long   overlimitAcctId = 22203L;
        accountRepository.save(new AccountEntity(
            overlimitAcctId,
            "Y",
            new BigDecimal("0.00"),
            new BigDecimal("50.00"),   // credit limit below default $100 transaction
            new BigDecimal("25.00"),
            "2020-01-01",
            "2030-01-01",             // not expired
            "2025-01-01",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "00000",
            "TESTGRP"
        ));
        cardXRefRepository.save(new CardXRefEntity(overlimitCard, 2003L, overlimitAcctId));

        // --- EXPIRED: expirationDate before synthetic originTs → reject 103 ---
        final String expiredCard   = "2222111100000004";
        final long   expiredAcctId = 22204L;
        accountRepository.save(new AccountEntity(
            expiredAcctId,
            "Y",
            new BigDecimal("0.00"),
            new BigDecimal("5000.00"), // credit limit ample — only expiry fires
            new BigDecimal("2500.00"),
            "2015-01-01",
            "2023-12-31",             // "2023-12-31" < "2024-01-01" (synthetic originTs)
            "2023-06-01",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "00000",
            "TESTGRP"
        ));
        cardXRefRepository.save(new CardXRefEntity(expiredCard, 2004L, expiredAcctId));

        TEST_INPUT_POSTING.set(new FileSystemResource(
            SyntheticDalytranBuilder.writeToTempFile(List.of(
                SyntheticDalytranBuilder.buildRecord("MIXEDTXN00000001", validCard1),
                SyntheticDalytranBuilder.buildRecord("MIXEDTXN00000002", validCard2),
                SyntheticDalytranBuilder.buildRecord("MIXEDTXN00000003", cardNotFoundCard),
                SyntheticDalytranBuilder.buildRecord("MIXEDTXN00000004", overlimitCard),
                SyntheticDalytranBuilder.buildRecord("MIXEDTXN00000005", expiredCard)
            ))
        ));

        executePostingJob();
        JobExecution execution = lastExecution;

        // Job-level: always COMPLETED for mixed input (rejects are handled within the step).
        assertEquals(BatchStatus.COMPLETED, execution.getStatus(),
            "Job must COMPLETE for mixed valid/invalid input");

        // Persistence: exactly 2 valid records posted.
        assertEquals(2L, transactionRepository.count(),
            "Exactly 2 TransactionEntity rows must be saved (the 2 valid records)");

        // Reject CSV: exactly 3 rows, one per rejection code.
        List<String> rejectRows = rejectDataRows(Path.of(postingRejectOutputPath));
        assertThat(rejectRows)
            .as("Exactly 3 reject rows: one per rejected DALYTRAN record")
            .hasSize(3);
        assertThat(rejectRows.stream().anyMatch(r -> r.contains("100")))
            .as("One reject row must contain code 100 (CARD_NOT_FOUND)").isTrue();
        assertThat(rejectRows.stream().anyMatch(r -> r.contains("102")))
            .as("One reject row must contain code 102 (OVERLIMIT)").isTrue();
        assertThat(rejectRows.stream().anyMatch(r -> r.contains("103")))
            .as("One reject row must contain code 103 (EXPIRED)").isTrue();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Runs the posting job with a unique {@code run.id} parameter and stores the
     * outcome in {@link #lastExecution}.
     *
     * <p>Return type is intentionally {@code void} so that
     * {@link org.springframework.batch.test.JobScopeTestExecutionListener} cannot
     * discover this helper via its return-type scan
     * ({@code JobExecution.class.isAssignableFrom(method.getReturnType())}).
     * If the listener discovered a method returning {@code JobExecution} it would
     * invoke it during test-instance preparation — before {@code @BeforeEach} sets
     * the job on {@link JobLauncherTestUtils} — causing "The Job must not be null".
     */
    private void executePostingJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
            .addLong("run.id", System.currentTimeMillis())
            .toJobParameters();
        lastExecution = jobLauncherTestUtils.launchJob(params);
    }

    /**
     * Returns lines from the reject CSV that contain a numeric reject code (100–103).
     *
     * <p>The filter is intentionally format-agnostic: it does not assume a specific
     * header, column order, or delimiter.  Header lines, blank lines, and valid-record
     * log lines are excluded because they do not contain these literal code strings.
     * Synthetic test data (card numbers, transaction IDs, merchant IDs) is designed to
     * avoid accidental matches.
     *
     * @param rejectPath path to the posting reject CSV (may not exist yet)
     * @return mutable list of matching lines; empty list if the file is absent
     */
    private static List<String> rejectDataRows(Path rejectPath) throws Exception {
        if (!Files.exists(rejectPath)) return List.of();
        return Files.lines(rejectPath)
            .filter(l -> l.contains("100") || l.contains("101")
                      || l.contains("102") || l.contains("103"))
            .collect(Collectors.toList());
    }

    /**
     * Counts reject data rows; returns 0 if the file is absent.
     */
    private static long countRejectRows(Path rejectPath) throws Exception {
        return rejectDataRows(rejectPath).size();
    }

    /**
     * Creates an {@link AccountEntity} suitable for "valid" posting scenarios.
     *
     * <p>All field values are semantically neutral placeholders (no secrets or credentials):
     * <ul>
     *   <li>creditLimit $5000 &gt;&gt; default transaction amount $100 → overlimit check passes</li>
     *   <li>expirationDate "2030-01-01" &gt; synthetic originTs "2024-01-01" → expiry check passes</li>
     *   <li>activeStatus 'Y' for clarity (RULE-059 case uses 'N' explicitly)</li>
     * </ul>
     *
     * @param accountId the account ID to assign
     * @return a new, unsaved {@link AccountEntity}
     */
    private static AccountEntity validAccount(long accountId) {
        return new AccountEntity(
            accountId,
            "Y",                          // ACCT-ACTIVE-STATUS
            new BigDecimal("500.00"),     // ACCT-CURR-BAL
            new BigDecimal("5000.00"),    // ACCT-CREDIT-LIMIT — ample; $5000 >> $100 default txn
            new BigDecimal("2500.00"),    // ACCT-CASH-CREDIT-LIMIT
            "2020-01-01",                 // ACCT-OPEN-DATE
            "2030-01-01",                 // ACCT-EXPIRAION-DATE — safely after synthetic "2024-01-01"
            "2025-01-01",                 // ACCT-REISSUE-DATE
            BigDecimal.ZERO,              // ACCT-CURR-CYC-CREDIT
            BigDecimal.ZERO,              // ACCT-CURR-CYC-DEBIT
            "00000",                      // ACCT-ADDR-ZIP (placeholder shape X(5))
            "TESTGRP"                     // ACCT-GROUP-ID (placeholder shape X(10))
        );
    }
}
