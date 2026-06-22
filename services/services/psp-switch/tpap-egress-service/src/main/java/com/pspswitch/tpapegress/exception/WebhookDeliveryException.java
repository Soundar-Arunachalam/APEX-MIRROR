package com.pspswitch.tpapegress.exception;

/**
 * Thrown by WebhookHttpClient on network-level failures:
 * connection refused, read timeout, DNS resolution failure, etc.
 *
 * This is a retryable error — the handler will retry up to MAX_RETRIES times.
 *
 * @since 1.0
 */
public class WebhookDeliveryException extends RuntimeException {

    /**
     * Constructs exception for simple network failure states.
     *
     * @param message failure cause description
     */
    public WebhookDeliveryException(String message) {
        super(message);
    }

    /**
     * Constructs exception with explicit nested throwable traces.
     *
     * @param message failure context label
     * @param cause nested underlying exception propagating up
     */
    public WebhookDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
