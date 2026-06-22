package com.bankingswitch.orchestrator.producer;

import com.bankingswitch.orchestrator.model.CbsRequestEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CbsRequestProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.cbs-request}")
    private String topic;

    public void sendRequest(CbsRequestEvent event) {
        log.info("Sending CbsRequestEvent to Kafka: {}", event.getTxnId());
        kafkaTemplate.send(topic, event.getTxnId(), event);
    }
}
