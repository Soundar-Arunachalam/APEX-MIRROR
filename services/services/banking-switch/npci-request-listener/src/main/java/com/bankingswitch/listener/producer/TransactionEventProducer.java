package com.bankingswitch.listener.producer;

import com.bankingswitch.listener.model.InboundTransactionEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.inbound-txn}")
    private String topic;

    public void sendEvent(InboundTransactionEvent event) {
        log.info("Sending InboundTransactionEvent to Kafka: {}", event.getTxnId());
        kafkaTemplate.send(topic, event.getTxnId(), event);
    }
}
