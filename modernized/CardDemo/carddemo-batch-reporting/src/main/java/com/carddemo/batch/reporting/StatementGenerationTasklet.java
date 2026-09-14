package com.carddemo.batch.reporting;

import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.entity.CardXRefEntity;
import com.carddemo.domain.entity.CustomerEntity;
import com.carddemo.domain.entity.TransactionEntity;
import com.carddemo.domain.repository.AccountRepository;
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
import java.util.List;

/**
 * Generates account statements in plain text and HTML.
 * Replaces CBSTM03A.cbl (924 LOC) + CBSTM03B.cbl (230 LOC) as a single Tasklet.
 *
 * Algorithm (directly equivalent to CBSTM03A 1000-MAINLINE):
 * 1. Read all XREF records sequentially (sorted by card number — CBSTM03B XREFFILE sequential read).
 * 2. For each XREF:
 *    a. Read customer by custId  (CBSTM03B CUSTFILE READ-K → 2000-CUSTFILE-GET)
 *    b. Read account by acctId   (CBSTM03B ACCTFILE READ-K → 3000-ACCTFILE-GET)
 *    c. Get transactions for card (replaces WS-TRNX-TABLE in-memory lookup → 4000-TRNXFILE-GET)
 *    d. Write text statement     (5000-CREATE-STATEMENT + 6000-WRITE-TRANS)
 *    e. Write HTML statement     (5100/5200-WRITE-HTML-* + 6000-WRITE-TRANS HTML)
 *
 * Dropped: PSA/TCB/TIOT block addressing (z/OS-specific memory structure, lines 266-291).
 * Dropped: ALTER/GO TO dispatch (0000-START state machine) — replaced by direct method calls.
 * Dropped: WS-TRNX-TABLE 2D array (51 cards × 10 txns) — replaced by JPA query per card.
 */
@Component
public class StatementGenerationTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(StatementGenerationTasklet.class);

    private final CardXRefRepository cardXRefRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final String textOutputPath;
    private final String htmlOutputPath;

    public StatementGenerationTasklet(
            CardXRefRepository cardXRefRepository,
            CustomerRepository customerRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            @Value("${carddemo.reporting.statement-text-output}") String textOutputPath,
            @Value("${carddemo.reporting.statement-html-output}") String htmlOutputPath) {
        this.cardXRefRepository = cardXRefRepository;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.textOutputPath = textOutputPath;
        this.htmlOutputPath = htmlOutputPath;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext)
            throws Exception {
        // CBSTM03A: OPEN OUTPUT STMT-FILE HTML-FILE
        ensureParentDir(textOutputPath);
        ensureParentDir(htmlOutputPath);

        try (BufferedWriter textWriter = new BufferedWriter(
                 new FileWriter(textOutputPath, StandardCharsets.UTF_8));
             BufferedWriter htmlWriter = new BufferedWriter(
                 new FileWriter(htmlOutputPath, StandardCharsets.UTF_8))) {

            // 1000-MAINLINE: PERFORM UNTIL END-OF-FILE — read XREF sequentially
            List<CardXRefEntity> xrefList = cardXRefRepository.findAllByOrderByCardNumberAsc();

            if (xrefList.isEmpty()) {
                log.info("No XREF records found — no statements to generate");
                return RepeatStatus.FINISHED;
            }

            int accountsProcessed = 0;
            for (CardXRefEntity xref : xrefList) {
                processOneAccount(xref, textWriter, htmlWriter);
                accountsProcessed++;
            }

            log.info("Statement generation complete. Accounts: {}", accountsProcessed);
        }
        return RepeatStatus.FINISHED;
    }

    /**
     * Processes a single account entry — equivalent to one iteration of CBSTM03A 1000-MAINLINE.
     */
    private void processOneAccount(CardXRefEntity xref,
                                    BufferedWriter textWriter,
                                    BufferedWriter htmlWriter) throws IOException {
        // 2000-CUSTFILE-GET: CBSTM03B CUSTFILE READ-K by XREF-CUST-ID
        CustomerEntity customer = customerRepository.findById(xref.getCustomerId())
            .orElse(null);
        if (customer == null) {
            log.warn("Customer not found: custId={}, cardNumber={}", xref.getCustomerId(), xref.getCardNumber());
            return;
        }

        // 3000-ACCTFILE-GET: CBSTM03B ACCTFILE READ-K by XREF-ACCT-ID
        AccountEntity account = accountRepository.findById(xref.getAccountId())
            .orElse(null);
        if (account == null) {
            log.warn("Account not found: acctId={}, cardNumber={}", xref.getAccountId(), xref.getCardNumber());
            return;
        }

        // 4000-TRNXFILE-GET: look up transactions for this card in WS-TRNX-TABLE
        // In Java: JPA query by card number, sorted by transaction ID (COSTM01 key order)
        List<TransactionEntity> transactions =
            transactionRepository.findAllByCardNumberOrderByIdAsc(xref.getCardNumber());

        // 5000-CREATE-STATEMENT + 6000-WRITE-TRANS (text)
        List<String> textLines = StatementFormatter.formatText(customer, account, transactions);
        for (String line : textLines) {
            textWriter.write(line);
            textWriter.newLine();
        }

        // 5100/5200-WRITE-HTML-* + 6000-WRITE-TRANS (HTML)
        List<String> htmlLines = StatementFormatter.formatHtml(customer, account, transactions);
        for (String line : htmlLines) {
            htmlWriter.write(line);
            htmlWriter.newLine();
        }

        log.debug("Statement written: acctId={}, cardNumber={}, txnCount={}",
            account.getId(), xref.getCardNumber(), transactions.size());
    }

    private static void ensureParentDir(String path) throws IOException {
        Path parent = Path.of(path).getParent();
        if (parent != null) Files.createDirectories(parent);
    }
}
