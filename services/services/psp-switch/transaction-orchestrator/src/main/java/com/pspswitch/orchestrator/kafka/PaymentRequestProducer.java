package com.pspswitch.orchestrator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pspswitch.orchestrator.model.UpiPaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Kafka Producer — publishes test payment requests to the topic.
 *
 * Used for demo/testing when the Ingress Service is not running.
 * Exposed via ControlController endpoint:
 * POST /api/v1/control/kafka-publish-test
 *
 * In production, the Ingress Service publishes to this topic after
 * security validation (ECDSA, JWT, anti-replay).
 */
@Service
public class PaymentRequestProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic.payment-requests}")
    private String topicName;

    public PaymentRequestProducer(KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a UpiPaymentRequest to the Kafka topic.
     * Uses tr (transaction reference) as the message key for partition ordering.
     */
    public void publish(UpiPaymentRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
            kafkaTemplate.send(topicName, request.getTr(), json);

            log.info("[KAFKA_PRODUCER] Published to {} | tr={} | pa={} | am={}",
                    topicName, request.getTr(), request.getPa(), request.getAm());

        } catch (Exception e) {
            log.error("[KAFKA_PRODUCER] Failed to publish: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to publish to Kafka", e);
        }
    }
}
