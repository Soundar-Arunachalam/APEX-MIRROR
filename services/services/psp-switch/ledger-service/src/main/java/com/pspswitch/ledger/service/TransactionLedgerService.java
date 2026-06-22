package com.pspswitch.ledger.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pspswitch.ledger.entity.LedgerEntry;
import com.pspswitch.ledger.entity.TxnStatusEvent;
import com.pspswitch.ledger.entity.UpiTransaction;
import com.pspswitch.ledger.repository.LedgerEntryRepository;
import com.pspswitch.ledger.repository.TxnStatusEventRepository;
import com.pspswitch.ledger.repository.UpiTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Core transactional service for recording ledger entries and state changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionLedgerService {

    private final UpiTransactionRepository txnRepository;
    private final TxnStatusEventRepository eventRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final DataCryptoService cryptoService;
    private final ObjectMapper objectMapper;

    /**
     * Called when a switch completed event is received from the orchestrator.
     */
    @Transactional
    public void recordSwitchCompletedEvent(String jsonPayload) {
        try {
            Map<String, Object> payload = objectMapper.readValue(jsonPayload, new TypeReference<>() {});
            String tid = (String) payload.get("txnId");
            String type = (String) payload.get("eventType");

            Map<String, Object> innerPayload = (Map<String, Object>) payload.get("payload");
            String status = innerPayload != null ? (String) innerPayload.get("status") : "UNKNOWN";
            String rrn = innerPayload != null ? (String) innerPayload.get("approvalRefNo") : null;

            log.info("[LEDGER] Processing SwitchCompletedEvent | tid={} | type={} | status={}", tid, type, status);

            if (!"PAYMENT_PUSH".equalsIgnoreCase(type)) {
                // We only do double-entry for financial PAY transactions
                recordStatusEventOnly(tid, status, "psp.switch.completed.events", payload);
                return;
            }

            if (innerPayload == null) {
                log.warn("[LEDGER] Missing payload for PAYMENT_PUSH txn | tid={}", tid);
                return;
            }

            // 1. Upsert UpiTransaction
            UpiTransaction txn = upsertTransaction(tid, status, innerPayload, rrn);

            // 2. Record Status Event
            TxnStatusEvent statusEvent = TxnStatusEvent.builder()
                    .tid(tid)
                    .fromStatus(txn.getStatus())
                    .toStatus(status)
                    .sourceService("transaction-orchestrator")
                    .kafkaTopic("psp.switch.completed.events")
                    .eventPayload(payload)
                    .build();
            eventRepository.save(statusEvent);

            // 3. Update status
            txn.setStatus(status);
            if ("SUCCESS".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
                txn.setCompletedAt(Instant.now());
            }

            // 4. Double-Entry Bookkeeping (if SUCCESS)
            if ("SUCCESS".equalsIgnoreCase(status)) {
                recordDoubleEntry(txn);
            }

        } catch (Exception e) {
            log.error("[LEDGER] Failed to process SwitchCompletedEvent", e);
            throw new RuntimeException("Ledger processing failed", e);
        }
    }

    /**
     * Called when a raw NPCI response is received (just an audit log, no ledgering).
     */
    @Transactional
    public void recordNpciResponseEvent(String jsonPayload) {
        try {
            Map<String, Object> payload = objectMapper.readValue(jsonPayload, new TypeReference<>() {});
            String tid = (String) payload.get("txnId");
            String result = (String) payload.get("result");

            log.info("[LEDGER] Recording NPCI Response Event | tid={} | result={}", tid, result);
            recordStatusEventOnly(tid, "NPCI_" + result, "npci.inbound.response", payload);

        } catch (Exception e) {
            log.error("[LEDGER] Failed to record NPCI response", e);
        }
    }

    private void recordStatusEventOnly(String tid, String status, String topic, Map<String, Object> payload) {
        TxnStatusEvent event = TxnStatusEvent.builder()
                .tid(tid)
                .toStatus(status)
                .sourceService("npci-response-consumer")
                .kafkaTopic(topic)
                .eventPayload(payload)
                .build();
        eventRepository.save(event);
    }

    private UpiTransaction upsertTransaction(String tid, String status, Map<String, Object> payData, String rrn) {
        Optional<UpiTransaction> existing = txnRepository.findByTid(tid);

        if (existing.isPresent()) {
            UpiTransaction txn = existing.get();
            if (rrn != null) txn.setRrn(rrn);
            return txn;
        }

        // Create new
        BigDecimal amt = new BigDecimal(payData.getOrDefault("amount", "0").toString());
        String payerVpa = (String) payData.getOrDefault("payerVpa", "UNKNOWN");
        String payeeVpa = (String) payData.getOrDefault("payeeVpa", "UNKNOWN");

        UpiTransaction newTxn = UpiTransaction.builder()
                .tid(tid)
                .txnRefId((String) payData.getOrDefault("txnRefId", tid))
                .rrn(rrn)
                .payerVpa(cryptoService.encrypt(payerVpa))
                .payeeVpa(cryptoService.encrypt(payeeVpa))
                .pspId((String) payData.getOrDefault("pspId", "DEMOPSP"))
                .amount(amt)
                .currency("INR")
                .txnType("PAY")
                .flowDirection("SEND")
                .status("PENDING")
                .build();

        return txnRepository.save(newTxn);
    }

    private void recordDoubleEntry(UpiTransaction txn) {
        // Idempotency check: Did we already record ledger entries for this SUCCESS?
        var existingEntries = ledgerRepository.findByTid(txn.getTid());
        if (!existingEntries.isEmpty()) {
            log.info("[LEDGER] Entries already exist for tid={} | skipping", txn.getTid());
            return;
        }

        LocalDate today = LocalDate.now();

        // 1. DEBIT the Payer
        LedgerEntry debit = LedgerEntry.builder()
                .tid(txn.getTid())
                .entryType("DEBIT")
                .accountVpa(txn.getPayerVpa()) // already encrypted
                .amount(txn.getAmount())
                .currency(txn.getCurrency())
                .rrn(txn.getRrn())
                .settlementDate(today)
                .build();

        // 2. CREDIT the Payee
        LedgerEntry credit = LedgerEntry.builder()
                .tid(txn.getTid())
                .entryType("CREDIT")
                .accountVpa(txn.getPayeeVpa()) // already encrypted
                .amount(txn.getAmount())
                .currency(txn.getCurrency())
                .rrn(txn.getRrn())
                .settlementDate(today)
                .build();

        ledgerRepository.save(debit);
        ledgerRepository.save(credit);

        log.info("[LEDGER] Recorded DOUBLE-ENTRY | tid={} | amt={}", txn.getTid(), txn.getAmount());
    }
}
