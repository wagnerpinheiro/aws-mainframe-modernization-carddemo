package com.carddemo.web.transaction;

import com.carddemo.domain.repository.TransactionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates unique transaction IDs using an in-JVM AtomicLong sequence.
 *
 * RULE-012 fix: replaces COBOL READPREV+increment (STARTBR→READPREV→ENDBR→ADD 1 pattern
 * in COBIL00C/COTRN02C). The COBOL pattern races under concurrent writes; an AtomicLong
 * is thread-safe and structurally eliminates duplicates within a JVM lifetime.
 *
 * For production: replace with a database SEQUENCE (H2/PostgreSQL NEXTVAL) to survive
 * restarts without gaps. The PoC seeds from the current max ID + 1 on startup.
 *
 * ID format: 16-char zero-padded numeric string, matching TRAN-ID PIC X(16).
 */
@Component
public class TransactionIdGenerator {

    private final TransactionRepository transactionRepository;
    private final AtomicLong counter = new AtomicLong(1_000_000_000_000_000L);

    public TransactionIdGenerator(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @PostConstruct
    void init() {
        // Seed counter from existing transactions to avoid reuse after restart
        transactionRepository.findAll().stream()
            .map(t -> {
                try { return Long.parseLong(t.getId().strip()); }
                catch (NumberFormatException e) { return 0L; }
            })
            .mapToLong(Long::longValue)
            .max()
            .ifPresent(max -> counter.set(Math.max(counter.get(), max + 1)));
    }

    /** Returns the next unique 16-char zero-padded transaction ID. */
    public String nextId() {
        return String.format("%016d", counter.getAndIncrement());
    }
}
