package com.pspswitch.ledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable audit log entry for every transaction status transition.
 * Append-only — rows are never updated or deleted.
 */
@Entity
@Table(name = "txn_status_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TxnStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tid",           length = 35, nullable = false)
    private String tid;

    @Column(name = "from_status",   length = 20)
    private String fromStatus;

    @Column(name = "to_status",     length = 20, nullable = false)
    private String toStatus;

    @Column(name = "source_service", length = 50, nullable = false)
    private String sourceService;

    @Column(name = "reason",        length = 500)
    private String reason;

    @Column(name = "kafka_topic",   length = 200)
    private String kafkaTopic;

    @Column(name = "kafka_offset")
    private Long kafkaOffset;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_payload", columnDefinition = "jsonb")
    private Map<String, Object> eventPayload;

    @Column(name = "occurred_at",   nullable = false)
    private Instant occurredAt;

    @PrePersist
    public void prePersist() {
        if (occurredAt == null) occurredAt = Instant.now();
    }
}
