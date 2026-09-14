package com.carddemo.web.transaction;

import java.math.BigDecimal;

/** Form model for COTRN02C — online transaction add. */
public record TransactionAddRequest(
    String cardNumber,
    String typeCode,
    Integer categoryCode,
    BigDecimal amount,
    String description,
    String merchantName,
    String originDate
) {}
