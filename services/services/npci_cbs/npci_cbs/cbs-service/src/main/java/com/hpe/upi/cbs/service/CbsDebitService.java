package com.hpe.upi.cbs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * CBS Debit Service — writes to DEBIT database (cbs_debit).
 *
 * Responsible for:
 *  - Debiting payer's account when NPCI routes a new transaction
 *  - Confirming debit success back to NPCI via Kafka
 *  - Recording reversal credits (money returned to payer) in the debit DB
 */
@Service
public class CbsDebitService {

    private final JdbcTemplate debitJdbc;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public CbsDebitService(@Qualifier("debitJdbc") JdbcTemplate debitJdbc,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.debitJdbc = debitJdbc;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void processDebit(Map<String, Object> txn) {
        String txnId = (String) txn.get("txnId");
        String payerVpa = (String) txn.get("payerVpa");
        String payerBank = (String) txn.get("payerBank");
        Object amount = txn.get("amount");

        System.out.println("[CBS-DEBIT] Processing debit for txn: " + txnId + " | payer: " + payerVpa + " | amount: " + amount);

        try {
            // Deduct balance from payer account
            debitJdbc.update(
                    "UPDATE accounts SET balance = balance - ?::numeric, updated_at=NOW() WHERE vpa=?",
                    amount.toString(), payerVpa
            );

            // Write debit record to DEBIT DB
            debitJdbc.update(
                    "INSERT INTO debit_ledger (txn_id, rrn, payer_vpa, payer_bank, amount, status, created_at) " +
                            "VALUES (?, ?, ?, ?, ?::numeric, 'DEBITED', NOW()) " +
                            "ON CONFLICT (txn_id) DO UPDATE SET status='DEBITED', updated_at=NOW()",
                    txnId, txn.get("rrn"), payerVpa, payerBank, amount.toString()
            );

            System.out.println("[CBS-DEBIT] ✓ Debit recorded in cbs_debit DB for: " + txnId);

            // Publish debit confirmation back to NPCI
            txn.put("status", "DEBIT_SUCCESS");
            txn.put("debitConfirmedAt", Instant.now().toString());
            kafkaTemplate.send("upi.cbs.debit.confirm", txnId, mapper.writeValueAsString(txn));

            // Publish dashboard event
            publishDashboardEvent(txnId, txn, "CBS DEBIT DB: Payer account debited successfully");

        } catch (Exception e) {
            System.err.println("[CBS-DEBIT] Debit failed for " + txnId + ": " + e.getMessage());
            try {
                txn.put("status", "DEBIT_FAILED");
                txn.put("failureReason", e.getMessage());
                kafkaTemplate.send("upi.transactions.status", txnId, mapper.writeValueAsString(txn));
            } catch (Exception ex) {
                System.err.println("[CBS-DEBIT] Failed to publish failure status: " + ex.getMessage());
            }
        }
    }

    /**
     * Reversal: credit payer back in DEBIT DB.
     * NPCI initiates this after detecting credit bank failure.
     * CBS credits money back to payer's account.
     */
    public void processReversal(Map<String, Object> txn) {
        String txnId = (String) txn.get("txnId");
        String payerVpa = (String) txn.get("payerVpa");
        Object amount = txn.get("amount");
        String reversalReason = (String) txn.getOrDefault("reversalReason", "NPCI auto-reversal");

        System.out.println("[CBS-DEBIT] REVERSAL: Crediting payer back for txn: " + txnId);

        try {
            // Update debit record — mark as reversed (payer gets money back)
            debitJdbc.update(
                    "UPDATE debit_ledger SET status='REVERSED', reversal_reason=?, reversed_at=NOW(), updated_at=NOW() WHERE txn_id=?",
                    reversalReason, txnId
            );

            // Restore balance to payer account
            debitJdbc.update(
                    "UPDATE accounts SET balance = balance + ?::numeric, updated_at=NOW() WHERE vpa=?",
                    amount.toString(), payerVpa
            );

            // Insert reversal credit record
            debitJdbc.update(
                    "INSERT INTO reversal_ledger (txn_id, payer_vpa, amount, reason, reversed_at) " +
                            "VALUES (?, ?, ?::numeric, ?, NOW()) ON CONFLICT (txn_id) DO NOTHING",
                    txnId, payerVpa, amount.toString(), reversalReason
            );

            System.out.println("[CBS-DEBIT] ✓ Reversal credit recorded in cbs_debit DB for: " + txnId);

            txn.put("status", "REVERSED");
            txn.put("reversalCompletedAt", Instant.now().toString());
            kafkaTemplate.send("upi.cbs.reversal.confirm", txnId, mapper.writeValueAsString(txn));

            publishDashboardEvent(txnId, txn, "CBS DEBIT DB: Payer account credited back (auto-reversal complete)");

        } catch (Exception e) {
            System.err.println("[CBS-DEBIT] Reversal failed for " + txnId + ": " + e.getMessage());
        }
    }

    private void publishDashboardEvent(String txnId, Map<String, Object> txn, String message) {
        try {
            ObjectNode event = mapper.createObjectNode();
            event.put("txnId", txnId);
            event.put("status", txn.getOrDefault("status", "UNKNOWN").toString());
            event.put("payerVpa", txn.getOrDefault("payerVpa", "").toString());
            event.put("payeeVpa", txn.getOrDefault("payeeVpa", "").toString());
            event.put("payerBank", txn.getOrDefault("payerBank", "").toString());
            event.put("payeeBank", txn.getOrDefault("payeeBank", "").toString());
            event.put("amount", txn.getOrDefault("amount", "0").toString());
            event.put("rrn", txn.getOrDefault("rrn", "").toString());
            event.put("message", message);
            event.put("dbSource", "CBS_DEBIT_DB");
            event.put("timestamp", Instant.now().toString());
            if (txn.get("reversalReason") != null) event.put("reversalReason", txn.get("reversalReason").toString());
            kafkaTemplate.send("upi.dashboard.events", txnId, event.toString());
        } catch (Exception e) {
            System.err.println("[CBS-DEBIT] Dashboard event error: " + e.getMessage());
        }
    }
}