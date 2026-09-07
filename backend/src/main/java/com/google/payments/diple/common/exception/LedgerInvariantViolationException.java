package com.google.payments.diple.common.exception;

public class LedgerInvariantViolationException extends RuntimeException {
    public LedgerInvariantViolationException(String message) {
        super(message);
    }
}
