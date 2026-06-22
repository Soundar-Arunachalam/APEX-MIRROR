package com.pspswitch.tpapegress.dispatcher.handler;

import com.pspswitch.tpapegress.model.event.EventType;
import com.pspswitch.tpapegress.model.event.SwitchCompletedEvent;

/**
 * Polymorphic handler interface (ADR-001).
 * One implementation per event type — no if-else in the dispatcher.
 * Implementors outline the handler contract, governing missing config behaviors, logging, and retry logic.
 *
 * @since 1.0
 */
public interface WebhookEventHandler {

    /**
     * Returns the target event type.
     * 
     * @return the {@link EventType} mapping for this handler instance
     */
    EventType supportedType();

    /** 
     * Process the event: config lookup → payload build → HTTP POST → delivery log.
     * Returns silently without throwing if no active config is found — see ADR-003.
     * Retries up to 3 times on 5xx or timeout. Does not retry on 4xx (see ADR-002).
     * Must save the delivery attempt to the delivery log entity regardless of success.
     *
     * @param event the {@link SwitchCompletedEvent} representing the Kafka message envelope
     */
    void handle(SwitchCompletedEvent event);
}
