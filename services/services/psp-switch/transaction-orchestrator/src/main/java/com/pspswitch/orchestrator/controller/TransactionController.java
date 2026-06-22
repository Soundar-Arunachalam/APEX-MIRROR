package com.pspswitch.orchestrator.controller;

import com.pspswitch.orchestrator.model.TransactionContext;
import com.pspswitch.orchestrator.model.TransactionResponse;
import com.pspswitch.orchestrator.model.UpiPaymentRequest;
import com.pspswitch.orchestrator.orchestrator.TransactionOrchestrator;
import com.pspswitch.orchestrator.service.TransactionStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Transaction Controller — primary API for initiating and querying
 * transactions.
 *
 * Endpoints:
 * POST /api/v1/txn → Initiate payment (HTTP 202)
 * GET /api/v1/txn/{txnId} → Get transaction state by tid
 * GET /api/v1/txn/ref?tr={tr}&pa={pa} → Lookup by composite key
 */
@RestController
@RequestMapping("/api/v1/txn")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionOrchestrator orchestrator;
    private final TransactionStateService stateService;

    public TransactionController(TransactionOrchestrator orchestrator,
            TransactionStateService stateService) {
        this.orchestrator = orchestrator;
        this.stateService = stateService;
    }

    /**
     * POST /api/v1/txn — Initiate a UPI payment.
     *
     * Runs Steps 1-5 synchronously:
     * - Idempotency check → duplicate returns HTTP 200 + X-Idempotent-Replayed
     * header
     * - TID generation → correlation ID for all systems
     * - Mode preprocessing → requiresPasscode, flowType
     * - Validation → HTTP 400 on failure
     * - Write PENDING → returns HTTP 202
     *
     * Steps 6-8 execute asynchronously (NPCI webhook → SEND: ledger/finalise,
     * COLLECT: CBS/ledger/finalise).
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> initiateTransaction(@RequestBody UpiPaymentRequest request) {
        TransactionOrchestrator.OrchestratorResult result = orchestrator.orchestrate(request);

        if (result.isDuplicate()) {
            // DUPLICATE — return cached response with idempotency header
            return ResponseEntity.status(HttpStatus.OK)
                    .header("X-Idempotent-Replayed", "true")
                    .body(result.getResponse());
        }

        // NEW — return HTTP 202 Accepted with PENDING state
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(result.getResponse());
    }

    /**
     * GET /api/v1/txn/{txnId} — Get current transaction state by PSP tid.
     */
    @GetMapping("/{txnId}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable String txnId) {
        TransactionContext context = stateService.getByTid(txnId);

        if (context == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(TransactionResponse.fromContext(context));
    }

    /**
     * GET /api/v1/txn/ref?tr={tr}&pa={pa} — Lookup transaction by composite key.
     */
    @GetMapping("/ref")
    public ResponseEntity<TransactionResponse> getTransactionByRef(
            @RequestParam String tr, @RequestParam String pa) {

        TransactionContext context = stateService.getByCompositeKey(tr, pa);

        if (context == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(TransactionResponse.fromContext(context));
    }
}
