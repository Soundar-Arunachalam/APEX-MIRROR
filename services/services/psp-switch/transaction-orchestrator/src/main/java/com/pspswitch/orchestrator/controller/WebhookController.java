package com.pspswitch.orchestrator.controller;

import com.pspswitch.orchestrator.adapter.CbsAdapter;
import com.pspswitch.orchestrator.adapter.LedgerService;
import com.pspswitch.orchestrator.adapter.NpciAdapter;
import com.pspswitch.orchestrator.adapter.NotificationService;
import com.pspswitch.orchestrator.model.*;
import com.pspswitch.orchestrator.service.IdempotencyService;
import com.pspswitch.orchestrator.service.TransactionStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Webhook Controller — receives callbacks from NPCI and CBS.
 *
 * Endpoints:
 * POST /api/v1/webhook/npci — NPCI final transaction result
 * POST /api/v1/webhook/cbs — CBS credit confirmation (informational, COLLECT
 * only)
 *
 * Dual-direction branching on NPCI SUCCESS:
 * - SEND flow: Log audit → SUCCESS → Notify TPAP (no CBS interaction)
 * - COLLECT flow: CBS credit → if ok: Ledger + SUCCESS; if fail: NPCI reversal
 * → COMPENSATED
 */
@RestController
@RequestMapping("/api/v1/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final TransactionStateService stateService;
    private final IdempotencyService idempotencyService;
    private final CbsAdapter cbsAdapter;
    private final NpciAdapter npciAdapter;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;

    // Stores timeout futures so they can be cancelled when NPCI webhook arrives
    private final ConcurrentHashMap<String, ScheduledFuture<?>> timeoutFutures = new ConcurrentHashMap<>();

    public WebhookController(TransactionStateService stateService,
            IdempotencyService idempotencyService,
            @Lazy CbsAdapter cbsAdapter,
            @Lazy NpciAdapter npciAdapter,
            LedgerService ledgerService,
            NotificationService notificationService) {
        this.stateService = stateService;
        this.idempotencyService = idempotencyService;
        this.cbsAdapter = cbsAdapter;
        this.npciAdapter = npciAdapter;
        this.ledgerService = ledgerService;
        this.notificationService = notificationService;
    }

    /**
     * Registers a timeout future for a transaction so it can be cancelled
     * when the NPCI webhook arrives.
     */
    public void registerTimeoutFuture(String tid, ScheduledFuture<?> future) {
        timeoutFutures.put(tid, future);
    }

    /**
     * POST /api/v1/webhook/npci
     *
     * Receives the final transaction result from NPCI (simulated by NpciAdapter).
     * On SUCCESS (responseCode="00"): branches on flowDirection (SEND vs COLLECT).
     * On FAILURE (responseCode!="00"): marks transaction FAILED and ends flow.
     */
    @PostMapping("/npci")
    public ResponseEntity<Map<String, String>> handleNpciCallback(@RequestBody NpciCallbackPayload payload) {
        String tid = payload.getTid();
        TransactionContext context = stateService.getByTid(tid);

        if (context == null) {
            log.warn("[WEBHOOK] tid={} | NPCI callback for unknown transaction", tid);
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown transaction"));
        }

        // Cancel timeout task — NPCI responded in time
        ScheduledFuture<?> timeoutFuture = timeoutFutures.remove(tid);
        if (timeoutFuture != null) {
            timeoutFuture.cancel(false);
        }

        if ("00".equals(payload.getResponseCode())) {
            // ——— NPCI SUCCESS ———
            log.info("[WEBHOOK] tid={} | NPCI_SUCCESS | responseCode=00 | approvalRefNo={}",
                    tid, payload.getApprovalRefNo());

            context.setApprovalRefNo(payload.getApprovalRefNo());
            context.setResponseCode("00");

            // ═══════════════════════════════════════════════════════
            // DUAL-DIRECTION BRANCH
            // ═══════════════════════════════════════════════════════
            if (context.getFlowDirection() == FlowDirection.COLLECT) {
                // COLLECT flow → CBS credit required
                processCbsCreditAsync(context);
            } else {
                // SEND flow (default) → Direct SUCCESS, no CBS
                completeSendFlow(context);
            }

        } else {
            // ——— NPCI FAILURE ———
            log.info("[WEBHOOK] tid={} | NPCI_FAILED | responseCode={}", tid, payload.getResponseCode());

            context.setState(TransactionState.FAILED);
            context.setResponseCode(payload.getResponseCode());
            context.setFailureReason("NPCI rejected: responseCode=" + payload.getResponseCode());
            stateService.update(context);

            // Cache FAILED response in idempotency store
            String key = idempotencyService.buildKey(context.getTr(), context.getPa());
            idempotencyService.cacheResponse(key, TransactionResponse.fromContext(context));

            notificationService.notifyFailure(tid, context.getPa(), context.getFailureReason());
        }

        return ResponseEntity.ok(Map.of("status", "received"));
    }

    // ═══════════════════════════════════════════════════════
    // SEND FLOW — Payer Side (no CBS interaction)
    // ═══════════════════════════════════════════════════════

    /**
     * Completes the SEND flow: Ledger audit + SUCCESS + Notify TPAP.
     * No CBS interaction — NPCI handles all debit/credit routing.
     */
    private void completeSendFlow(TransactionContext context) {
        String tid = context.getTid();

        // Audit Trail (Ledger)
        ledgerService.record(context, context.getApprovalRefNo());

        // Finalise
        context.setState(TransactionState.SUCCESS);
        stateService.update(context);

        String key = idempotencyService.buildKey(context.getTr(), context.getPa());
        idempotencyService.cacheResponse(key, TransactionResponse.fromContext(context));

        notificationService.notify(tid, context.getPa(), context.getAm(), "SUCCESS");
        log.info("[ORCHESTRATOR] tid={} | SEND | COMPLETE | Final state=SUCCESS", tid);
    }

    // ═══════════════════════════════════════════════════════
    // COLLECT FLOW — Receiver Side (CBS credit required)
    // ═══════════════════════════════════════════════════════

    /**
     * Processes CBS credit asynchronously after NPCI success (COLLECT flow only).
     * Credits the merchant's CBS account. If CBS fails, triggers compensation.
     */
    @Async("orchestratorExecutor")
    public void processCbsCreditAsync(TransactionContext context) {
        String tid = context.getTid();

        try {
            log.info("[CBS_ADAPTER] tid={} | REST_CALL_SENT | payee={} | amount={} | mid={}",
                    tid, context.getPa(), context.getAm(), context.getMid());

            boolean cbsSuccess = cbsAdapter.creditPayee(
                    tid, context.getPa(), context.getAm(),
                    context.getMid(), context.getMsid(), context.getMtid());

            if (cbsSuccess) {
                // CBS credit succeeded
                log.info("[CBS_ADAPTER] tid={} | CREDIT_SUCCESS | payee={} credited {}",
                        tid, context.getPa(), context.getAm());

                // Ledger write
                ledgerService.record(context, context.getApprovalRefNo());

                // Finalise
                context.setState(TransactionState.SUCCESS);
                stateService.update(context);

                String key = idempotencyService.buildKey(context.getTr(), context.getPa());
                idempotencyService.cacheResponse(key, TransactionResponse.fromContext(context));

                notificationService.notify(tid, context.getPa(), context.getAm(), "SUCCESS");
                log.info("[ORCHESTRATOR] tid={} | COLLECT | COMPLETE | Final state=SUCCESS", tid);

            } else {
                // CBS credit failed → COMPENSATION
                log.info("[CBS_ADAPTER] tid={} | CREDIT_FAILED | Triggering compensation", tid);
                performCompensation(context);
            }

        } catch (Exception e) {
            log.error("[ORCHESTRATOR] tid={} | CBS processing error: {}", tid, e.getMessage(), e);
            performCompensation(context);
        }
    }

    /**
     * Compensation flow (COLLECT only): CBS failed after NPCI SUCCESS → send
     * reversal to NPCI.
     * NpciAdapter.reversal() is a synchronous mock (500ms delay, always succeeds).
     */
    private void performCompensation(TransactionContext context) {
        String tid = context.getTid();

        // Call NPCI reversal endpoint
        log.info("[COMPENSATION] tid={} | REVERSAL_SENT | amount={}", tid, context.getAm());
        npciAdapter.reversal(tid, context.getAm());

        // Update state to COMPENSATED
        context.setState(TransactionState.COMPENSATED);
        context.setFailureReason("CBS credit failed — reversal sent to NPCI");
        stateService.update(context);

        // Cache COMPENSATED response in idempotency store
        String key = idempotencyService.buildKey(context.getTr(), context.getPa());
        idempotencyService.cacheResponse(key, TransactionResponse.fromContext(context));

        notificationService.notifyCompensation(tid, context.getPa(), context.getAm());
        log.info("[COMPENSATION] tid={} | state=COMPENSATED | Flow ended", tid);
    }

    /**
     * POST /api/v1/webhook/cbs
     *
     * Receives CBS credit confirmation — informational only (COLLECT flow).
     * By the time this arrives, the transaction is already in SUCCESS state.
     */
    @PostMapping("/cbs")
    public ResponseEntity<Map<String, String>> handleCbsCallback(@RequestBody CbsCallbackPayload payload) {
        log.info("[WEBHOOK] tid={} | CBS_CONFIRMATION_RECEIVED | status={}",
                payload.getTid(), payload.getStatus());
        return ResponseEntity.ok(Map.of("status", "received"));
    }
}
