package com.psp.npci.adapter.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psp.npci.adapter.model.NpciInboundResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka producer – publishes {@link NpciInboundResponseEvent} to
 * {@code npci.inbound.response} so the Orchestrator and Notification Service
 * can react to NPCI results.
 *
 * <p>
 * The txnId is used as the Kafka message key so that all messages for the
 * same transaction land on the same partition (ordering guarantee).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NpciResponseProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.inbound-response}")
    private String inboundResponseTopic;

    /**
     * Serialises {@code event} to JSON and publishes it as a Kafka message.
     *
     * <p>
     * Key = {@code event.getTxnId()} (partition affinity for ordering).
     * <p>
     * Value = JSON string of {@link NpciInboundResponseEvent}.
     *
     * @param event the response event to publish
     */
    public void publish(NpciInboundResponseEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(inboundResponseTopic, event.getTxnId(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("[NPCI-ADAPTER] Failed to publish Kafka event | txnId={} | error={}",
                                    event.getTxnId(), ex.getMessage(), ex);
                        } else {
                            log.info(
                                    "[NPCI-ADAPTER] Published result to Kafka | txnId={} | result={} | topic={} | partition={} | offset={}",
                                    event.getTxnId(), event.getResult(),
                                    result.getRecordMetadata().topic(),
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("[NPCI-ADAPTER] JSON serialisation failed for txnId={}", event.getTxnId(), e);
            throw new IllegalStateException("Failed to serialise NpciInboundResponseEvent", e);
        }
    }
}
