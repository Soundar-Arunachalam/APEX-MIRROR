package com.pspswitch.orchestrator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pspswitch.orchestrator.model.NpciOutboundRequestEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes {@link NpciOutboundRequestEvent} to the Kafka topic
 * {@code npci.outbound.request}, consumed by the npci-adapter service.
 *
 * <p>This replaces the in-process {@code NpciAdapter.forward()} mock for
 * production deployments. The npci-adapter picks up the event, builds and
 * signs the UPI XML, and POSTs it to NPCI.
 */
@Service
public class NpciKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(NpciKafkaPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic.npci-outbound-request}")
    private String npciOutboundTopic;

    public NpciKafkaPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes an outbound NPCI request event to Kafka.
     * Uses txnId as the partition key to ensure ordering per transaction.
     *
     * @param event the outbound request event to publish
     */
    public void publish(NpciOutboundRequestEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(npciOutboundTopic, event.getTxnId(), json);
            log.info("[NPCI-PUBLISHER] Published to {} | txnId={} | type={}",
                    npciOutboundTopic, event.getTxnId(), event.getType());
        } catch (Exception e) {
            log.error("[NPCI-PUBLISHER] Failed to publish | txnId={} | error={}",
                    event.getTxnId(), e.getMessage(), e);
            throw new RuntimeException("Failed to publish NPCI outbound event to Kafka", e);
        }
    }
}
