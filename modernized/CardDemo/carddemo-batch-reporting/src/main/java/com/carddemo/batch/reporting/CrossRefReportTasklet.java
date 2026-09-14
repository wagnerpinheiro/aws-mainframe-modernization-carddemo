package com.carddemo.batch.reporting;

import com.carddemo.domain.entity.CardXRefEntity;
import com.carddemo.domain.repository.CardXRefRepository;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads all card cross-reference records and writes a report.
 * Corresponds to CBACT03C.cbl: reads and prints account cross reference data file.
 */
@Component
public class CrossRefReportTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(CrossRefReportTasklet.class);
    private static final String SEP = "-".repeat(80);

    private final CardXRefRepository cardXRefRepository;
    private final String outputPath;

    public CrossRefReportTasklet(CardXRefRepository cardXRefRepository,
            @Value("${carddemo.reporting.xref-report-output}") String outputPath) {
        this.cardXRefRepository = cardXRefRepository;
        this.outputPath = outputPath;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        List<CardXRefEntity> xrefs = cardXRefRepository.findAllByOrderByCardNumberAsc();
        if (Path.of(outputPath).getParent() != null)
            Files.createDirectories(Path.of(outputPath).getParent());

        try (BufferedWriter w = new BufferedWriter(new FileWriter(outputPath, StandardCharsets.UTF_8))) {
            w.write("CARD CROSS REFERENCE REPORT"); w.newLine();
            w.write(SEP); w.newLine();
            for (CardXRefEntity x : xrefs) {
                w.write(String.format("CARD-NUM: %s  CUST-ID: %09d  ACCT-ID: %011d",
                        x.getCardNumber(), x.getCustomerId(), x.getAccountId()));
                w.newLine();
            }
            w.write(SEP); w.newLine();
            w.write(String.format("TOTAL XREF RECORDS: %d", xrefs.size())); w.newLine();
        }
        log.info("CrossRefReport: wrote {} xref records to {}", xrefs.size(), outputPath);
        return RepeatStatus.FINISHED;
    }
}
