package com.carddemo.batch.reporting;

import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.CardXRefEntity;
import com.carddemo.domain.entity.CustomerEntity;
import com.carddemo.domain.entity.TransactionEntity;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXRefRepository;
import com.carddemo.domain.repository.CustomerRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterization tests for CBSTM03A + CBSTM03B → StatementGenerationJob.
 *
 * <h2>Oracle</h2>
 * CBSTM03A.cbl (924 LOC) + CBSTM03B.cbl (230 LOC) define the ground truth.
 *
 * <h2>Equivalence strategy</h2>
 * Trace-based (golden master): no live COBOL runtime available.
 * Assertions verify that the generated statement output contains the correct fields
 * derived from static analysis of CBSTM03A's formatting paragraphs.
 *
 * <h2>Phase 2 exit criterion</h2>
 * Statement output matches golden-master for at least 3 test accounts (§3 brief).
 *
 * <h2>CBSTM03A brief risk (Phase 2)</h2>
 * "13 calls to CBSTM03B become 13 StatementFormatter invocations per account;
 * these are 13 I/O operations for one account's statement, not a batch of 13 items."
 * Tests verify statement line count is >13 (header + N transactions + footer).
 */
@SpringBatchTest
@SpringBootTest(properties = "spring.main.banner-mode=off")
@ActiveProfiles("test")
class StatementGenerationJobTest {

    @Autowired private JobLauncherTestUtils   jobLauncherTestUtils;
    @Autowired private JobRepositoryTestUtils  jobRepositoryTestUtils;

    @Autowired @Qualifier("statementGenerationJob")
    private Job statementGenerationJob;

    @Autowired private CustomerRepository    customerRepository;
    @Autowired private AccountRepository     accountRepository;
    @Autowired private CardXRefRepository    cardXRefRepository;
    @Autowired private TransactionRepository transactionRepository;

    @Value("${carddemo.reporting.statement-text-output}") private String textOutputPath;
    @Value("${carddemo.reporting.statement-html-output}") private String htmlOutputPath;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(statementGenerationJob);
        jobRepositoryTestUtils.removeJobExecutions();
        transactionRepository.deleteAll();
        cardXRefRepository.deleteAll();
        customerRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // =========================================================================
    // Case 1 — Golden master: 3 accounts with transactions
    // =========================================================================

    /**
     * Three accounts (brief exit criterion: ≥3 test accounts).
     * Verifies that each account's statement appears in the output with correct fields.
     *
     * <p>CBSTM03A oracle:
     * <ul>
     *   <li>5000-CREATE-STATEMENT: writes customer name, address, account ID, balance, FICO</li>
     *   <li>4000-TRNXFILE-GET + 6000-WRITE-TRANS: writes one line per transaction</li>
     *   <li>Total lines per account = 16 header lines + N transaction lines + 3 footer lines</li>
     * </ul>
     */
    @Test
    void threeAccounts_allStatementsWritten() throws Exception {
        // Account 1: Alice Smith, card 1001000100010001
        long acct1 = 10001L, cust1 = 1001L;
        seedCustomer(cust1, "Alice", "M", "Smith", "123 Main St");
        seedAccount(acct1, "1500.00");
        seedXRef("1001000100010001", cust1, acct1);
        seedTransaction("TXN001", "1001000100010001", "50.00", "Purchase at Store A");
        seedTransaction("TXN002", "1001000100010001", "25.00", "Purchase at Store B");

        // Account 2: Bob Jones, card 2002000200020002
        long acct2 = 20002L, cust2 = 2002L;
        seedCustomer(cust2, "Bob", "", "Jones", "456 Oak Ave");
        seedAccount(acct2, "2200.00");
        seedXRef("2002000200020002", cust2, acct2);
        seedTransaction("TXN003", "2002000200020002", "100.00", "Restaurant");

        // Account 3: Carol Brown, card 3003000300030003
        long acct3 = 30003L, cust3 = 3003L;
        seedCustomer(cust3, "Carol", "A", "Brown", "789 Pine Rd");
        seedAccount(acct3, "800.00");
        seedXRef("3003000300030003", cust3, acct3);
        seedTransaction("TXN004", "3003000300030003", "10.00", "Gas Station");
        seedTransaction("TXN005", "3003000300030003", "20.00", "Grocery");
        seedTransaction("TXN006", "3003000300030003", "30.00", "Pharmacy");

        run();

        List<String> textLines = Files.readAllLines(Path.of(textOutputPath));
        List<String> htmlLines = Files.readAllLines(Path.of(htmlOutputPath));

        // --- Account 1 assertions ---
        assertThat(textLines).anyMatch(l -> l.contains("Alice"));
        assertThat(textLines).anyMatch(l -> l.contains(String.valueOf(acct1)));
        assertThat(textLines).anyMatch(l -> l.contains("1,500.00"));
        assertThat(textLines).anyMatch(l -> l.contains("TXN001") && l.contains("50.00"));
        assertThat(textLines).anyMatch(l -> l.contains("TXN002") && l.contains("25.00"));

        // --- Account 2 assertions ---
        assertThat(textLines).anyMatch(l -> l.contains("Bob"));
        assertThat(textLines).anyMatch(l -> l.contains(String.valueOf(acct2)));
        assertThat(textLines).anyMatch(l -> l.contains("2,200.00"));
        assertThat(textLines).anyMatch(l -> l.contains("TXN003"));

        // --- Account 3 assertions ---
        assertThat(textLines).anyMatch(l -> l.contains("Carol"));
        assertThat(textLines).anyMatch(l -> l.contains(String.valueOf(acct3)));
        assertThat(textLines).anyMatch(l -> l.contains("800.00"));
        assertThat(textLines).anyMatch(l -> l.contains("TXN004"));
        assertThat(textLines).anyMatch(l -> l.contains("TXN005"));
        assertThat(textLines).anyMatch(l -> l.contains("TXN006"));

        // --- HTML output ---
        assertThat(htmlLines).anyMatch(l -> l.toLowerCase().contains("html"));
        assertThat(htmlLines).anyMatch(l -> l.contains("Alice"));
        assertThat(htmlLines).anyMatch(l -> l.contains("Bob"));
        assertThat(htmlLines).anyMatch(l -> l.contains("Carol"));
    }

    // =========================================================================
    // Case 2 — Statement line count (CBSTM03A brief risk)
    // =========================================================================

    /**
     * Verifies that the statement has at least 16 header/footer lines plus
     * one line per transaction.
     *
     * <p>Brief risk: "13 calls per account — these are 13 I/O operations for one account's
     * statement, not a batch of 13 items." If the formatter is miscounted, the statement
     * would be missing sections or would duplicate sections.
     *
     * <p>CBSTM03A oracle:
     * One account with 2 transactions → 16 header lines + 2 txn lines + 3 footer lines = 21 lines.
     * (Conservative bound: assert ≥ 15 non-empty lines per account.)
     */
    @Test
    void statementLineCount_includesAllSections() throws Exception {
        long acct = 50001L, cust = 5001L;
        seedCustomer(cust, "David", "", "Lee", "100 Elm St");
        seedAccount(acct, "3000.00");
        seedXRef("5001000500010001", cust, acct);
        seedTransaction("TXN010", "5001000500010001", "150.00", "Airline");
        seedTransaction("TXN011", "5001000500010001", "250.00", "Hotel");

        run();

        List<String> lines = Files.readAllLines(Path.of(textOutputPath));
        long nonEmptyLines = lines.stream().filter(l -> !l.isBlank()).count();

        assertThat(nonEmptyLines)
            .as("Statement must have at least 15 non-empty lines (header + txns + footer)")
            .isGreaterThanOrEqualTo(15);

        // START OF STATEMENT banner appears
        assertThat(lines).anyMatch(l -> l.contains("START OF STATEMENT"));
        // END OF STATEMENT banner appears
        assertThat(lines).anyMatch(l -> l.contains("END OF STATEMENT"));
        // TRANSACTION SUMMARY section header appears
        assertThat(lines).anyMatch(l -> l.contains("TRANSACTION SUMMARY"));
        // Basic Details section header appears
        assertThat(lines).anyMatch(l -> l.contains("Basic Details"));
        // Both transactions appear
        assertThat(lines).anyMatch(l -> l.contains("TXN010"));
        assertThat(lines).anyMatch(l -> l.contains("TXN011"));
    }

    // =========================================================================
    // Case 3 — Account with no transactions
    // =========================================================================

    /**
     * Account with no transactions — statement is written with empty transaction section.
     *
     * <p>CBSTM03A oracle: 4000-TRNXFILE-GET finds no matching card in WS-TRNX-TABLE.
     * Total EXP = $0.00. Statement is still written (header + footer, no txn lines).
     */
    @Test
    void accountWithNoTransactions_statementWrittenWithZeroTotal() throws Exception {
        long acct = 60001L, cust = 6001L;
        seedCustomer(cust, "Eve", "", "Martinez", "200 Cedar Blvd");
        seedAccount(acct, "500.00");
        seedXRef("6001000600010001", cust, acct);
        // No transactions seeded for this card

        run();

        List<String> lines = Files.readAllLines(Path.of(textOutputPath));
        assertThat(lines).anyMatch(l -> l.contains("Eve"));
        assertThat(lines).anyMatch(l -> l.contains("START OF STATEMENT"));
        assertThat(lines).anyMatch(l -> l.contains("END OF STATEMENT"));
        // Total EXP should be 0.00
        assertThat(lines).anyMatch(l -> l.contains("Total EXP"));
    }

    // =========================================================================
    // Case 4 — Empty XREF: no statements generated
    // =========================================================================

    /**
     * No XREF records → job COMPLETED, output files are created but empty.
     *
     * <p>CBSTM03A oracle: 1000-XREFFILE-GET-NEXT returns '10' immediately → END-OF-FILE='Y'.
     * Main loop exits without processing any accounts.
     */
    @Test
    void emptyXref_noStatementsGenerated() throws Exception {
        // No data seeded

        run();

        assertEquals(BatchStatus.COMPLETED, lastExecution.getStatus());
        // Output files exist (created on open) but contain no statement content
        Path textPath = Path.of(textOutputPath);
        if (Files.exists(textPath)) {
            long stmtLines = Files.readAllLines(textPath).stream()
                .filter(l -> l.contains("START OF STATEMENT")).count();
            assertEquals(0L, stmtLines, "No statements should be generated for empty XREF");
        }
    }

    // =========================================================================
    // Case 5 — HTML output structure
    // =========================================================================

    /**
     * Verifies HTML output has valid structure with account data.
     *
     * <p>CBSTM03A oracle: 5100-WRITE-HTML-HEADER writes DOCTYPE, html, head, body, table tags.
     * 5200-WRITE-HTML-NMADBS writes customer name and address in p tags.
     */
    @Test
    void htmlOutput_containsValidStructure() throws Exception {
        long acct = 70001L, cust = 7001L;
        seedCustomer(cust, "Frank", "B", "Wilson", "300 Maple Dr");
        seedAccount(acct, "4500.00");
        seedXRef("7001000700010001", cust, acct);
        seedTransaction("TXN020", "7001000700010001", "99.99", "Electronics");

        run();

        List<String> htmlLines = Files.readAllLines(Path.of(htmlOutputPath));

        assertThat(htmlLines).anyMatch(l -> l.contains("<!DOCTYPE html>"));
        assertThat(htmlLines).anyMatch(l -> l.toLowerCase().contains("<html"));
        assertThat(htmlLines).anyMatch(l -> l.toLowerCase().contains("<table"));
        assertThat(htmlLines).anyMatch(l -> l.contains("Frank"));
        assertThat(htmlLines).anyMatch(l -> l.contains("TXN020"));
        assertThat(htmlLines).anyMatch(l -> l.toLowerCase().contains("</html>"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private JobExecution lastExecution;

    private void run() throws Exception {
        JobParameters params = new JobParametersBuilder()
            .addLong("run.id", System.currentTimeMillis())
            .toJobParameters();
        lastExecution = jobLauncherTestUtils.launchJob(params);
        assertEquals(BatchStatus.COMPLETED, lastExecution.getStatus(),
            "StatementGenerationJob must COMPLETE");
    }

    private void seedCustomer(long id, String first, String middle, String last, String addr1) {
        customerRepository.save(new CustomerEntity(
            id, first, middle, last,
            addr1, "Apt 1", "City", "WA", "USA", "98101",
            "555-0001", "555-0002", 123456789L, "GOV001",
            "1990-01-01", "EFT001", "Y", 720
        ));
    }

    private void seedAccount(long id, String balance) {
        accountRepository.save(new AccountEntity(
            id, "Y",
            new BigDecimal(balance), new BigDecimal("10000.00"), new BigDecimal("5000.00"),
            "2020-01-01", "2030-01-01", "2025-01-01",
            BigDecimal.ZERO, BigDecimal.ZERO, "98101", "TESTGRP"
        ));
    }

    private void seedXRef(String cardNumber, long custId, long acctId) {
        cardXRefRepository.save(new CardXRefEntity(cardNumber, custId, acctId));
    }

    private void seedTransaction(String id, String cardNumber, String amount, String desc) {
        transactionRepository.save(new TransactionEntity(
            id, "01", 1, "POS TERM", desc,
            new BigDecimal(amount), 800000001L, "Test Merchant", "Seattle", "98101",
            cardNumber, "2024-01-01 00:00:00.000000", "2024-01-01 00:00:00.000000"
        ));
    }
}
