package com.pspswitch.tpapingress.exception;

public class IdempotencyStoreException extends RuntimeException {
    public IdempotencyStoreException(String message) {
        super(message);
    }
}
