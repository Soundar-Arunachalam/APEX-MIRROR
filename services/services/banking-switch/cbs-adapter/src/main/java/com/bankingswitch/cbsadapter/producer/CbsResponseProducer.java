package com.bankingswitch.cbsadapter.producer;

import com.bankingswitch.cbsadapter.model.CbsResponseEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CbsResponseProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.cbs-response}")
    private String topic;

    public void sendResponse(CbsResponseEvent event) {
        log.info("Sending CbsResponseEvent to Kafka: {}", event.getTxnId());
        kafkaTemplate.send(topic, event.getTxnId(), event);
    }
}
