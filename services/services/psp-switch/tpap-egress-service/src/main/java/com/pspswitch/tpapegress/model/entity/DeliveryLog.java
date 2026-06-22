package com.pspswitch.tpapegress.model.entity;

import com.pspswitch.tpapegress.model.event.EventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for the delivery_logs table.
 * One row per dispatched event capturing the final delivery outcome.
 *
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "delivery_logs")
public class DeliveryLog {

    /** Primary Identifier for the record. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Corresponds to the Kafka event ID sent by switch. */
    @Column(name = "event_id", nullable = false)
    private String eventId;

    /** The unique UPI transaction ID representing the payment or request. */
    @Column(name = "txn_id", nullable = false, length = 100)
    private String txnId;

    /** Originating TPAP application registry identifier. */
    @Column(name = "tpap_id", nullable = false, length = 50)
    private String tpapId;

    /** Operational type of event for polymorphic delivery routing. */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    /** The fully resolved HTTP webhook endpoint URL hit during payload POST. */
    @Column(name = "webhook_url", nullable = false, columnDefinition = "TEXT")
    private String webhookUrl;

    /** Exact status result of delivery process. Allowed values: SUCCESS, FAILED, SKIPPED. */
    @Column(nullable = false, length = 20)
    private String status;

    /** Null when SKIPPED or when all attempts threw connection exceptions. Represents HTTP outcome code. */
    @Column(name = "http_status")
    private Integer httpStatus;

    /** Number of attempts taken for dispatch delivery (including initial failure retries). */
    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private int attemptNumber = 1;

    /** Textual log description detailing any generated Java/HTTP errors or connection issues. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "delivered_at", nullable = false)
    @Builder.Default
    private Instant deliveredAt = Instant.now();
}
