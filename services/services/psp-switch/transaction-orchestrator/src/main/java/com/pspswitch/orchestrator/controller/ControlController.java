package com.pspswitch.orchestrator.controller;

import com.pspswitch.orchestrator.adapter.CbsAdapter;
import com.pspswitch.orchestrator.adapter.LedgerService;
import com.pspswitch.orchestrator.adapter.NpciAdapter;
import com.pspswitch.orchestrator.kafka.PaymentRequestProducer;
import com.pspswitch.orchestrator.model.TransactionState;
import com.pspswitch.orchestrator.model.UpiPaymentRequest;
import com.pspswitch.orchestrator.service.DataCryptoService;
import com.pspswitch.orchestrator.service.IdempotencyService;
import com.pspswitch.orchestrator.service.ReconciliationService;
import com.pspswitch.orchestrator.service.TransactionStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Control Controller — demo toggles, Kafka test publishing, and manual
 * reconciliation.
 *
 * Endpoints:
 * POST /api/v1/control/npci-failure?enabled=true|false → Toggle NPCI failure
 * mode
 * POST /api/v1/control/cbs-failure?enabled=true|false → Toggle CBS failure mode
 * (COLLECT only)
 * POST /api/v1/control/npci-timeout?enabled=true|false → Suppress NPCI webhook
 * POST /api/v1/control/kafka-publish-test → Publish test message to Kafka
 * GET /api/v1/control/reconcile-now → Trigger reconciliation manually
 * GET /api/v1/control/status → Show all toggles + txn counts
 */
@RestController
@RequestMapping("/api/v1/control")
public class ControlController {

    private static final Logger log = LoggerFactory.getLogger(ControlController.class);

    private final NpciAdapter npciAdapter;
    private final CbsAdapter cbsAdapter;
    private final TransactionStateService stateService;
    private final IdempotencyService idempotencyService;
    private final LedgerService ledgerService;
    private final PaymentRequestProducer kafkaProducer;
    private final ReconciliationService reconciliationService;
    private final DataCryptoService dataCryptoService;

    public ControlController(NpciAdapter npciAdapter,
            CbsAdapter cbsAdapter,
            TransactionStateService stateService,
            IdempotencyService idempotencyService,
            LedgerService ledgerService,
            PaymentRequestProducer kafkaProducer,
            ReconciliationService reconciliationService,
            DataCryptoService dataCryptoService) {
        this.npciAdapter = npciAdapter;
        this.cbsAdapter = cbsAdapter;
        this.stateService = stateService;
        this.idempotencyService = idempotencyService;
        this.ledgerService = ledgerService;
        this.kafkaProducer = kafkaProducer;
        this.reconciliationService = reconciliationService;
        this.dataCryptoService = dataCryptoService;
    }

    /**
     * POST /api/v1/control/npci-failure?enabled=true|false
     */
    @PostMapping("/npci-failure")
    public ResponseEntity<Map<String, Object>> toggleNpciFailure(@RequestParam boolean enabled) {
        npciAdapter.setFailureMode(enabled);
        log.info("[CONTROL] NPCI failure mode = {}", enabled);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("npciFailureMode", enabled);
        response.put("message", enabled ? "NPCI will return FAILED for new transactions"
                : "NPCI will return SUCCESS for new transactions");
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/control/cbs-failure?enabled=true|false
     * Only affects COLLECT flow transactions (Receiver Side CBS credit).
     */
    @PostMapping("/cbs-failure")
    public ResponseEntity<Map<String, Object>> toggleCbsFailure(@RequestParam boolean enabled) {
        cbsAdapter.setFailureMode(enabled);
        log.info("[CONTROL] CBS failure mode = {}", enabled);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("cbsFailureMode", enabled);
        response.put("message", enabled ? "CBS will REJECT credits — COLLECT compensation will trigger"
                : "CBS will ACCEPT credits normally");
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/control/npci-timeout?enabled=true|false
     */
    @PostMapping("/npci-timeout")
    public ResponseEntity<Map<String, Object>> toggleNpciTimeout(@RequestParam boolean enabled) {
        npciAdapter.setSuppressWebhook(enabled);
        log.info("[CONTROL] NPCI webhook suppressed = {}", enabled);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("npciWebhookSuppressed", enabled);
        response.put("message", enabled ? "NPCI webhook will NOT fire — transactions will timeout to UNKNOWN"
                : "NPCI webhook will fire normally");
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/control/kafka-publish-test
     * Publishes a test payment request to Kafka topic (for demo without Ingress
     * Service).
     */
    @PostMapping("/kafka-publish-test")
    public ResponseEntity<Map<String, Object>> publishTestToKafka(@RequestBody UpiPaymentRequest request) {
        kafkaProducer.publish(request);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "published");
        response.put("topic", "upi.txn.requests");
        response.put("tr", request.getTr());
        response.put("message", "Test message published to Kafka. Consumer will process it.");
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/control/reconcile-now
     * Manually triggers the reconciliation sweep for demo purposes.
     * Resolves UNKNOWN transactions without waiting for the 60-second scheduler.
     */
    @GetMapping("/reconcile-now")
    public ResponseEntity<Map<String, Object>> reconcileNow() {
        long unknownCount = stateService.countByState(TransactionState.UNKNOWN);
        log.info("[CONTROL] Manual reconciliation triggered | {} UNKNOWN transactions", unknownCount);

        // Run synchronously — blocks until sweep completes (fine for demo)
        reconciliationService.reconcileUnknownTransactions();

        long remainingUnknown = stateService.countByState(TransactionState.UNKNOWN);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Reconciliation triggered manually");
        response.put("unknownBefore", unknownCount);
        response.put("unknownAfter", remainingUnknown);
        response.put("resolved", unknownCount - remainingUnknown);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/control/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        // Toggle states
        Map<String, Object> toggles = new LinkedHashMap<>();
        toggles.put("npciFailureMode", npciAdapter.isFailureMode());
        toggles.put("cbsFailureMode", cbsAdapter.isFailureMode());
        toggles.put("npciWebhookSuppressed", npciAdapter.isSuppressWebhook());
        status.put("toggles", toggles);

        // Transaction counts by state
        Map<String, Long> txnCounts = new LinkedHashMap<>();
        for (TransactionState state : TransactionState.values()) {
            txnCounts.put(state.name(), stateService.countByState(state));
        }
        status.put("transactionCounts", txnCounts);

        // Service sizes
        Map<String, Integer> sizes = new LinkedHashMap<>();
        sizes.put("totalTransactions", stateService.size());
        sizes.put("idempotencyKeys", idempotencyService.size());
        sizes.put("ledgerEntries", ledgerService.size());
        status.put("serviceSizes", sizes);

        // Infrastructure
        Map<String, Object> infra = new LinkedHashMap<>();
        infra.put("database", "PostgreSQL");
        infra.put("cache", "Redis");
        infra.put("messaging", "Apache Kafka");
        infra.put("cryptoEnabled", dataCryptoService.isCryptoEnabled());
        status.put("infrastructure", infra);

        return ResponseEntity.ok(status);
    }
}
