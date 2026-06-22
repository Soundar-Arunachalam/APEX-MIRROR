package com.pspswitch.tpapegress.model.payload;

import java.time.Instant;

/**
 * Common webhook payload envelope.
 * All concrete payloads implement this interface so the HTTP client
 * can accept any payload type polymorphically for outbound HTTP posts.
 * Provides guaranteed extraction of envelope-level fields representing the event.
 *
 * @since 1.0
 */
public interface WebhookPayload {

    /**
     * Gets the unique tracking event ID.
     * @return the string representing Kafka message event ID
     */
    String  getEventId();

    /**
     * Gets the operation event type.
     * @return string value corresponding to the exact event format
     */
    String  getEventType();

    /**
     * Gets the target destination TPAP registry ID.
     * @return string TPAP registry ID
     */
    String  getTpapId();

    /**
     * Gets the core payment/transaction ID.
     * @return UPI generated transaction string
     */
    String  getTxnId();

    /**
     * Gets the client payload correlation map.
     * @return unique client tracking correlation ID
     */
    String  getCorrelationId();

    /**
     * Gets the moment the payload was dispatched successfully or failed formatting.
     * @return absolute delivery instantiation trigger point
     */
    Instant getDeliveredAt();
}
