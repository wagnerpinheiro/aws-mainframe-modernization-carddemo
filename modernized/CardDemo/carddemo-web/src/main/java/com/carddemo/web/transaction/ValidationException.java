package com.carddemo.web.transaction;

/** Thrown when transaction input fails business validation (RULE-047–054). */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) { super(message); }
}
