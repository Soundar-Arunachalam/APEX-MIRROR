package com.hpe.upi.npci.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * NPCI Router — Central UPI switching engine.
 *
 * Responsibilities:
 *  1. Consume initiated transactions from PSP
 *  2. Route to CBS for debit/credit
 *  3. DETECT failed/timed-out transactions
 *  4. INITIATE auto-reversal requests (credit-back to payer via CBS)
 *  5. Publish status updates to dashboard
 */
@Service
public class NpciRoutingService {

    private static final long TIMEOUT_MS = 15_000; // 15 seconds timeout for demo
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    // In-flight transactions: txnId -> transaction state map
    private final Map<String, Map<String, Object>> inFlight = new ConcurrentHashMap<>();
    private final Deque<Map<String, Object>> processedHistory = new ConcurrentLinkedDeque<>();

    public NpciRoutingService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Called by Kafka consumer when PSP publishes a new transaction.
     * NPCI validates, routes, and tracks it.
     */
    public void handleIncomingTransaction(Map<String, Object> txn) {
        String txnId = (String) txn.get("txnId");
        boolean simulateFailure = Boolean.TRUE.equals(txn.get("simulateFailure"));

        System.out.println("[NPCI] Received transaction: " + txnId + " | simulateFailure=" + simulateFailure);

        // Update status to ROUTING
        txn.put("status", "ROUTING");
        txn.put("npciReceivedAt", Instant.now().toString());
        txn.put("simulateFailure", simulateFailure);

        // Track for timeout monitoring
        inFlight.put(txnId, txn);

        publishDashboardEvent(txn, "NPCI received and routing transaction");

        if (simulateFailure) {
            // Simulate a failure: mark debit as success but credit times out
            System.out.println("[NPCI] Simulating credit failure for: " + txnId);
            txn.put("status", "DEBIT_SUCCESS");
            publishToKafka("upi.cbs.debit", txnId, txn);
            // Credit will "fail" — detected by timeout scheduler
        } else {
            // Normal happy path: route to CBS for debit first
            txn.put("status", "DEBIT_PENDING");
            publishToKafka("upi.cbs.debit", txnId, txn);
        }
    }

    /**
     * Called when CBS confirms debit success.
     * NPCI then routes to CBS credit operation.
     */
    public void handleDebitConfirmation(Map<String, Object> txn) {
        String txnId = (String) txn.get("txnId");
        boolean simulateFailure = Boolean.TRUE.equals(txn.get("simulateFailure"));
        System.out.println("[NPCI] Debit confirmed for: " + txnId);

        if (simulateFailure) {
            // Simulate credit bank timeout — don't route to credit, let timeout kick in
            System.out.println("[NPCI] Simulating credit bank DOWN — will trigger auto-reversal");
            txn.put("status", "CREDIT_PENDING");
            txn.put("creditBankStatus", "TIMEOUT_SIMULATED");
            inFlight.put(txnId, txn);
            publishDashboardEvent(txn, "Credit bank timeout — NPCI monitoring for reversal");
        } else {
            // Normal: route to CBS credit
            txn.put("status", "CREDIT_PENDING");
            publishToKafka("upi.cbs.credit", txnId, txn);
        }
    }

    /**
     * Called when CBS confirms credit success — transaction complete.
     */
    public void handleCreditConfirmation(Map<String, Object> txn) {
        String txnId = (String) txn.get("txnId");
        System.out.println("[NPCI] Credit confirmed — transaction SUCCESS: " + txnId);

        txn.put("status", "SUCCESS");
        txn.put("completedAt", Instant.now().toString());
        inFlight.remove(txnId);

        archiveTransaction(txn);
        publishDashboardEvent(txn, "Transaction completed successfully");
        publishToKafka("upi.transactions.status", txnId, txn);
    }

    /**
     * NPCI Auto-Reversal Scheduler.
     *
     * Runs every 5 seconds. Checks all in-flight transactions.
     * If CREDIT_PENDING for more than TIMEOUT_MS:
     *   → NPCI detects failure
     *   → NPCI sends reversal request to CBS (credit back to payer)
     */
    @Scheduled(fixedDelay = 5000)
    public void detectAndInitiateReversal() {
        Instant now = Instant.now();

        for (Map.Entry<String, Map<String, Object>> entry : inFlight.entrySet()) {
            String txnId = entry.getKey();
            Map<String, Object> txn = entry.getValue();
            String status = (String) txn.get("status");

            if (!"CREDIT_PENDING".equals(status)) continue;
            if (Boolean.TRUE.equals(txn.get("reversalInitiated"))) continue;

            String receivedAt = (String) txn.get("npciReceivedAt");
            if (receivedAt == null) continue;

            long elapsedMs = now.toEpochMilli() - Instant.parse(receivedAt).toEpochMilli();

            if (elapsedMs > TIMEOUT_MS) {
                System.out.println("[NPCI] ⚠ FAILURE DETECTED for " + txnId + " — elapsed: " + elapsedMs + "ms");
                System.out.println("[NPCI] → Initiating AUTO-REVERSAL: crediting money back to payer via CBS");

                txn.put("status", "REVERSAL_INITIATED");
                txn.put("reversalInitiated", true);
                txn.put("reversalReason", "Credit bank timed out after " + elapsedMs + "ms — NPCI auto-reversal");
                txn.put("reversalInitiatedAt", now.toString());

                inFlight.put(txnId, txn);

                publishDashboardEvent(txn, "NPCI detected failure — initiating auto-reversal to credit payer back");

                // NPCI sends reversal request to CBS — CBS will credit payer's account
                publishToKafka("upi.cbs.reversal", txnId, txn);
            }
        }
    }

    /**
     * Called when CBS completes the reversal credit to payer.
     */
    public void handleReversalConfirmation(Map<String, Object> txn) {
        String txnId = (String) txn.get("txnId");
        System.out.println("[NPCI] ✓ Reversal complete for: " + txnId + " — payer credited back");

        txn.put("status", "REVERSED");
        txn.put("reversalCompletedAt", Instant.now().toString());
        inFlight.remove(txnId);

        archiveTransaction(txn);
        publishDashboardEvent(txn, "Auto-reversal complete — payer account credited back by CBS");
        publishToKafka("upi.transactions.status", txnId, txn);
    }

    private void publishToKafka(String topic, String key, Map<String, Object> payload) {
        try {
            kafkaTemplate.send(topic, key, mapper.writeValueAsString(payload));
        } catch (Exception e) {
            System.err.println("[NPCI] Kafka publish error: " + e.getMessage());
        }
    }

    private void publishDashboardEvent(Map<String, Object> txn, String message) {
        try {
            ObjectNode event = mapper.createObjectNode();
            event.put("txnId", (String) txn.get("txnId"));
            event.put("rrn", txn.getOrDefault("rrn", "").toString());
            event.put("payerVpa", txn.getOrDefault("payerVpa", "").toString());
            event.put("payeeVpa", txn.getOrDefault("payeeVpa", "").toString());
            event.put("payerBank", txn.getOrDefault("payerBank", "").toString());
            event.put("payeeBank", txn.getOrDefault("payeeBank", "").toString());
            event.put("amount", txn.getOrDefault("amount", "0").toString());
            event.put("status", txn.getOrDefault("status", "UNKNOWN").toString());
            event.put("message", message);
            event.put("timestamp", Instant.now().toString());
            event.put("reversalInitiated", Boolean.TRUE.equals(txn.get("reversalInitiated")));
            if (txn.get("reversalReason") != null) event.put("reversalReason", txn.get("reversalReason").toString());
            kafkaTemplate.send("upi.dashboard.events", (String) txn.get("txnId"), event.toString());
        } catch (Exception e) {
            System.err.println("[NPCI] Dashboard event error: " + e.getMessage());
        }
    }

    private void archiveTransaction(Map<String, Object> txn) {
        processedHistory.addFirst(new HashMap<>(txn));
        if (processedHistory.size() > 100) processedHistory.pollLast();
    }

    public List<Map<String, Object>> getInFlight() { return new ArrayList<>(inFlight.values()); }
    public List<Map<String, Object>> getHistory() { return new ArrayList<>(processedHistory); }
}
