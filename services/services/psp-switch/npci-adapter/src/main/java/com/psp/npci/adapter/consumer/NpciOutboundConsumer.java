package com.psp.npci.adapter.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.psp.npci.adapter.model.NpciOutboundRequestEvent;
import com.psp.npci.adapter.service.NpciAdapterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for topic {@code npci.outbound.request}.
 *
 * <p>
 * The Orchestrator publishes to this topic whenever it wants the Adapter to
 * make an outbound call to NPCI. This class is the entry point for both the
 * PAY pipeline (Flow A) and the BALANCE ENQUIRY pipeline (Flow B).
 *
 * <h2>Error handling</h2>
 * <p>
 * If JSON deserialisation or the adapter service throws an unchecked exception,
 * the default Spring Kafka error handler will log the error and (after
 * configured
 * retries) skip or dead-letter the record. For demo purposes errors are logged
 * and
 * the consumer continues to the next message — no DLQ configured.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NpciOutboundConsumer {

    private final NpciAdapterService npciAdapterService;
    private final ObjectMapper objectMapper;

    /**
     * Receives an {@code NpciOutboundRequestEvent} from Kafka and delegates
     * to {@link NpciAdapterService} based on the {@code type} field.
     *
     * @param message raw JSON string from Kafka
     */
    @KafkaListener(topics = "${kafka.topics.outbound-request}", groupId = "${spring.kafka.consumer.group-id}", concurrency = "3")
    public void consume(String message) {
        log.info("[NPCI-ADAPTER] Kafka message received on npci.outbound.request | payload={}", message);

        NpciOutboundRequestEvent event;
        try {
            event = objectMapper.readValue(message, NpciOutboundRequestEvent.class);
        } catch (Exception e) {
            log.error("[NPCI-ADAPTER] Failed to deserialise Kafka message | payload={} | error={}",
                    message, e.getMessage(), e);
            return; // skip malformed message
        }

        log.info("[NPCI-ADAPTER] Processing event | txnId={} | type={}", event.getTxnId(), event.getType());

        try {
            switch (event.getType().toUpperCase()) {
                case "PAY" -> npciAdapterService.handlePay(event);
                case "BALANCE" -> npciAdapterService.handleBalance(event);
                case "VPA_LOOKUP", "REQ_VPA" -> npciAdapterService.handleVpaLookup(event);
                default -> log.warn("[NPCI-ADAPTER] Unknown event type='{}' | txnId={}",
                        event.getType(), event.getTxnId());
            }
        } catch (Exception e) {
            log.error("[NPCI-ADAPTER] Error processing event | txnId={} | type={} | error={}",
                    event.getTxnId(), event.getType(), e.getMessage(), e);
        }
    }
}
