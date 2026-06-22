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
public class SwitchCompletedEventConsumer {

    private final TransactionLedgerService ledgerService;

    @KafkaListener(topics = "${kafka.topics.switch-completed}", groupId = "ledger-service")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
        log.debug("[KAFKA] Consumed switch-completed | key={}", record.key());
        try {
            ledgerService.recordSwitchCompletedEvent(record.value());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[KAFKA] Failed to process switch-completed | key={}", record.key(), e);
            ack.acknowledge();
        }
    }
}
