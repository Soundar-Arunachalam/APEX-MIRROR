package com.pspswitch.tpapegress.exception;

/**
 * Thrown when the EventHandlerFactory cannot resolve a handler
 * for the given event type (including null).
 * Matches rule (ADR-001) where unknown types lead to processing exception.
 *
 * @since 1.0
 */
public class UnknownEventTypeException extends RuntimeException {

    /**
     * Constructs exception outlining what event type had no concrete dispatch handler registered.
     *
     * @param eventType the unrecognized raw generic or String denoting the type
     */
    public UnknownEventTypeException(Object eventType) {
        super("No handler registered for event type: " + eventType);
    }
}
