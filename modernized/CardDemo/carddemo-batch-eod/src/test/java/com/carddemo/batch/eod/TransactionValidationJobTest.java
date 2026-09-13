package com.carddemo.batch.eod;

import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.CardXRefEntity;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXRefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
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

/**
 * Characterization tests for CBTRN01C → TransactionValidationJob.
 *
 * <h2>Oracle</h2>
 * The legacy COBOL program CBTRN01C.cbl (494 LOC) defines the ground truth.
 * Every assertion here records what the COBOL program <em>actually does</em>, not
 * what a specification says it should do.  The three COBOL branches covered are:
 * <ol>
 *   <li>XREF INVALID KEY → WS-XREF-READ-STATUS=4 → {@link ValidationStatus#CARD_NOT_FOUND}</li>
 *   <li>ACCOUNT INVALID KEY → WS-ACCT-READ-STATUS=4 → {@link ValidationStatus#ACCOUNT_NOT_FOUND}</li>
 *   <li>Both found → {@link ValidationStatus#VALID}</li>
 * </ol>
 *
 * <h2>Current status</h2>
 * Tests 1, 2, 3, and 5 currently <strong>FAIL</strong> because
 * {@link TransactionValidationProcessor} and {@link TransactionValidationWriter} are
 * stubs that throw {@link UnsupportedOperationException}.  Test 4 (empty file) passes
 * immediately because the processor and writer are never invoked on a 0-record input.
 * All five tests will go GREEN when the stubs are implemented.
 *
 * <h2>Input override mechanism</h2>
 * The production {@link TransactionValidationJobConfig} binds the reader to a classpath
 * resource via {@code @Value("${carddemo.batch.dalytran-input}")}.  Tests that need a
 * custom DALYTRAN file (cases 2–5) set {@link #TEST_INPUT} before launching the job;
 * the inner {@link OverridableReaderConfig} provides a {@code @StepScope @Primary}
 * override that reads from that static reference at step-start time.
 * Bean-definition overriding is enabled via
 * {@code spring.main.allow-bean-definition-overriding=true}.
 */
@SpringBatchTest
@SpringBootTest(properties = {
    "spring.main.allow-bean-definition-overriding=true",
    "spring.main.banner-mode=off"
})
@ActiveProfiles("test")
class TransactionValidationJobTest {

    // -------------------------------------------------------------------------
    // Overridable reader — tests set this field before calling launchJob().
    // AtomicReference ensures safe publication between @BeforeEach and the step.
    // -------------------------------------------------------------------------

    /**
     * Mutable input resource shared across tests (sequential execution — no lock needed).
     * Reset to the full fixture file in {@link #setUp()} before each test.
     */
    static final AtomicReference<Resource> TEST_INPUT =
        new AtomicReference<>(new ClassPathResource("fixtures/dailytran.txt"));

    /**
     * Inner {@link TestConfiguration} that overrides the production
     * {@code dailyTranReader} bean with a {@code @StepScope} variant.
     * Because it is {@code @StepScope}, a fresh instance is created per step
     * execution, reading the {@link #TEST_INPUT} reference at that point.
     *
     * <p>The bean name {@code "dailyTranReader"} (from the method name) shadows
     * the production bean of the same name; overriding is enabled via the
     * {@code spring.main.allow-bean-definition-overriding=true} property above.
     *
     * <p>Replicating the tokenizer configuration here is intentional: the test is
     * the oracle for what the reader <em>must</em> produce; if the production column
     * ranges drift, this test catches the regression.
     */
    @TestConfiguration
    static class OverridableReaderConfig {

        @Bean("dailyTranReader")
        @StepScope
        @Primary
        FlatFileItemReader<DailyTransactionRecord> dailyTranReader() {
            var tokenizer = new FixedLengthTokenizer();
            tokenizer.setNames(
                "transactionId", "typeCode", "categoryCode", "source", "description",
                "amount", "merchantId", "merchantName", "merchantCity", "merchantZip",
                "cardNumber", "originTs", "processTs"
            );
            // Column ranges must match TransactionValidationJobConfig exactly (CVTRA06Y).
            tokenizer.setColumns(
                new Range(1,   16),  // DALYTRAN-ID
                new Range(17,  18),  // DALYTRAN-TYPE-CD
                new Range(19,  22),  // DALYTRAN-CAT-CD
                new Range(23,  32),  // DALYTRAN-SOURCE
                new Range(33,  132), // DALYTRAN-DESC
                new Range(133, 143), // DALYTRAN-AMT  S9(9)V99 sign-overpunch
                new Range(144, 152), // DALYTRAN-MERCHANT-ID
                new Range(153, 202), // DALYTRAN-MERCHANT-NAME
                new Range(203, 252), // DALYTRAN-MERCHANT-CITY
                new Range(253, 262), // DALYTRAN-MERCHANT-ZIP
                new Range(263, 278), // DALYTRAN-CARD-NUM  ← XREF lookup key
                new Range(279, 304), // DALYTRAN-ORIG-TS
                new Range(305, 330)  // DALYTRAN-PROC-TS
            );
            tokenizer.setStrict(false);

            return new FlatFileItemReaderBuilder<DailyTransactionRecord>()
                .name("dailyTranReader")
                .resource(TEST_INPUT.get())       // resolved at step-start time
                .lineTokenizer(tokenizer)
                .fieldSetMapper(fs -> new DailyTransactionRecord(
                    fs.readString("transactionId").trim(),
                    fs.readString("typeCode").trim(),
                    Integer.parseInt(fs.readString("categoryCode").trim()),
                    fs.readString("source").trim(),
                    fs.readString("description").trim(),
                    com.carddemo.common.CobolDisplayParser.parseSignedAmount(
                        fs.readString("amount"), 2),
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

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CardXRefRepository cardXRefRepository;

    /**
     * Reject CSV path from {@code src/test/resources/application-test.yml}:
     * {@code ${java.io.tmpdir}/carddemo-test-rejects.csv}
     */
    @Value("${carddemo.batch.reject-output}")
    private String rejectOutputPath;

    @BeforeEach
    void setUp() throws Exception {
        // Clean batch metadata between tests so the same job can be re-run.
        jobRepositoryTestUtils.removeJobExecutions();

        // Clear domain data left by the previous test.
        cardXRefRepository.deleteAll();
        accountRepository.deleteAll();

        // Reset to the full fixture input; tests that need a different file
        // override TEST_INPUT before calling launchJob().
        TEST_INPUT.set(new ClassPathResource("fixtures/dailytran.txt"));

        // Delete any reject file left by the previous test.
        Files.deleteIfExists(Path.of(rejectOutputPath));
    }

    // -------------------------------------------------------------------------
    // Case 1 — Golden-master: all 300 records valid
    // -------------------------------------------------------------------------

    /**
     * Full fixture run — all 300 DALYTRAN records have matching XREF and account entries.
     *
     * <p>Derivation (BASELINE.md static analysis):
     * <ul>
     *   <li>300 records in dailytran.txt</li>
     *   <li>50 distinct card numbers, all present in cardxref.txt → 0 CARD_NOT_FOUND</li>
     *   <li>50 distinct account IDs in cardxref.txt, all in acctdata.txt → 0 ACCOUNT_NOT_FOUND</li>
     * </ul>
     *
     * <p>COBOL oracle: DISPLAY "SUCCESSFUL READ OF ACCOUNT FILE" × 300;
     * no "COULD NOT BE VERIFIED" output.
     *
     * <p><strong>Currently FAILS</strong> — processor stub throws {@link UnsupportedOperationException}.
     */
    @Test
    void fullFixtureRun_allValid() throws Exception {
        FixtureLoader.loadFullFixtures(cardXRefRepository, accountRepository);
        // TEST_INPUT already set to classpath:fixtures/dailytran.txt in setUp().

        JobExecution execution = launchJob();

        // Job-level
        assertEquals(BatchStatus.COMPLETED, execution.getStatus(),
            "Job must complete successfully when all 300 DALYTRAN records resolve to VALID");

        // Step-level
        Collection<StepExecution> steps = execution.getStepExecutions();
        assertThat(steps).hasSize(1);
        StepExecution step = steps.iterator().next();
        assertEquals(300, step.getReadCount(),
            "Reader must consume all 300 records from dailytran.txt");
        assertEquals(0, step.getSkipCount(),
            "No records should be skipped in the all-valid scenario");

        // Reject CSV
        // COBOL has no file writes; Q6 enhancement writes only failed records.
        // For a fully valid run the reject file must be absent or contain 0 data rows.
        assertEquals(0L, countRejectRows(Path.of(rejectOutputPath)),
            "Reject CSV must have 0 data rows when all records are VALID");
    }

    // -------------------------------------------------------------------------
    // Case 2 — XREF lookup miss → CARD_NOT_FOUND → reject row
    // -------------------------------------------------------------------------

    /**
     * One record with an unknown card number → CARD_NOT_FOUND → written to reject CSV.
     *
     * <p>COBOL oracle (CBTRN01C.cbl lines 181–183):
     * {@code DISPLAY 'CARD NUMBER ' DALYTRAN-CARD-NUM ' COULD NOT BE VERIFIED.'}
     * {@code WS-XREF-READ-STATUS = 4} — the ACCOUNT-FILE lookup is skipped entirely.
     *
     * <p>Synthetic input: one 350-byte DALYTRAN record whose cardNumber is
     * {@code 9999999999999999} — deliberately absent from the seeded card_xref table.
     *
     * <p><strong>Currently FAILS</strong> — processor stub throws.
     */
    @Test
    void cardNotFoundGoesToReject() throws Exception {
        // Seed one valid pair so the tables are not completely empty.
        accountRepository.save(minimalAccount(1001L));
        cardXRefRepository.save(new CardXRefEntity("1111111111111111", 9001L, 1001L));

        // 9999999999999999 (16 nines) is not seeded → triggers CARD_NOT_FOUND branch.
        final String unknownCard   = "9999999999999999";
        final String transactionId = "TXN0000000000001";
        TEST_INPUT.set(new FileSystemResource(
            SyntheticDalytranBuilder.writeToTempFile(List.of(
                SyntheticDalytranBuilder.buildRecord(transactionId, unknownCard)
            ))
        ));

        JobExecution execution = launchJob();

        // Job-level
        assertEquals(BatchStatus.COMPLETED, execution.getStatus(),
            "Job must COMPLETE on XREF miss — COBOL skips with DISPLAY, does not abend");

        // Step-level
        Collection<StepExecution> steps = execution.getStepExecutions();
        assertThat(steps).hasSize(1);
        StepExecution step = steps.iterator().next();
        assertEquals(1, step.getReadCount(), "Exactly one record was read");

        // Reject CSV
        Path rejectPath = Path.of(rejectOutputPath);
        assertThat(rejectPath).exists();
        List<String> rejectRows = rejectDataRows(rejectPath);
        assertThat(rejectRows).hasSize(1);
        String row = rejectRows.get(0);
        assertThat(row)
            .as("Reject row must contain CARD_NOT_FOUND status")
            .contains("CARD_NOT_FOUND");
        assertThat(row)
            .as("Reject row must reference the failed transaction by card or txn-id")
            .satisfiesAnyOf(
                r -> assertThat(r).contains(unknownCard),
                r -> assertThat(r).contains(transactionId)
            );
    }

    // -------------------------------------------------------------------------
    // Case 3 — XREF found but account absent → ACCOUNT_NOT_FOUND → reject row
    // -------------------------------------------------------------------------

    /**
     * XREF entry exists but the referenced account is absent → ACCOUNT_NOT_FOUND.
     *
     * <p>COBOL oracle (CBTRN01C.cbl lines 177–178):
     * {@code WS-ACCT-READ-STATUS = 4} (ACCOUNT INVALID KEY);
     * {@code DISPLAY 'ACCOUNT ' ACCT-ID ' NOT FOUND'}
     *
     * <p>Seed state: card_xref row {@code 8888888888888888 → accountId=88888} exists,
     * but the account table has no row with id=88888.  This is the precise condition
     * that triggers the ACCOUNT_NOT_FOUND branch in the COBOL READ ACCOUNT-FILE.
     *
     * <p><strong>Currently FAILS</strong> — processor stub throws.
     */
    @Test
    void accountNotFoundGoesToReject() throws Exception {
        final String cardNumber    = "8888888888888888";
        final long   missingAcctId = 88888L;
        final String transactionId = "TXN0000000000002";

        cardXRefRepository.save(new CardXRefEntity(cardNumber, 8001L, missingAcctId));
        // Deliberately NO AccountEntity with id=88888 → ACCOUNT_NOT_FOUND branch.

        TEST_INPUT.set(new FileSystemResource(
            SyntheticDalytranBuilder.writeToTempFile(List.of(
                SyntheticDalytranBuilder.buildRecord(transactionId, cardNumber)
            ))
        ));

        JobExecution execution = launchJob();

        // Job-level
        assertEquals(BatchStatus.COMPLETED, execution.getStatus(),
            "Job must COMPLETE on missing account — COBOL DISPLAYs and continues, does not abend");

        // Step-level
        Collection<StepExecution> steps = execution.getStepExecutions();
        assertThat(steps).hasSize(1);
        StepExecution step = steps.iterator().next();
        assertEquals(1, step.getReadCount(), "Exactly one record was read");

        // Reject CSV
        Path rejectPath = Path.of(rejectOutputPath);
        assertThat(rejectPath).exists();
        List<String> rejectRows = rejectDataRows(rejectPath);
        assertThat(rejectRows).hasSize(1);
        String row = rejectRows.get(0);
        assertThat(row)
            .as("Reject row must contain ACCOUNT_NOT_FOUND status")
            .contains("ACCOUNT_NOT_FOUND");
        assertThat(row)
            .as("Reject row must reference the failed transaction by card or txn-id")
            .satisfiesAnyOf(
                r -> assertThat(r).contains(cardNumber),
                r -> assertThat(r).contains(transactionId)
            );
    }

    // -------------------------------------------------------------------------
    // Case 4 — Empty DALYTRAN file → job completes with 0 reads
    // -------------------------------------------------------------------------

    /**
     * Empty DALYTRAN input (0 bytes) → job COMPLETED with readCount=0, no reject rows.
     *
     * <p>COBOL oracle: the main PERFORM UNTIL loop exits immediately when
     * DALYTRAN-STATUS='10' on the first READ (END-OF-DAILY-TRANS-FILE='Y').
     * No DISPLAY output is produced; program reaches GOBACK cleanly.
     *
     * <p>This test <strong>currently PASSES</strong> because Spring Batch never
     * invokes the stub processor or writer when the chunk is empty — the reader
     * returns null on the first call for a 0-byte file, ending the step immediately.
     *
     * <p>This test must remain GREEN before and after the implementation.
     */
    @Test
    void emptyFileCompletesCleanly() throws Exception {
        // No H2 data needed — no records will be processed.
        TEST_INPUT.set(new FileSystemResource(SyntheticDalytranBuilder.emptyTempFile()));

        JobExecution execution = launchJob();

        // Job-level
        assertEquals(BatchStatus.COMPLETED, execution.getStatus(),
            "Job must COMPLETE on empty input — mirrors COBOL clean exit with 0 records");

        // Step-level
        Collection<StepExecution> steps = execution.getStepExecutions();
        assertThat(steps).hasSize(1);
        StepExecution step = steps.iterator().next();
        assertEquals(0, step.getReadCount(),
            "readCount must be 0 for an empty DALYTRAN file");
        assertEquals(0, step.getWriteCount(),
            "writeCount must be 0 — nothing to write");

        // Reject CSV — writer is never called; file must not exist or have 0 rows.
        assertEquals(0L, countRejectRows(Path.of(rejectOutputPath)),
            "Reject CSV must have 0 rows for an empty input file");
    }

    // -------------------------------------------------------------------------
    // Case 5 — Mixed: 1 VALID + 1 CARD_NOT_FOUND + 1 ACCOUNT_NOT_FOUND
    // -------------------------------------------------------------------------

    /**
     * Three DALYTRAN records with three distinct outcomes.
     *
     * <table>
     *   <tr><th>Card number</th><th>In XREF?</th><th>Account exists?</th><th>Expected</th></tr>
     *   <tr><td>1111222233334444</td><td>Yes</td><td>Yes</td><td>VALID</td></tr>
     *   <tr><td>9999999999999999</td><td>No</td><td>N/A</td><td>CARD_NOT_FOUND</td></tr>
     *   <tr><td>7777777777777777</td><td>Yes</td><td>No</td><td>ACCOUNT_NOT_FOUND</td></tr>
     * </table>
     *
     * <p>COBOL oracle: each record is processed independently in the PERFORM UNTIL loop.
     * CARD_NOT_FOUND and ACCOUNT_NOT_FOUND produce DISPLAY warnings; the valid record
     * produces "SUCCESSFUL READ OF ACCOUNT FILE".  All three are consumed without abend.
     *
     * <p>Java Q6 enhancement: CARD_NOT_FOUND and ACCOUNT_NOT_FOUND go to the reject CSV;
     * the VALID record is logged at DEBUG only.
     *
     * <p><strong>Currently FAILS</strong> — processor stub throws.
     */
    @Test
    void mixedValidAndInvalid() throws Exception {
        // Card 1: VALID — both XREF and account present.
        final String validCard   = "1111222233334444";
        final long   validAcctId = 77777L;
        cardXRefRepository.save(new CardXRefEntity(validCard, 6001L, validAcctId));
        accountRepository.save(minimalAccount(validAcctId));

        // Card 2: CARD_NOT_FOUND — no XREF entry.
        final String cardNotFoundCard = "9999999999999999";

        // Card 3: ACCOUNT_NOT_FOUND — XREF present, account absent.
        final String acctNotFoundCard   = "7777777777777777";
        final long   acctNotFoundAcctId = 55555L;
        cardXRefRepository.save(new CardXRefEntity(acctNotFoundCard, 7001L, acctNotFoundAcctId));
        // No AccountEntity with id=55555.

        TEST_INPUT.set(new FileSystemResource(
            SyntheticDalytranBuilder.writeToTempFile(List.of(
                SyntheticDalytranBuilder.buildRecord("TXN0000000000010", validCard),
                SyntheticDalytranBuilder.buildRecord("TXN0000000000011", cardNotFoundCard),
                SyntheticDalytranBuilder.buildRecord("TXN0000000000012", acctNotFoundCard)
            ))
        ));

        JobExecution execution = launchJob();

        // Job-level
        assertEquals(BatchStatus.COMPLETED, execution.getStatus(),
            "Job must COMPLETE on mixed input — COBOL processes all records and exits normally");

        // Step-level
        Collection<StepExecution> steps = execution.getStepExecutions();
        assertThat(steps).hasSize(1);
        StepExecution step = steps.iterator().next();
        assertEquals(3, step.getReadCount(), "All 3 records must be read");

        // Reject CSV — exactly 2 rows: CARD_NOT_FOUND + ACCOUNT_NOT_FOUND.
        // The VALID record must NOT appear in the reject file.
        Path rejectPath = Path.of(rejectOutputPath);
        assertThat(rejectPath).exists();
        List<String> rejectRows = rejectDataRows(rejectPath);
        assertThat(rejectRows)
            .as("Exactly 2 reject rows: one CARD_NOT_FOUND and one ACCOUNT_NOT_FOUND")
            .hasSize(2);
        assertThat(rejectRows.stream().anyMatch(r -> r.contains("CARD_NOT_FOUND")))
            .as("One reject row must have CARD_NOT_FOUND").isTrue();
        assertThat(rejectRows.stream().anyMatch(r -> r.contains("ACCOUNT_NOT_FOUND")))
            .as("One reject row must have ACCOUNT_NOT_FOUND").isTrue();
        assertThat(rejectRows.stream().noneMatch(r -> r.contains(validCard)))
            .as("VALID record (" + validCard + ") must not appear in the reject file")
            .isTrue();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Launches the job registered in the test context with a unique {@code run.id}
     * parameter so Spring Batch treats each invocation as a distinct job instance.
     */
    private JobExecution launchJob() throws Exception {
        JobParameters params = new JobParametersBuilder()
            .addLong("run.id", System.currentTimeMillis())
            .toJobParameters();
        return jobLauncherTestUtils.launchJob(params);
    }

    /**
     * Returns the subset of lines in the reject CSV that carry a validation-failure
     * status code ({@code CARD_NOT_FOUND} or {@code ACCOUNT_NOT_FOUND}).
     *
     * <p>This approach is intentionally format-agnostic: it does not assume a specific
     * header, column order, or delimiter, leaving the writer free to choose its own
     * CSV structure.  Header lines and blank lines are excluded automatically because
     * they will not contain these status codes.
     *
     * @param rejectPath path to the reject CSV (may not yet exist)
     * @return mutable list of matching lines; empty if the file is absent
     */
    private static List<String> rejectDataRows(Path rejectPath) throws Exception {
        if (!Files.exists(rejectPath)) return List.of();
        return Files.lines(rejectPath)
            .filter(l -> l.contains("CARD_NOT_FOUND") || l.contains("ACCOUNT_NOT_FOUND"))
            .collect(Collectors.toList());
    }

    /**
     * Counts reject data rows; returns 0 if the file is absent.
     */
    private static long countRejectRows(Path rejectPath) throws Exception {
        return rejectDataRows(rejectPath).size();
    }

    /**
     * Creates a minimal {@link AccountEntity} with semantically neutral placeholder values.
     * All fields satisfy JPA constraints without encoding any secret or credential data.
     */
    private static AccountEntity minimalAccount(long accountId) {
        return new AccountEntity(
            accountId,
            "Y",                         // ACCT-ACTIVE-STATUS
            new BigDecimal("500.00"),    // ACCT-CURR-BAL
            new BigDecimal("2000.00"),   // ACCT-CREDIT-LIMIT
            new BigDecimal("1000.00"),   // ACCT-CASH-CREDIT-LIMIT
            "2020-01-01",                // ACCT-OPEN-DATE
            "2030-01-01",                // ACCT-EXPIRATION-DATE
            "2025-01-01",                // ACCT-REISSUE-DATE
            BigDecimal.ZERO,             // ACCT-CURR-CYC-CREDIT
            BigDecimal.ZERO,             // ACCT-CURR-CYC-DEBIT
            "00000",                     // ACCT-ADDR-ZIP
            "TESTGRP"                    // ACCT-GROUP-ID
        );
    }
}
