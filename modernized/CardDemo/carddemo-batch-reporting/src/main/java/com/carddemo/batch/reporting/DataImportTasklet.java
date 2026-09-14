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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports CardDemo data from the multi-record CSV export file into JPA repositories.
 * Corresponds to CBIMPORT.cbl (487 LOC).
 *
 * SEC-014 fix (Bean Validation on import):
 *   - SSN: exactly 9 digits (CUST-SSN PIC 9(9))
 *   - Credit limit: must be positive (≥ 0)
 *   - Date fields (YYYY-MM-DD): strict ISO parsing via LocalDate.parse()
 *   - FICO credit score: 0-999 (3-digit PIC 9(3))
 * Invalid records are written to the error output instead of being imported.
 *
 * Import format (pipe-delimited, matches DataExportTasklet output):
 *   C|custId|firstName|middleName|lastName|addr1|addr2|addr3|state|country|zip|phone1|phone2|dob|eftAcct|priHolder|fico
 *   A|acctId|activeStatus|currentBalance|creditLimit|cashCreditLimit|openDate|expirationDate|reissueDate|cycCredit|cycDebit|addrZip|groupId
 *   X|cardNum|custId|acctId
 *   T|tranId|typeCode|catCode|source|desc|amount|merchantId|merchantName|merchantCity|merchantZip|cardNum|origTs|procTs
 *   D|cardNum|acctId|embossedName|expirationDate|activeStatus
 */
@Component
public class DataImportTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(DataImportTasklet.class);
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final CardXRefRepository cardXRefRepository;
    private final TransactionRepository transactionRepository;
    private final CardRepository cardRepository;
    private final String inputPath;
    private final String errorOutputPath;

    // Import statistics
    private int custImported, acctImported, xrefImported, tranImported, cardImported, errorCount;

    public DataImportTasklet(CustomerRepository customerRepository,
                              AccountRepository accountRepository,
                              CardXRefRepository cardXRefRepository,
                              TransactionRepository transactionRepository,
                              CardRepository cardRepository,
                              @Value("${carddemo.reporting.import-input}") String inputPath,
                              @Value("${carddemo.reporting.import-error-output}") String errorOutputPath) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.cardXRefRepository = cardXRefRepository;
        this.transactionRepository = transactionRepository;
        this.cardRepository = cardRepository;
        this.inputPath = inputPath;
        this.errorOutputPath = errorOutputPath;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        custImported = acctImported = xrefImported = tranImported = cardImported = errorCount = 0;

        if (Path.of(errorOutputPath).getParent() != null)
            Files.createDirectories(Path.of(errorOutputPath).getParent());

        List<String> errorLines = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(inputPath, StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.isBlank()) continue;
                String[] fields = line.split("\\|", -1);
                if (fields.length == 0) continue;
                String recType = fields[0];
                try {
                    switch (recType) {
                        case "C" -> importCustomer(fields, lineNum, errorLines);
                        case "A" -> importAccount(fields, lineNum, errorLines);
                        case "X" -> importXRef(fields, lineNum, errorLines);
                        case "T" -> importTransaction(fields, lineNum, errorLines);
                        case "D" -> importCard(fields, lineNum, errorLines);
                        default  -> { errorLines.add(lineNum + "|UNKNOWN|Unknown record type: " + recType); errorCount++; }
                    }
                } catch (Exception e) {
                    errorLines.add(lineNum + "|" + recType + "|Unexpected error: " + e.getMessage());
                    errorCount++;
                }
            }
        }

        // Write error output (2750-WRITE-ERROR equivalent)
        try (BufferedWriter w = new BufferedWriter(new FileWriter(errorOutputPath, StandardCharsets.UTF_8))) {
            w.write("lineNum|recType|message"); w.newLine();
            for (String err : errorLines) { w.write(err); w.newLine(); }
        }

        log.info("DataImport: C={} A={} X={} T={} D={} errors={}", custImported, acctImported, xrefImported, tranImported, cardImported, errorCount);
        return RepeatStatus.FINISHED;
    }

    // ---- Customer (type 'C') ----
    private void importCustomer(String[] f, int lineNum, List<String> errors) {
        if (f.length < 17) { addError(errors, lineNum, "C", "Too few fields"); return; }
        long custId = parseLong(f[1]);
        // SEC-014: SSN validation (not in export - SSN not exported, skip)
        // SEC-014: FICO credit score range 0-999
        int fico = parseInt(f[16]);
        if (fico < 0 || fico > 999) { addError(errors, lineNum, "C", "FICO out of range: " + fico); return; }
        // SEC-014: DOB date format validation
        if (!isValidDate(f[13])) { addError(errors, lineNum, "C", "Invalid DOB date: " + f[13]); return; }

        customerRepository.save(new CustomerEntity(custId, f[2], f[3], f[4],
                f[5], f[6], f[7], f[8], f[9], f[10], f[11], f[12],
                0L, // SSN not exported (SEC-014)
                "", f[13], f[14], f[15], fico));
        custImported++;
    }

    // ---- Account (type 'A') ----
    private void importAccount(String[] f, int lineNum, List<String> errors) {
        if (f.length < 13) { addError(errors, lineNum, "A", "Too few fields"); return; }
        long acctId = parseLong(f[1]);
        BigDecimal creditLimit = parseBigDecimal(f[4]);
        // SEC-014: credit limit must be non-negative
        if (creditLimit.compareTo(BigDecimal.ZERO) < 0) {
            addError(errors, lineNum, "A", "Credit limit negative: " + creditLimit); return;
        }
        // SEC-014: expiration date format
        if (!isValidDate(f[7])) { addError(errors, lineNum, "A", "Invalid expiration date: " + f[7]); return; }
        // SEC-014: open date format
        if (!isValidDate(f[6])) { addError(errors, lineNum, "A", "Invalid open date: " + f[6]); return; }

        accountRepository.save(new AccountEntity(acctId, f[2],
                parseBigDecimal(f[3]), creditLimit, parseBigDecimal(f[5]),
                f[6], f[7], f[8],
                parseBigDecimal(f[9]), parseBigDecimal(f[10]),
                f[11], f[12]));
        acctImported++;
    }

    // ---- Card XREF (type 'X') ----
    private void importXRef(String[] f, int lineNum, List<String> errors) {
        if (f.length < 4) { addError(errors, lineNum, "X", "Too few fields"); return; }
        cardXRefRepository.save(new CardXRefEntity(f[1], parseLong(f[2]), parseLong(f[3])));
        xrefImported++;
    }

    // ---- Transaction (type 'T') ----
    private void importTransaction(String[] f, int lineNum, List<String> errors) {
        if (f.length < 14) { addError(errors, lineNum, "T", "Too few fields"); return; }
        transactionRepository.save(new TransactionEntity(
                f[1], f[2], parseInt(f[3]), f[4], f[5],
                parseBigDecimal(f[6]), parseLong(f[7]), f[8], f[9], f[10],
                f[11], f[12], f[13]));
        tranImported++;
    }

    // ---- Card (type 'D') ----
    private void importCard(String[] f, int lineNum, List<String> errors) {
        if (f.length < 6) { addError(errors, lineNum, "D", "Too few fields"); return; }
        // SEC-014: card expiration date format
        if (!isValidDate(f[4])) { addError(errors, lineNum, "D", "Invalid card expiration date: " + f[4]); return; }
        cardRepository.save(new CardEntity(f[1], parseLong(f[2]), f[3], f[4], f[5]));
        cardImported++;
    }

    // ---- Helpers ----
    private static void addError(List<String> errors, int lineNum, String type, String msg) {
        errors.add(lineNum + "|" + type + "|" + msg);
    }

    private boolean isValidDate(String s) {
        if (s == null || s.isBlank()) return false;
        try { LocalDate.parse(s.strip(), ISO_DATE); return true; }
        catch (DateTimeParseException e) { return false; }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s == null ? "0" : s.strip()); } catch (Exception e) { return 0L; }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s == null ? "0" : s.strip()); } catch (Exception e) { return 0; }
    }

    private static BigDecimal parseBigDecimal(String s) {
        try { return new BigDecimal(s == null ? "0" : s.strip()); } catch (Exception e) { return BigDecimal.ZERO; }
    }
}
