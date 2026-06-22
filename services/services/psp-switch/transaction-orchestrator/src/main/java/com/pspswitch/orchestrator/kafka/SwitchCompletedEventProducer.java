package com.pspswitch.orchestrator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pspswitch.orchestrator.model.SwitchCompletedEvent;
import com.pspswitch.orchestrator.model.TransactionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes {@link SwitchCompletedEvent} to the Kafka topic
 * {@code psp.switch.completed.events}, consumed by the tpap-egress-service
 * which dispatches typed webhooks to registered TPAP endpoints.
 *
 * <p>This replaces the direct HTTP webhook call in {@code NotificationService}
 * with a reliable Kafka-based notification pattern.
 */
@Service
public class SwitchCompletedEventProducer {

    private static final Logger log = LoggerFactory.getLogger(SwitchCompletedEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic.switch-completed}")
    private String switchCompletedTopic;

    public SwitchCompletedEventProducer(KafkaTemplate<String, String> kafkaTemplate,
                                         ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a payment completion event to tpap-egress.
     * Uses txnId as partition key for ordering.
     */
    public void publishPaymentResult(TransactionContext ctx) {
        publish(SwitchCompletedEvent.forPayment(ctx), ctx.getTid());
    }

    /**
     * Publishes a balance enquiry result event to tpap-egress.
     */
    public void publishBalanceResult(TransactionContext ctx, String balance, String currency) {
        publish(SwitchCompletedEvent.forBalance(ctx, balance, currency), ctx.getTid());
    }

    public void publish(SwitchCompletedEvent event, String txnId) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(switchCompletedTopic, txnId, json);
            log.info("[SWITCH-PRODUCER] Published {} to {} | txnId={}",
                    event.getEventType(), switchCompletedTopic, event.getTxnId());
        } catch (Exception e) {
            log.error("[SWITCH-PRODUCER] Failed to publish | txnId={} | error={}",
                    txnId, e.getMessage(), e);
        }
    }

}
