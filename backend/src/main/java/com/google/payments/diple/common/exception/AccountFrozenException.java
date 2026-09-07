package com.google.payments.diple.common.exception;

public class AccountFrozenException extends RuntimeException {
    public AccountFrozenException(String message) {
        super(message);
    }
}
