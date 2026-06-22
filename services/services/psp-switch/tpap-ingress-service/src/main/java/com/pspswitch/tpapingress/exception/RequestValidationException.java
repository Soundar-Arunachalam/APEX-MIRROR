package com.pspswitch.tpapingress.exception;

import lombok.Getter;

/**
 * Thrown when request validation fails.
 * Carries a machine-readable errorCode that maps directly to the Error Response Catalog.
 */
@Getter
public class RequestValidationException extends RuntimeException {
    private final String errorCode;
    private final String field;

    public RequestValidationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.field = null;
    }

    public RequestValidationException(String errorCode, String message, String field) {
        super(message);
        this.errorCode = errorCode;
        this.field = field;
    }
}
