package com.carddemo.batch.eod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes TransactionValidationResult items:
 * - VALID records: logged at DEBUG (mirrors COBOL DISPLAY "SUCCESSFUL READ OF ACCOUNT FILE")
 * - CARD_NOT_FOUND / ACCOUNT_NOT_FOUND: logged at WARN + written to structured CSV reject file
 *
 * Q6 premise (established): reject CSV written to carddemo.batch.reject-output (configurable path).
 * No auto-reprocess in legacy or PoC.
 * TODO(prod): confirm operational reject disposition — manual review / resubmission / discard. (DG-5)
 *
 * Legacy: CBTRN01C produces DISPLAY output only; no reject file exists in COBOL.
 * This is a deliberate PoC enhancement over the legacy behavior.
 *
 * CSV format: transactionId,cardNumber,resolvedAccountId,status
 *
 * IMPLEMENTATION NOTE (HIGH-1 fix): BufferedWriter is used instead of PrintWriter.
 * PrintWriter silently catches all IOException internally and sets an error flag
 * (checkError()) that callers almost never check — meaning a disk-full event during
 * a batch run produces a partial reject file and BatchStatus.COMPLETED with no alarm.
 * BufferedWriter propagates IOException as a checked exception; write() declares
 * "throws Exception" (from ItemWriter), so any I/O failure surfaces as a step failure
 * with BatchStatus.FAILED, keeping the job outcome trustworthy.
 */
@Component
public class TransactionValidationWriter implements ItemStreamWriter<TransactionValidationResult> {

    private static final Logger log = LoggerFactory.getLogger(TransactionValidationWriter.class);

    private final String rejectOutputPath;
    private BufferedWriter rejectWriter;

    public TransactionValidationWriter(
            @Value("${carddemo.batch.reject-output}") String rejectOutputPath) {
        this.rejectOutputPath = rejectOutputPath;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        close(); // reset if a previous step run left it open
        try {
            Path rejectPath = Path.of(rejectOutputPath);
            if (rejectPath.getParent() != null) {
                Files.createDirectories(rejectPath.getParent());
            }
            rejectWriter = new BufferedWriter(
                new FileWriter(rejectPath.toFile(), StandardCharsets.UTF_8));
            rejectWriter.write("transactionId,cardNumber,resolvedAccountId,status");
            rejectWriter.newLine();
        } catch (IOException e) {
            throw new ItemStreamException("Cannot open reject output: " + rejectOutputPath, e);
        }
    }

    /**
     * Writes one CSV row per rejected item.  IOException propagates as a step failure
     * (ItemWriter.write() declares throws Exception), so a disk error surfaces immediately
     * rather than being silently absorbed.
     */
    @Override
    public void write(Chunk<? extends TransactionValidationResult> chunk) throws Exception {
        for (TransactionValidationResult result : chunk.getItems()) {
            if (result.status() == ValidationStatus.VALID) {
                log.debug("VALID: transactionId={}, cardNumber={}, accountId={}",
                    result.record().transactionId(),
                    result.record().cardNumber(),
                    result.resolvedAccountId());
            } else {
                log.warn("{}: transactionId={}, cardNumber={}, accountId={}",
                    result.status(),
                    result.record().transactionId(),
                    result.record().cardNumber(),
                    result.resolvedAccountId());
                rejectWriter.write(String.format("%s,%s,%s,%s",
                    result.record().transactionId(),
                    result.record().cardNumber(),
                    result.resolvedAccountId() != null ? result.resolvedAccountId() : "",
                    result.status()));
                rejectWriter.newLine();
            }
        }
        rejectWriter.flush(); // propagates IOException — a flush failure is a step failure
    }

    @Override
    public void update(ExecutionContext executionContext) {}

    @Override
    public void close() {
        if (rejectWriter != null) {
            try {
                rejectWriter.close();
            } catch (IOException e) {
                throw new ItemStreamException(
                    "Failed to close reject output: " + rejectOutputPath, e);
            } finally {
                rejectWriter = null; // always null out so open() stays idempotent
            }
        }
    }
}
