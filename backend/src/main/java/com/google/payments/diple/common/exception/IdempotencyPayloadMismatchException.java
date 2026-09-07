package com.google.payments.diple.common.exception;

public class IdempotencyPayloadMismatchException extends RuntimeException {
    private final String idempotencyKey;

    public IdempotencyPayloadMismatchException(String idempotencyKey, String message) {
        super(message);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
