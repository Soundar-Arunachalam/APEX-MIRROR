package com.bankingswitch.orchestrator.producer;

import com.bankingswitch.orchestrator.model.NpciResponseEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NpciResponseProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.npci-response}")
    private String topic;

    public void sendResponse(NpciResponseEvent event) {
        log.info("Sending NpciResponseEvent to Kafka: {}", event.getTxnId());
        kafkaTemplate.send(topic, event.getTxnId(), event);
    }
}
