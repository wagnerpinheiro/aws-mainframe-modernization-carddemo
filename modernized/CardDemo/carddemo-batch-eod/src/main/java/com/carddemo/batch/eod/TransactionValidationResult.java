package com.carddemo.batch.eod;

/**
 * Enriches a raw DALYTRAN record with its validation outcome.
 * Used as the item type flowing from processor to writer.
 */
public record TransactionValidationResult(
    DailyTransactionRecord record,
    ValidationStatus status,
    Long resolvedAccountId   // null when status is CARD_NOT_FOUND
) {}
