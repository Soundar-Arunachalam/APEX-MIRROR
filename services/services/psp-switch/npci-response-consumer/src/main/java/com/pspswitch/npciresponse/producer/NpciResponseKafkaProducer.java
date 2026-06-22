package com.pspswitch.npciresponse.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pspswitch.npciresponse.model.NpciInboundResponseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes {@link NpciInboundResponseEvent} to the Kafka topic
 * {@code npci.inbound.response}.
 *
 * <p>Uses txnId as the partition key to guarantee message ordering per transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NpciResponseKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.inbound-response}")
    private String inboundResponseTopic;

    /**
     * Publishes an NPCI inbound response event to Kafka.
     *
     * @param event the structured response event
     */
    public void publish(NpciInboundResponseEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(inboundResponseTopic, event.getTxnId(), json);
            log.info("[NPCI-RESPONSE-PRODUCER] Published | topic={} | txnId={} | type={} | result={}",
                    inboundResponseTopic, event.getTxnId(), event.getType(), event.getResult());
        } catch (Exception e) {
            log.error("[NPCI-RESPONSE-PRODUCER] Failed to publish | txnId={} | error={}",
                    event.getTxnId(), e.getMessage(), e);
            throw new RuntimeException("Failed to publish NPCI response event", e);
        }
    }
}
