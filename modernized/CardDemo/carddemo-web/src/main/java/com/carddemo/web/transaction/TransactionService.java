package com.carddemo.web.transaction;

import com.carddemo.domain.entity.TransactionEntity;
import com.carddemo.domain.repository.AccountRepository;
import com.carddemo.domain.repository.CardXRefRepository;
import com.carddemo.domain.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Replicates COTRN02C — online transaction add.
 *
 * RULE-047: card number required.
 * RULE-048: type code required (non-blank).
 * RULE-049: category code ≥ 1.
 * RULE-050: amount must be positive (> 0).
 * RULE-051: two-step confirmation (Y required before save) — enforced in controller.
 * RULE-052: description required.
 * RULE-054: Q11 premise — strict LocalDate parse; no error 2513 suppression needed.
 * RULE-012: DB sequence ID (TransactionIdGenerator).
 */
@Service
public class TransactionService {

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.000000");

    private final TransactionRepository transactionRepository;
    private final CardXRefRepository cardXRefRepository;
    private final AccountRepository accountRepository;
    private final TransactionIdGenerator idGenerator;

    public TransactionService(TransactionRepository transactionRepository,
                              CardXRefRepository cardXRefRepository,
                              AccountRepository accountRepository,
                              TransactionIdGenerator idGenerator) {
        this.transactionRepository = transactionRepository;
        this.cardXRefRepository = cardXRefRepository;
        this.accountRepository = accountRepository;
        this.idGenerator = idGenerator;
    }

    public List<TransactionEntity> findByCard(String cardNumber) {
        return transactionRepository.findAllByCardNumberOrderByIdAsc(cardNumber);
    }

    public TransactionEntity findById(String tranId) {
        return transactionRepository.findById(tranId)
            .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + tranId));
    }

    /**
     * Validates and saves a new transaction (RULE-047–054).
     * Caller must have already received Y confirmation (RULE-051).
     */
    @Transactional
    public TransactionEntity add(TransactionAddRequest req) {
        // RULE-047
        if (req.cardNumber() == null || req.cardNumber().isBlank())
            throw new ValidationException("Card number is required (RULE-047)");
        // RULE-048
        if (req.typeCode() == null || req.typeCode().isBlank())
            throw new ValidationException("Transaction type code is required (RULE-048)");
        // RULE-049
        if (req.categoryCode() == null || req.categoryCode() < 1)
            throw new ValidationException("Category code must be >= 1 (RULE-049)");
        // RULE-050
        if (req.amount() == null || req.amount().compareTo(BigDecimal.ZERO) <= 0)
            throw new ValidationException("Amount must be positive (RULE-050)");
        // RULE-052
        if (req.description() == null || req.description().isBlank())
            throw new ValidationException("Description is required (RULE-052)");
        // RULE-054: Q11 — strict LocalDate parsing (no error 2513 suppression)
        if (req.originDate() != null && !req.originDate().isBlank()) {
            try { LocalDate.parse(req.originDate()); }
            catch (DateTimeParseException e) {
                throw new ValidationException("Invalid date format (must be YYYY-MM-DD): " + req.originDate());
            }
        }

        // Verify card exists in XREF
        if (cardXRefRepository.findById(req.cardNumber()).isEmpty())
            throw new ValidationException("Card not found in XREF: " + req.cardNumber());

        String ts = LocalDateTime.now().format(TS_FMT);
        String originTs = (req.originDate() != null && !req.originDate().isBlank())
            ? req.originDate() + " 00:00:00.000000" : ts;

        String tranId = idGenerator.nextId(); // RULE-012
        TransactionEntity tran = new TransactionEntity(
            tranId, req.typeCode(), req.categoryCode(),
            "Online", req.description(), req.amount(),
            0L, req.merchantName() != null ? req.merchantName() : "",
            "", "",
            req.cardNumber(), originTs, ts
        );
        return transactionRepository.save(tran);
    }
}
