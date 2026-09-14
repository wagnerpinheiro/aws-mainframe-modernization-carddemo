package com.carddemo.batch.reporting;

import com.carddemo.domain.entity.AccountEntity;
import com.carddemo.domain.repository.AccountRepository;
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
import java.util.Locale;

/**
 * Reads all accounts and writes a text report.
 * Corresponds to CBACT01C.cbl: reads ACCTFILE sequentially and DISPLAYs each account.
 * The Java version writes to a structured text file instead of DISPLAY/multiple output files.
 * RULE-021 (three-level totaling) is not applicable here (CBACT01C only displays, no totaling).
 */
@Component
public class AccountReportTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(AccountReportTasklet.class);
    private static final String SEP = "-".repeat(80);

    private final AccountRepository accountRepository;
    private final String outputPath;

    public AccountReportTasklet(AccountRepository accountRepository,
            @Value("${carddemo.reporting.account-report-output}") String outputPath) {
        this.accountRepository = accountRepository;
        this.outputPath = outputPath;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        List<AccountEntity> accounts = accountRepository.findAllByOrderByIdAsc();
        Files.createDirectories(Path.of(outputPath).getParent() != null
                ? Path.of(outputPath).getParent() : Path.of("."));

        try (BufferedWriter w = new BufferedWriter(new FileWriter(outputPath, StandardCharsets.UTF_8))) {
            w.write("ACCOUNT REPORT"); w.newLine();
            w.write(SEP); w.newLine();
            for (AccountEntity a : accounts) {
                w.write(String.format(Locale.US,
                        "ACCT-ID: %-11d  STATUS: %s  BALANCE: %12.2f  LIMIT: %12.2f  OPEN: %s  EXPIRY: %s",
                        a.getId(), a.getActiveStatus(), a.getCurrentBalance(),
                        a.getCreditLimit(), a.getOpenDate(), a.getExpirationDate()));
                w.newLine();
            }
            w.write(SEP); w.newLine();
            w.write(String.format("TOTAL ACCOUNTS: %d", accounts.size())); w.newLine();
        }
        log.info("AccountReport: wrote {} accounts to {}", accounts.size(), outputPath);
        return RepeatStatus.FINISHED;
    }
}
