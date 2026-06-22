package com.pspswitch.tpapegress.exception;

/**
 * Internal coding error — not the "no config found" skip case.
 * Thrown when a handler expects a config to exist but cannot find it
 * due to a programming mistake.
 */
public class WebhookConfigNotFoundException extends RuntimeException {

    public WebhookConfigNotFoundException(String message) {
        super(message);
    }
}
