package com.hpe.upi.npci.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hpe.upi.npci.service.NpciRoutingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NpciKafkaConsumer {

    private final NpciRoutingService routingService;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public NpciKafkaConsumer(NpciRoutingService routingService) {
        this.routingService = routingService;
    }

    @KafkaListener(topics = "upi.transactions.initiated", groupId = "npci-router")
    public void onTransactionInitiated(String message) {
        try {
            Map<String, Object> txn = mapper.readValue(message, new TypeReference<>() {});
            routingService.handleIncomingTransaction(txn);
        } catch (Exception e) {
            System.err.println("[NPCI] Error processing initiated transaction: " + e.getMessage());
        }
    }

    @KafkaListener(topics = "upi.cbs.debit.confirm", groupId = "npci-router")
    public void onDebitConfirmed(String message) {
        try {
            Map<String, Object> txn = mapper.readValue(message, new TypeReference<>() {});
            routingService.handleDebitConfirmation(txn);
        } catch (Exception e) {
            System.err.println("[NPCI] Error processing debit confirmation: " + e.getMessage());
        }
    }

    @KafkaListener(topics = "upi.cbs.credit.confirm", groupId = "npci-router")
    public void onCreditConfirmed(String message) {
        try {
            Map<String, Object> txn = mapper.readValue(message, new TypeReference<>() {});
            routingService.handleCreditConfirmation(txn);
        } catch (Exception e) {
            System.err.println("[NPCI] Error processing credit confirmation: " + e.getMessage());
        }
    }

    @KafkaListener(topics = "upi.cbs.reversal.confirm", groupId = "npci-router")
    public void onReversalConfirmed(String message) {
        try {
            Map<String, Object> txn = mapper.readValue(message, new TypeReference<>() {});
            routingService.handleReversalConfirmation(txn);
        } catch (Exception e) {
            System.err.println("[NPCI] Error processing reversal confirmation: " + e.getMessage());
        }
    }
}
