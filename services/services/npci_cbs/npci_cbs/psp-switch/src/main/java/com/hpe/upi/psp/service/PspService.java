package com.hpe.upi.psp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hpe.upi.psp.model.Transaction;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class PspService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Deque<Transaction> recentTransactions = new ConcurrentLinkedDeque<>();

    public PspService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public Transaction initiateTransaction(String txnId, String payerVpa, String payeeVpa,
                                           String payerBank, String payeeBank, BigDecimal amount,
                                           boolean simulateFailure) {
        Transaction txn = new Transaction();
        txn.setTxnId(txnId);
        txn.setRrn("RRN" + txnId.substring(3, 11));
        txn.setPayerVpa(payerVpa);
        txn.setPayeeVpa(payeeVpa);
        txn.setPayerBank(payerBank);
        txn.setPayeeBank(payeeBank);
        txn.setAmount(amount);
        txn.setStatus("INITIATED");
        txn.setCreatedAt(Instant.now());
        txn.setUpdatedAt(Instant.now());
        txn.setRetryCount(0);
        txn.setReversalInitiated(false);

        if (simulateFailure) {
            txn.setFailureReason("SIMULATED_TIMEOUT");
        }

        recentTransactions.addFirst(txn);
        if (recentTransactions.size() > 50) recentTransactions.pollLast();

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("txnId", txn.getTxnId());
            payload.put("rrn", txn.getRrn());
            payload.put("payerVpa", txn.getPayerVpa());
            payload.put("payeeVpa", txn.getPayeeVpa());
            payload.put("payerBank", txn.getPayerBank());
            payload.put("payeeBank", txn.getPayeeBank());
            payload.put("amount", txn.getAmount());
            payload.put("status", txn.getStatus());
            payload.put("createdAt", txn.getCreatedAt().toString());
            payload.put("updatedAt", txn.getUpdatedAt().toString());
            payload.put("retryCount", txn.getRetryCount());
            payload.put("simulateFailure", simulateFailure);
            payload.put("failureReason", txn.getFailureReason());

            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send("upi.transactions.initiated", txnId, json);
            System.out.println("[PSP] Published transaction to Kafka: " + txnId);
        } catch (Exception e) {
            System.err.println("[PSP] Failed to publish: " + e.getMessage());
        }

        return txn;
    }

    public List<Transaction> getRecentTransactions() {
        return new ArrayList<>(recentTransactions);
    }
}
