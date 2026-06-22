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
 * CBS Credit Service — writes to CREDIT database (cbs_credit).
 *
 * Responsible for:
 *  - Crediting payee's account after NPCI confirms debit was successful
 *  - Confirming credit success back to NPCI via Kafka
 *
 * Note: If credit fails (bank down, timeout, etc.), NPCI detects this and
 * initiates an auto-reversal via CbsDebitService to credit payer back.
 */
@Service
public class CbsCreditService {

    private final JdbcTemplate creditJdbc;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public CbsCreditService(@Qualifier("creditJdbc") JdbcTemplate creditJdbc,
                            KafkaTemplate<String, String> kafkaTemplate) {
        this.creditJdbc = creditJdbc;
        this.kafkaTemplate = kafkaTemplate;
    }

    public void processCredit(Map<String, Object> txn) {
        String txnId = (String) txn.get("txnId");
        String payeeVpa = (String) txn.get("payeeVpa");
        String payeeBank = (String) txn.get("payeeBank");
        Object amount = txn.get("amount");

        System.out.println("[CBS-CREDIT] Processing credit for txn: " + txnId + " | payee: " + payeeVpa + " | amount: " + amount);

        try {
            // Write credit record to CREDIT DB
            creditJdbc.update(
                    "INSERT INTO credit_ledger (txn_id, rrn, payee_vpa, payee_bank, amount, status, created_at) " +
                            "VALUES (?, ?, ?, ?, ?::numeric, 'CREDITED', NOW()) " +
                            "ON CONFLICT (txn_id) DO UPDATE SET status='CREDITED', updated_at=NOW()",
                    txnId, txn.get("rrn"), payeeVpa, payeeBank, amount.toString()
            );

            // Add balance to payee account
            creditJdbc.update(
                    "UPDATE accounts SET balance = balance + ?::numeric, updated_at=NOW() WHERE vpa=?",
                    amount.toString(), payeeVpa
            );

            System.out.println("[CBS-CREDIT] ✓ Credit recorded in cbs_credit DB for: " + txnId);

            // Publish credit confirmation to NPCI
            txn.put("status", "CREDIT_SUCCESS");
            txn.put("creditConfirmedAt", Instant.now().toString());
            kafkaTemplate.send("upi.cbs.credit.confirm", txnId, mapper.writeValueAsString(txn));

            publishDashboardEvent(txnId, txn, "CBS CREDIT DB: Payee account credited successfully");

        } catch (Exception e) {
            System.err.println("[CBS-CREDIT] Credit failed for " + txnId + ": " + e.getMessage());
            // Do NOT publish failure — NPCI will detect timeout and initiate reversal
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
            event.put("dbSource", "CBS_CREDIT_DB");
            event.put("timestamp", Instant.now().toString());
            kafkaTemplate.send("upi.dashboard.events", txnId, event.toString());
        } catch (Exception e) {
            System.err.println("[CBS-CREDIT] Dashboard event error: " + e.getMessage());
        }
    }
}