package com.pspswitch.orchestrator.exception;

/**
 * Thrown by ValidationService when any of the 9 validation rules fail.
 * Caught by GlobalExceptionHandler → HTTP 400 + FAILED state response.
 */
public class ValidationException extends RuntimeException {

    private final String reason;

    public ValidationException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
