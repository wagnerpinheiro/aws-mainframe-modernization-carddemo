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
 * Routes each {@link TransactionPostingResult}:
 * - VALID → {@link TransactionPostingService#post(TransactionPostingResult)} (atomic DB writes)
 * - CARD_NOT_FOUND / ACCOUNT_NOT_FOUND / OVERLIMIT / EXPIRED →
 *     structured CSV reject file (transactionId, cardNumber, rejectCode, rejectDescription)
 *
 * DALYREJS in COBOL: 350-byte DALYTRAN record + 80-byte validation trailer.
 * Java equivalent: structured CSV for readability and grep-ability.
 * No auto-reprocess in legacy or PoC.
 * TODO(prod): confirm reject disposition — manual review / resubmission / discard. (DG-5)
 *
 * IOException in write() propagates as step failure (BufferedWriter, not PrintWriter —
 * same design as TransactionValidationWriter per HIGH-1 architecture finding).
 */
@Component
public class TransactionPostingWriter implements ItemStreamWriter<TransactionPostingResult> {

    private static final Logger log = LoggerFactory.getLogger(TransactionPostingWriter.class);

    private final TransactionPostingService postingService;
    private final String rejectOutputPath;
    private BufferedWriter rejectWriter;

    public TransactionPostingWriter(
            TransactionPostingService postingService,
            @Value("${carddemo.batch.posting-reject-output}") String rejectOutputPath) {
        this.postingService = postingService;
        this.rejectOutputPath = rejectOutputPath;
    }

    @Override
    public void open(ExecutionContext ctx) throws ItemStreamException {
        close();
        try {
            Path rejectPath = Path.of(rejectOutputPath);
            if (rejectPath.getParent() != null) {
                Files.createDirectories(rejectPath.getParent());
            }
            rejectWriter = new BufferedWriter(
                new FileWriter(rejectPath.toFile(), StandardCharsets.UTF_8));
            rejectWriter.write("transactionId,cardNumber,rejectCode,rejectDescription");
            rejectWriter.newLine();
        } catch (IOException e) {
            throw new ItemStreamException("Cannot open posting reject output: " + rejectOutputPath, e);
        }
    }

    /**
     * For valid records: delegates to {@link TransactionPostingService#post} (SERIALIZABLE,
     * REQUIRES_NEW — each record is independently atomic).
     * For invalid records: writes one CSV row to the reject file.
     * IOException propagates as a step failure so disk errors are never silently swallowed.
     */
    @Override
    public void write(Chunk<? extends TransactionPostingResult> chunk) throws Exception {
        for (TransactionPostingResult result : chunk.getItems()) {
            if (result.isValid()) {
                postingService.post(result);
                log.debug("Posted: transactionId={}, accountId={}",
                    result.source().transactionId(), result.xrefAccountId());
            } else {
                log.warn("Rejected (code {} — {}): transactionId={}, cardNumber={}",
                    result.rejectCode(), result.rejectDescription(),
                    result.source().transactionId(), result.source().cardNumber());
                rejectWriter.write(String.format("%s,%s,%d,%s",
                    result.source().transactionId(),
                    result.source().cardNumber(),
                    result.rejectCode(),
                    result.rejectDescription()));
                rejectWriter.newLine();
            }
        }
        rejectWriter.flush();
    }

    @Override
    public void update(ExecutionContext ctx) {}

    @Override
    public void close() {
        if (rejectWriter != null) {
            try {
                rejectWriter.close();
            } catch (IOException e) {
                throw new ItemStreamException(
                    "Failed to close posting reject output: " + rejectOutputPath, e);
            } finally {
                rejectWriter = null;
            }
        }
    }
}
