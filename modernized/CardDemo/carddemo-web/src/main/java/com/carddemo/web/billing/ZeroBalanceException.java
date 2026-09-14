package com.carddemo.web.billing;

/** Thrown when a bill payment is attempted on an account with zero or negative balance (RULE-011). */
public class ZeroBalanceException extends RuntimeException {
    public ZeroBalanceException(String message) { super(message); }
}
