package com.carddemo.batch.reporting;

import com.carddemo.domain.entity.CardXRefEntity;
import com.carddemo.domain.entity.TransactionEntity;
import com.carddemo.domain.repository.CardXRefRepository;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Prints a transaction detail report filtered by date range.
 * Corresponds to CBTRN03C.cbl (649 LOC).
 *
 * RULE-022: page size = 20 lines per page.
 * RULE-023: date-range filter from job parameters (startDate, endDate).
 *           Filter uses TRAN-PROC-TS(1:10) >= startDate AND <= endDate.
 * RULE-021: three-level totaling — page total, account (card) total, grand total.
 *
 * Date parameters come from job execution context set before launch, or
 * from @Value defaults (accept all dates if not specified).
 */
@Component
public class TransactionReportTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(TransactionReportTasklet.class);
    private static final String SEP = "=".repeat(133);
    private static final String DASH = "-".repeat(133);
    private static final int PAGE_SIZE = 20; // RULE-022: WS-PAGE-SIZE

    private final TransactionRepository transactionRepository;
    private final CardXRefRepository cardXRefRepository;
    private final String outputPath;

    public TransactionReportTasklet(
            TransactionRepository transactionRepository,
            CardXRefRepository cardXRefRepository,
            @Value("${carddemo.reporting.transaction-report-output}") String outputPath) {
        this.transactionRepository = transactionRepository;
        this.cardXRefRepository = cardXRefRepository;
        this.outputPath = outputPath;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        // RULE-023: read date range from job parameters; default = accept all
        String startDate = getParam(chunkContext, "startDate", "0000-00-00");
        String endDate   = getParam(chunkContext, "endDate",   "9999-99-99");

        List<TransactionEntity> transactions = transactionRepository
                .findAllByOrderByCardNumberAscIdAsc()
                .stream()
                .filter(t -> {
                    String procDate = t.getProcessTimestamp() != null && t.getProcessTimestamp().length() >= 10
                            ? t.getProcessTimestamp().substring(0, 10) : "";
                    return procDate.compareTo(startDate) >= 0 && procDate.compareTo(endDate) <= 0;
                })
                .collect(Collectors.toList());

        // Build XREF map (card → accountId) for the report
        Map<String, Long> cardToAcct = cardXRefRepository.findAllByOrderByCardNumberAsc()
                .stream().collect(Collectors.toMap(CardXRefEntity::getCardNumber,
                        CardXRefEntity::getAccountId, (a, b) -> a));

        if (Path.of(outputPath).getParent() != null)
            Files.createDirectories(Path.of(outputPath).getParent());

        try (BufferedWriter w = new BufferedWriter(new FileWriter(outputPath, StandardCharsets.UTF_8))) {
            writeHeader(w, startDate, endDate);

            BigDecimal pageTotal    = BigDecimal.ZERO;
            BigDecimal accountTotal = BigDecimal.ZERO;
            BigDecimal grandTotal   = BigDecimal.ZERO;
            String     currentCard  = "";
            int        lineCounter  = 0;
            int        pageNum      = 1;

            for (TransactionEntity t : transactions) {
                // Account (card) break
                if (!t.getCardNumber().equals(currentCard)) {
                    if (!currentCard.isEmpty()) {
                        // Write account totals (1120-WRITE-ACCOUNT-TOTALS)
                        w.write(DASH); w.newLine();
                        w.write(String.format(Locale.US, "  ACCOUNT TOTAL for card %s: %15.2f", currentCard, accountTotal));
                        w.newLine();
                        accountTotal = BigDecimal.ZERO;
                    }
                    currentCard = t.getCardNumber();
                    Long acctId = cardToAcct.get(currentCard);
                    w.write(String.format("  CARD: %s  ACCT: %s", currentCard,
                            acctId != null ? String.format("%011d", acctId) : "N/A"));
                    w.newLine();
                }

                // Page break check (RULE-022)
                if (lineCounter > 0 && lineCounter % PAGE_SIZE == 0) {
                    w.write(String.format(Locale.US, "  PAGE %d TOTAL: %15.2f", pageNum, pageTotal));
                    w.newLine(); w.write(SEP); w.newLine();
                    pageTotal = BigDecimal.ZERO;
                    pageNum++;
                    writeHeader(w, startDate, endDate);
                }

                // Write transaction line (1100-WRITE-TRANSACTION-REPORT)
                w.write(String.format(Locale.US, "  %-16s  %-26s  %-50s  %12.2f",
                        t.getId(),
                        t.getProcessTimestamp() != null ? t.getProcessTimestamp().substring(0, Math.min(26, t.getProcessTimestamp().length())) : "",
                        t.getDescription() != null ? t.getDescription().strip().substring(0, Math.min(50, t.getDescription().strip().length())) : "",
                        t.getAmount()));
                w.newLine();

                pageTotal    = pageTotal.add(t.getAmount());
                accountTotal = accountTotal.add(t.getAmount());
                grandTotal   = grandTotal.add(t.getAmount());
                lineCounter++;
            }

            // Final account total
            if (!currentCard.isEmpty()) {
                w.write(DASH); w.newLine();
                w.write(String.format(Locale.US, "  ACCOUNT TOTAL for card %s: %15.2f", currentCard, accountTotal));
                w.newLine();
            }

            // Grand total (RULE-021)
            w.write(SEP); w.newLine();
            w.write(String.format(Locale.US, "  GRAND TOTAL: %15.2f  TRANSACTIONS: %d", grandTotal, transactions.size()));
            w.newLine();
        }
        log.info("TransactionReport: {} transactions, range [{}, {}], output {}", transactions.size(), startDate, endDate, outputPath);
        return RepeatStatus.FINISHED;
    }

    private void writeHeader(BufferedWriter w, String startDate, String endDate) throws Exception {
        w.write(SEP); w.newLine();
        w.write(String.format("  TRANSACTION DETAIL REPORT   DATE RANGE: %s TO %s", startDate, endDate));
        w.newLine();
        w.write(String.format("  %-16s  %-26s  %-50s  %12s", "TRAN ID", "PROC TIMESTAMP", "DESCRIPTION", "AMOUNT"));
        w.newLine(); w.write(SEP); w.newLine();
    }

    private static String getParam(ChunkContext ctx, String key, String defaultVal) {
        try {
            Object val = ctx.getStepContext().getJobParameters().get(key);
            return val != null ? val.toString() : defaultVal;
        } catch (Exception e) {
            return defaultVal;
        }
    }
}
