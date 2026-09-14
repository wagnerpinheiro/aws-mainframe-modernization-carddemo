package com.carddemo.batch.reporting;

import com.carddemo.domain.entity.CardEntity;
import com.carddemo.domain.repository.CardRepository;
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
 * Reads all card records and writes a report.
 * Corresponds to CBACT02C.cbl: reads and prints card data file.
 * SEC-005: CVV field is absent (PCI DSS compliance).
 */
@Component
public class CardReportTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(CardReportTasklet.class);
    private static final String SEP = "-".repeat(80);

    private final CardRepository cardRepository;
    private final String outputPath;

    public CardReportTasklet(CardRepository cardRepository,
            @Value("${carddemo.reporting.card-report-output}") String outputPath) {
        this.cardRepository = cardRepository;
        this.outputPath = outputPath;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        List<CardEntity> cards = cardRepository.findAllByOrderByCardNumberAsc();
        if (Path.of(outputPath).getParent() != null)
            Files.createDirectories(Path.of(outputPath).getParent());

        try (BufferedWriter w = new BufferedWriter(new FileWriter(outputPath, StandardCharsets.UTF_8))) {
            w.write("CARD DATA REPORT"); w.newLine();
            w.write(SEP); w.newLine();
            for (CardEntity c : cards) {
                // SEC-005: CVV omitted
                w.write(String.format("CARD-NUM: %s  ACCT-ID: %011d  NAME: %-30s  EXPIRY: %s  STATUS: %s",
                        c.getCardNumber(), c.getAccountId(),
                        c.getEmbossedName() != null ? c.getEmbossedName().strip() : "",
                        c.getExpirationDate(), c.getActiveStatus()));
                w.newLine();
            }
            w.write(SEP); w.newLine();
            w.write(String.format("TOTAL CARDS: %d", cards.size())); w.newLine();
        }
        log.info("CardReport: wrote {} cards to {}", cards.size(), outputPath);
        return RepeatStatus.FINISHED;
    }
}
