package com.pspswitch.orchestrator.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pspswitch.orchestrator.orchestrator.TransactionOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class VpaLookupConsumer {

    private static final Logger log = LoggerFactory.getLogger(VpaLookupConsumer.class);

    private final TransactionOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    public VpaLookupConsumer(TransactionOrchestrator orchestrator, ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${app.kafka.topics.vpa-lookup:psp.vpa.lookup.request}", groupId = "psp-orchestrator")
    public void consume(String message) {
        try {
            log.info("[KAFKA_CONSUMER] Received VPA lookup message");
            JsonNode root = objectMapper.readTree(message);
            
            if (root.has("data") && root.has("eventType")) {
                JsonNode data = root.get("data");
                String txnId = data.path("txnId").asText(null);
                String vpa = data.path("requesterVpa").asText(null);
                
                log.info("[KAFKA_CONSUMER] Deserialized VpaLookup | txnId={} | vpa={}", txnId, vpa);
                
                TransactionOrchestrator.OrchestratorResult result = orchestrator.orchestrateVpaLookup(txnId, vpa, null);
                if (result.isDuplicate()) {
                    log.info("[KAFKA_CONSUMER] txnId={} | DUPLICATE | Skipped", txnId);
                } else {
                    log.info("[KAFKA_CONSUMER] txnId={} | ACCEPTED | VPA lookup initiated", txnId);
                }
            }
        } catch (Exception e) {
            log.error("[KAFKA_CONSUMER] Failed to process VPA lookup message: {}", e.getMessage(), e);
        }
    }
}
