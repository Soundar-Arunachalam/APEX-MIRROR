package com.bankingswitch.orchestrator.consumer;

import com.bankingswitch.orchestrator.model.CbsResponseEvent;
import com.bankingswitch.orchestrator.service.SagaOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CbsResponseConsumer {

    private final SagaOrchestratorService sagaOrchestratorService;

    @KafkaListener(topics = "${kafka.topic.cbs-response}", groupId = "bank-orchestrator-group")
    public void consume(CbsResponseEvent event) {
        log.info("Consumed CbsResponseEvent: {}", event.getTxnId());
        sagaOrchestratorService.processCbsResponse(event);
    }
}
