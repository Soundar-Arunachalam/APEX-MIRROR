package com.pspswitch.tpapingress.kafka;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Common Kafka message envelope shared across all topics.
 * Topic-specific payload is embedded in the {@code data} field.
 * See architecture_spec.md Section 3.2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KafkaEventEnvelope {
    private String eventId;
    private String eventType;
    private String tpapId;
    private String txnId;
    private String correlationId;
    private String timestamp;
    private String schemaVersion;
    private Object data;
}
