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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Exports all CardDemo data to a multi-record CSV file for branch migration.
 * Corresponds to CBEXPORT.cbl (582 LOC).
 *
 * Export format (pipe-delimited CSV, one record type per line):
 *   C|custId|firstName|middleName|lastName|addr1|addr2|addr3|state|country|zip|phone1|phone2|dob|eftAcct|priHolder|fico
 *   A|acctId|activeStatus|currentBalance|creditLimit|cashCreditLimit|openDate|expirationDate|reissueDate|cycCredit|cycDebit|addrZip|groupId
 *   X|cardNum|custId|acctId
 *   T|tranId|typeCode|catCode|source|desc|amount|merchantId|merchantName|merchantCity|merchantZip|cardNum|origTs|procTs
 *   D|cardNum|acctId|embossedName|expirationDate|activeStatus   (SEC-005: CVV omitted)
 *
 * SEC-005: CARD-CVV-CD is intentionally omitted from export (PCI DSS compliance).
 */
@Component
public class DataExportTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(DataExportTasklet.class);

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final CardXRefRepository cardXRefRepository;
    private final TransactionRepository transactionRepository;
    private final CardRepository cardRepository;
    private final String outputPath;

    public DataExportTasklet(CustomerRepository customerRepository,
                              AccountRepository accountRepository,
                              CardXRefRepository cardXRefRepository,
                              TransactionRepository transactionRepository,
                              CardRepository cardRepository,
                              @Value("${carddemo.reporting.export-output}") String outputPath) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.cardXRefRepository = cardXRefRepository;
        this.transactionRepository = transactionRepository;
        this.cardRepository = cardRepository;
        this.outputPath = outputPath;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        if (Path.of(outputPath).getParent() != null)
            Files.createDirectories(Path.of(outputPath).getParent());

        int custCount = 0, acctCount = 0, xrefCount = 0, tranCount = 0, cardCount = 0;

        try (BufferedWriter w = new BufferedWriter(new FileWriter(outputPath, StandardCharsets.UTF_8))) {
            // 2000-EXPORT-CUSTOMERS: type 'C'
            for (CustomerEntity c : customerRepository.findAllByOrderByIdAsc()) {
                writeCsv(w, "C", c.getId(), safe(c.getFirstName()), safe(c.getMiddleName()),
                        safe(c.getLastName()), safe(c.getAddrLine1()), safe(c.getAddrLine2()),
                        safe(c.getAddrLine3()), safe(c.getAddrStateCd()), safe(c.getAddrCountryCd()),
                        safe(c.getAddrZip()), safe(c.getPhoneNum1()), safe(c.getPhoneNum2()),
                        safe(c.getDob()), safe(c.getEftAccountId()), safe(c.getPriCardHolderInd()),
                        c.getFicoCreditScore());
                custCount++;
            }
            // 3000-EXPORT-ACCOUNTS: type 'A'
            for (AccountEntity a : accountRepository.findAllByOrderByIdAsc()) {
                writeCsv(w, "A", a.getId(), safe(a.getActiveStatus()),
                        fmtAmt(a.getCurrentBalance()), fmtAmt(a.getCreditLimit()),
                        fmtAmt(a.getCashCreditLimit()), safe(a.getOpenDate()),
                        safe(a.getExpirationDate()), safe(a.getReissueDate()),
                        fmtAmt(a.getCurrCycleCredit()), fmtAmt(a.getCurrCycleDebit()),
                        safe(a.getAddrZip()), safe(a.getGroupId()));
                acctCount++;
            }
            // 4000-EXPORT-XREFS: type 'X'
            for (CardXRefEntity x : cardXRefRepository.findAllByOrderByCardNumberAsc()) {
                writeCsv(w, "X", x.getCardNumber(), x.getCustomerId(), x.getAccountId());
                xrefCount++;
            }
            // 5000-EXPORT-TRANSACTIONS: type 'T'
            for (TransactionEntity t : transactionRepository.findAllByOrderByCardNumberAscIdAsc()) {
                writeCsv(w, "T", t.getId(), safe(t.getTypeCode()), t.getCategoryCode(),
                        safe(t.getSource()), safe(t.getDescription()), fmtAmt(t.getAmount()),
                        t.getMerchantId(), safe(t.getMerchantName()), safe(t.getMerchantCity()),
                        safe(t.getMerchantZip()), safe(t.getCardNumber()),
                        safe(t.getOriginalTimestamp()), safe(t.getProcessTimestamp()));
                tranCount++;
            }
            // 5500-EXPORT-CARDS: type 'D' (SEC-005: no CVV)
            for (CardEntity c : cardRepository.findAllByOrderByCardNumberAsc()) {
                writeCsv(w, "D", c.getCardNumber(), c.getAccountId(),
                        safe(c.getEmbossedName()), safe(c.getExpirationDate()), safe(c.getActiveStatus()));
                cardCount++;
            }
        }
        log.info("DataExport: C={} A={} X={} T={} D={} -> {}", custCount, acctCount, xrefCount, tranCount, cardCount, outputPath);
        return RepeatStatus.FINISHED;
    }

    private static void writeCsv(BufferedWriter w, Object... fields) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append('|');
            Object f = fields[i];
            String s = f == null ? "" : f.toString();
            sb.append(s.replace("|", "\\|"));
        }
        w.write(sb.toString());
        w.newLine();
    }

    private static String safe(String s) { return s != null ? s.strip() : ""; }
    private static String fmtAmt(java.math.BigDecimal v) {
        return v == null ? "0.00" : String.format(Locale.US, "%.2f", v);
    }
}
