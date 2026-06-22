package com.pspswitch.ledger.consumer;

import com.pspswitch.ledger.service.TransactionLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NpciResponseEventConsumer {

    private final TransactionLedgerService ledgerService;

    @KafkaListener(topics = "${kafka.topics.npci-inbound-response}", groupId = "ledger-service")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.debug("[KAFKA] Consumed npci-inbound-response | key={}", record.key());
        try {
            ledgerService.recordNpciResponseEvent(record.value());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[KAFKA] Failed to process npci-inbound-response | key={}", record.key(), e);
            // In a real system, you'd send to DLQ here. For now, ack to prevent poison pill.
            ack.acknowledge();
        }
    }
}
