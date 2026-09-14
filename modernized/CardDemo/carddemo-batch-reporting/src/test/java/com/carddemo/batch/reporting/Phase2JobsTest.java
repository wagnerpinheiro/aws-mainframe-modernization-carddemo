package com.carddemo.batch.reporting;

import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.CardEntity;
import com.carddemo.domain.entity.CardXRefEntity;
import com.carddemo.domain.entity.CustomerEntity;
import com.carddemo.domain.entity.TransactionEntity;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardRepository;
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
 * Characterization tests for Phase 2 remaining programs:
 * CBACT01C, CBACT02C, CBACT03C, CBCUS01C, CBTRN03C, CBEXPORT, CBIMPORT.
 */
@SpringBatchTest
@SpringBootTest(properties = "spring.main.banner-mode=off")
@ActiveProfiles("test")
class Phase2JobsTest {

    @Autowired private JobLauncherTestUtils   utils;
    @Autowired private JobRepositoryTestUtils  repoUtils;

    @Autowired @Qualifier("accountListReportJob") private Job accountListReportJob;
    @Autowired @Qualifier("cardReportJob")         private Job cardReportJob;
    @Autowired @Qualifier("crossRefReportJob")     private Job crossRefReportJob;
    @Autowired @Qualifier("customerReportJob")     private Job customerReportJob;
    @Autowired @Qualifier("transactionReportJob")  private Job transactionReportJob;
    @Autowired @Qualifier("dataExportJob")         private Job dataExportJob;
    @Autowired @Qualifier("dataImportJob")         private Job dataImportJob;

    @Autowired private AccountRepository     accountRepository;
    @Autowired private CustomerRepository    customerRepository;
    @Autowired private CardXRefRepository    cardXRefRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private CardRepository        cardRepository;

    @Value("${carddemo.reporting.account-report-output}")     private String acctReportPath;
    @Value("${carddemo.reporting.card-report-output}")         private String cardReportPath;
    @Value("${carddemo.reporting.xref-report-output}")         private String xrefReportPath;
    @Value("${carddemo.reporting.customer-report-output}")     private String custReportPath;
    @Value("${carddemo.reporting.transaction-report-output}")  private String tranReportPath;
    @Value("${carddemo.reporting.export-output}")              private String exportPath;
    @Value("${carddemo.reporting.import-error-output}")        private String importErrorPath;

    private JobExecution lastExecution;

    @BeforeEach
    void setUp() {
        repoUtils.removeJobExecutions();
        transactionRepository.deleteAll();
        cardRepository.deleteAll();
        cardXRefRepository.deleteAll();
        customerRepository.deleteAll();
        accountRepository.deleteAll();
        lastExecution = null;
    }

    // =========================================================================
    // CBACT01C → accountListReportJob
    // =========================================================================

    @Test
    void accountReport_writesAllAccounts() throws Exception {
        seedAccount(1001L, "Y", "1500.00", "5000.00");
        seedAccount(1002L, "N", "250.00", "1000.00");
        utils.setJob(accountListReportJob);

        run(); JobExecution exec = lastExecution;
        assertEquals(BatchStatus.COMPLETED, exec.getStatus());

        List<String> lines = Files.readAllLines(Path.of(acctReportPath));
        assertThat(lines).anyMatch(l -> l.contains("1001"));
        assertThat(lines).anyMatch(l -> l.contains("1002"));
        assertThat(lines).anyMatch(l -> l.contains("TOTAL ACCOUNTS: 2"));
    }

    @Test
    void accountReport_emptyDatabase_writesZeroTotal() throws Exception {
        utils.setJob(accountListReportJob);
        run(); JobExecution exec = lastExecution;
        assertEquals(BatchStatus.COMPLETED, exec.getStatus());
        List<String> lines = Files.readAllLines(Path.of(acctReportPath));
        assertThat(lines).anyMatch(l -> l.contains("TOTAL ACCOUNTS: 0"));
    }

    // =========================================================================
    // CBACT02C → cardReportJob
    // =========================================================================

    @Test
    void cardReport_writesCardsWithoutCvv() throws Exception {
        cardRepository.save(new CardEntity("1001000100010001", 1001L, "ALICE SMITH", "2030-01-01", "Y"));
        cardRepository.save(new CardEntity("2002000200020002", 1002L, "BOB JONES",  "2028-06-30", "Y"));
        utils.setJob(cardReportJob);

        run(); JobExecution exec = lastExecution;
        assertEquals(BatchStatus.COMPLETED, exec.getStatus());

        List<String> lines = Files.readAllLines(Path.of(cardReportPath));
        assertThat(lines).anyMatch(l -> l.contains("1001000100010001"));
        assertThat(lines).anyMatch(l -> l.contains("ALICE SMITH"));
        assertThat(lines).noneMatch(l -> l.toUpperCase().contains("CVV"));
        assertThat(lines).anyMatch(l -> l.contains("TOTAL CARDS: 2"));
    }

    // =========================================================================
    // CBACT03C → crossRefReportJob
    // =========================================================================

    @Test
    void crossRefReport_writesXrefRecords() throws Exception {
        cardXRefRepository.save(new CardXRefEntity("1001000100010001", 9001L, 1001L));
        cardXRefRepository.save(new CardXRefEntity("2002000200020002", 9002L, 1002L));
        utils.setJob(crossRefReportJob);

        run(); JobExecution exec = lastExecution;
        assertEquals(BatchStatus.COMPLETED, exec.getStatus());

        List<String> lines = Files.readAllLines(Path.of(xrefReportPath));
        assertThat(lines).anyMatch(l -> l.contains("1001000100010001") && l.contains("9001") && l.contains("1001"));
        assertThat(lines).anyMatch(l -> l.contains("TOTAL XREF RECORDS: 2"));
    }

    // =========================================================================
    // CBCUS01C → customerReportJob
    // =========================================================================

    @Test
    void customerReport_writesCustomers() throws Exception {
        seedCustomer(5001L, "Alice", "M", "Smith", 750);
        seedCustomer(5002L, "Bob", "", "Jones", 680);
        utils.setJob(customerReportJob);

        run(); JobExecution exec = lastExecution;
        assertEquals(BatchStatus.COMPLETED, exec.getStatus());

        List<String> lines = Files.readAllLines(Path.of(custReportPath));
        assertThat(lines).anyMatch(l -> l.contains("5001") && l.contains("Alice"));
        assertThat(lines).anyMatch(l -> l.contains("5002") && l.contains("Bob"));
        assertThat(lines).anyMatch(l -> l.contains("TOTAL CUSTOMERS: 2"));
    }

    // =========================================================================
    // CBTRN03C → transactionReportJob (RULE-021/022/023)
    // =========================================================================

    @Test
    void transactionReport_dateRangeFilter_rule023() throws Exception {
        cardXRefRepository.save(new CardXRefEntity("1001000100010001", 9001L, 1001L));
        // In-range transaction
        transactionRepository.save(txn("TXN001", "1001000100010001", "50.00", "2024-06-15 10:00:00.000000"));
        // Out-of-range transaction
        transactionRepository.save(txn("TXN002", "1001000100010001", "100.00", "2023-01-01 10:00:00.000000"));
        utils.setJob(transactionReportJob);

        JobParameters params = new JobParametersBuilder()
                .addString("startDate", "2024-01-01")
                .addString("endDate", "2024-12-31")
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        lastExecution = utils.launchJob(params); JobExecution exec = lastExecution;
        assertEquals(BatchStatus.COMPLETED, exec.getStatus());

        List<String> lines = Files.readAllLines(Path.of(tranReportPath));
        assertThat(lines).anyMatch(l -> l.contains("TXN001"));
        assertThat(lines).noneMatch(l -> l.contains("TXN002")); // filtered out by date
        // RULE-021: grand total line present
        assertThat(lines).anyMatch(l -> l.contains("GRAND TOTAL"));
    }

    @Test
    void transactionReport_threeLevel_totals_rule021() throws Exception {
        cardXRefRepository.save(new CardXRefEntity("1001000100010001", 9001L, 1001L));
        transactionRepository.save(txn("TXN010", "1001000100010001", "25.00", "2024-03-10 10:00:00.000000"));
        transactionRepository.save(txn("TXN011", "1001000100010001", "75.00", "2024-03-15 10:00:00.000000"));
        utils.setJob(transactionReportJob);

        run(); JobExecution exec = lastExecution;
        assertEquals(BatchStatus.COMPLETED, exec.getStatus());

        List<String> lines = Files.readAllLines(Path.of(tranReportPath));
        assertThat(lines).anyMatch(l -> l.contains("ACCOUNT TOTAL"));
        assertThat(lines).anyMatch(l -> l.contains("GRAND TOTAL"));
    }

    // =========================================================================
    // CBEXPORT → dataExportJob
    // =========================================================================

    @Test
    void dataExport_producesMultiRecordFile() throws Exception {
        seedCustomer(8001L, "Carol", "A", "Brown", 720);
        seedAccount(8001L, "Y", "3000.00", "10000.00");
        cardXRefRepository.save(new CardXRefEntity("8001000800010001", 8001L, 8001L));
        transactionRepository.save(txn("TXN_EXP1", "8001000800010001", "150.00", "2024-05-01 09:00:00.000000"));
        cardRepository.save(new CardEntity("8001000800010001", 8001L, "CAROL BROWN", "2028-12-31", "Y"));
        utils.setJob(dataExportJob);

        run(); JobExecution exec = lastExecution;
        assertEquals(BatchStatus.COMPLETED, exec.getStatus());

        List<String> lines = Files.readAllLines(Path.of(exportPath));
        assertThat(lines).anyMatch(l -> l.startsWith("C|") && l.contains("8001"));   // customer
        assertThat(lines).anyMatch(l -> l.startsWith("A|") && l.contains("8001"));   // account
        assertThat(lines).anyMatch(l -> l.startsWith("X|") && l.contains("8001"));   // xref
        assertThat(lines).anyMatch(l -> l.startsWith("T|") && l.contains("TXN_EXP1")); // transaction
        assertThat(lines).anyMatch(l -> l.startsWith("D|") && l.contains("CAROL BROWN")); // card
        // SEC-005: no CVV in export
        assertThat(lines).noneMatch(l -> l.toUpperCase().contains("CVV"));
    }

    // =========================================================================
    // CBIMPORT → dataImportJob (SEC-014 Bean Validation)
    // =========================================================================

    @Test
    void dataImport_roundTrip_exportThenImport() throws Exception {
        // Seed data and export
        seedCustomer(9001L, "Dave", "", "Evans", 650);
        seedAccount(9001L, "Y", "500.00", "2000.00");
        cardXRefRepository.save(new CardXRefEntity("9001000900010001", 9001L, 9001L));
        cardRepository.save(new CardEntity("9001000900010001", 9001L, "DAVE EVANS", "2029-06-30", "Y"));

        utils.setJob(dataExportJob);
        run();
        assertEquals(BatchStatus.COMPLETED, lastExecution.getStatus());

        // Clear DB and import
        repoUtils.removeJobExecutions();
        cardRepository.deleteAll();
        cardXRefRepository.deleteAll();
        accountRepository.deleteAll();
        customerRepository.deleteAll();

        utils.setJob(dataImportJob);
        run();
        assertEquals(BatchStatus.COMPLETED, lastExecution.getStatus());

        // Data re-imported
        assertThat(accountRepository.count()).isGreaterThanOrEqualTo(1);
        assertThat(cardRepository.count()).isGreaterThanOrEqualTo(1);
        assertThat(cardXRefRepository.count()).isGreaterThanOrEqualTo(1);
        assertThat(customerRepository.count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void dataImport_invalidCreditLimit_rejectsRecord() throws Exception {
        // Write an export file manually with negative credit limit (SEC-014)
        // Format: A|acctId|status|currentBalance|creditLimit|cashCreditLimit|...
        String badExport = "A|9999|Y|500.00|-500.00|5000.00|2020-01-01|2030-01-01|2025-01-01|0.00|0.00|00000|GRP\n";
        Files.writeString(Path.of(exportPath), badExport);

        utils.setJob(dataImportJob);
        run(); JobExecution exec = lastExecution;
        assertEquals(BatchStatus.COMPLETED, exec.getStatus());

        // Invalid account was NOT imported
        assertThat(accountRepository.findById(9999L)).isEmpty();

        // Error was logged
        List<String> errors = Files.readAllLines(Path.of(importErrorPath));
        assertThat(errors).anyMatch(l -> l.contains("Credit limit negative") || l.contains("9999"));
    }

    @Test
    void dataImport_invalidDate_rejectsRecord() throws Exception {
        // Write export file with invalid expiration date (SEC-014)
        String badExport = "A|8888|Y|500.00|10000.00|5000.00|2020-01-01|BADDATE|2025-01-01|0.00|0.00|00000|GRP\n";
        Files.writeString(Path.of(exportPath), badExport);

        utils.setJob(dataImportJob);
        run(); JobExecution exec = lastExecution;
        assertEquals(BatchStatus.COMPLETED, exec.getStatus());

        assertThat(accountRepository.findById(8888L)).isEmpty();
        List<String> errors = Files.readAllLines(Path.of(importErrorPath));
        assertThat(errors).anyMatch(l -> l.contains("Invalid") && l.contains("date"));
    }

    @Test
    void dataImport_invalidFicoScore_rejectsCustomer() throws Exception {
        // FICO > 999 (3-digit field, SEC-014)
        String badExport = "C|7777|Alice||Smith|123 Main|Apt 1|City|WA|USA|98101|555-0001|555-0002|1990-01-01|EFT001|Y|1500\n";
        Files.writeString(Path.of(exportPath), badExport);

        utils.setJob(dataImportJob);
        run(); JobExecution exec = lastExecution;
        assertEquals(BatchStatus.COMPLETED, exec.getStatus());

        assertThat(customerRepository.findById(7777L)).isEmpty();
        List<String> errors = Files.readAllLines(Path.of(importErrorPath));
        assertThat(errors).anyMatch(l -> l.contains("FICO"));
    }

    @Test
    void dataImport_unknownRecordType_writesErrorLog() throws Exception {
        String badExport = "Z|unknown|data|here\n";
        Files.writeString(Path.of(exportPath), badExport);

        utils.setJob(dataImportJob);
        run(); JobExecution exec = lastExecution;
        assertEquals(BatchStatus.COMPLETED, exec.getStatus());

        List<String> errors = Files.readAllLines(Path.of(importErrorPath));
        assertThat(errors).anyMatch(l -> l.contains("UNKNOWN") || l.contains("Unknown"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Return type is void to prevent JobScopeTestExecutionListener from scanning
     * this method (it scans for methods returning JobExecution and invokes them
     * before @BeforeEach, when utils.job is null). Result stored in lastExecution.
     */
    private void run() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters();
        lastExecution = utils.launchJob(params);
    }

    private void seedAccount(long id, String status, String balance, String limit) {
        accountRepository.save(new AccountEntity(id, status,
                new BigDecimal(balance), new BigDecimal(limit), new BigDecimal(limit),
                "2020-01-01", "2030-01-01", "2025-01-01",
                BigDecimal.ZERO, BigDecimal.ZERO, "98101", "GRP"));
    }

    private void seedCustomer(long id, String first, String middle, String last, int fico) {
        customerRepository.save(new CustomerEntity(id, first, middle, last,
                "123 Main St", "Apt 1", "City", "WA", "USA", "98101",
                "555-0001", "555-0002", 0L, "GOV001", "1990-01-01", "EFT001", "Y", fico));
    }

    private TransactionEntity txn(String id, String card, String amt, String procTs) {
        return new TransactionEntity(id, "01", 1, "POS", "Test transaction",
                new BigDecimal(amt), 800000001L, "Merchant", "City", "98101",
                card, procTs, procTs);
    }
}
