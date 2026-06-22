package com.pspswitch.tpapegress.model.entity;

import com.pspswitch.tpapegress.model.event.EventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for the webhook_configs table.
 * Maps each (tpapId, eventType) to a webhook URL.
 * Config rows are seeded via DB migration in v1.
 *
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "webhook_configs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tpap_id", "event_type"}))
public class WebhookConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tpap_id", nullable = false, length = 50)
    private String tpapId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    /** 
     * Determines if URL delivery is active. 
     * If false, delivery is skipped and returns silently without throwing (see ADR-003). 
     */
    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
