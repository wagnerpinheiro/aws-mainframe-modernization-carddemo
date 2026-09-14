package com.carddemo.batch.eod;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Routes each {@link TransactionPostingResult}:
 * - VALID → {@link TransactionPostingService#post(TransactionPostingResult)} (atomic DB writes)
 * - CARD_NOT_FOUND / ACCOUNT_NOT_FOUND / OVERLIMIT / EXPIRED →
 *     reject CSV (transactionId, cardNumber, rejectCode, rejectDescription)
 *
 * Q6 premise: reject CSV written to carddemo.batch.posting-reject-output.
 * DALYREJS format in COBOL: 350-byte tran + 80-byte trailer. Java uses CSV for readability.
 * TODO(prod): confirm reject disposition. (DG-5)
 *
 * IOException in write() propagates as step failure (BufferedWriter, not PrintWriter).
 */
@Component
public class TransactionPostingWriter implements ItemStreamWriter<TransactionPostingResult> {

    // TODO(step2): inject TransactionPostingService and reject path; implement open/write/close
    public TransactionPostingWriter(
            TransactionPostingService postingService,
            @Value("${carddemo.batch.posting-reject-output}") String rejectOutputPath) {
    }

    @Override
    public void open(ExecutionContext ctx) throws ItemStreamException {
        throw new UnsupportedOperationException("TransactionPostingWriter.open() not yet implemented");
    }

    @Override
    public void write(Chunk<? extends TransactionPostingResult> chunk) throws Exception {
        throw new UnsupportedOperationException("TransactionPostingWriter.write() not yet implemented");
    }

    @Override
    public void update(ExecutionContext ctx) {}

    @Override
    public void close() {}
}
