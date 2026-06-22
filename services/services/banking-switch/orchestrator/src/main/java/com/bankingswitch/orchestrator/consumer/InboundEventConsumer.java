package com.bankingswitch.orchestrator.consumer;

import com.bankingswitch.orchestrator.model.InboundTransactionEvent;
import com.bankingswitch.orchestrator.service.SagaOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboundEventConsumer {

    private final SagaOrchestratorService sagaOrchestratorService;

    @KafkaListener(topics = "${kafka.topic.inbound-txn}", groupId = "bank-orchestrator-group")
    public void consume(InboundTransactionEvent event) {
        log.info("Consumed InboundTransactionEvent: {}", event.getTxnId());
        sagaOrchestratorService.processInboundEvent(event);
    }
}
