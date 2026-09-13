package com.carddemo.common;

/** Thrown when a data record cannot be parsed due to unexpected format or content. */
public class InvalidDataException extends RuntimeException {
    public InvalidDataException(String message) {
        super(message);
    }

    public InvalidDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
