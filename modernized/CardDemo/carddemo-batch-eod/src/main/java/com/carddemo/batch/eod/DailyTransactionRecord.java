package com.carddemo.batch.eod;

import java.math.BigDecimal;

/**
 * Parsed representation of one DALYTRAN fixed-width record (CVTRA06Y, 350 bytes).
 *
 * Field byte positions (1-indexed, matching CVTRA06Y):
 *   transactionId    1–16
 *   typeCode        17–18
 *   categoryCode    19–22
 *   source          23–32
 *   description     33–132
 *   amount         133–143  S9(9)V99 sign-overpunched
 *   merchantId     144–152  9(9)
 *   merchantName   153–202
 *   merchantCity   203–252
 *   merchantZip    253–262
 *   cardNumber     263–278
 *   originTs       279–304
 *   processTs      305–330
 *   (filler        331–350 — not captured)
 */
public record DailyTransactionRecord(
    String transactionId,
    String typeCode,
    int categoryCode,
    String source,
    String description,
    BigDecimal amount,
    long merchantId,
    String merchantName,
    String merchantCity,
    String merchantZip,
    String cardNumber,
    String originTimestamp,
    String processTimestamp
) {}
