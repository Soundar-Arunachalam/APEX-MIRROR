package com.pspswitch.orchestrator.adapter;

import com.pspswitch.orchestrator.controller.WebhookController;
import com.pspswitch.orchestrator.model.CbsCallbackPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * CBS Adapter — simulates REST communication with the Core Banking System.
 *
 * Used ONLY during the COLLECT (Receiver Side) flow.
 * When NPCI confirms that the payer has been debited, the PSP Orchestrator
 * uses this adapter to credit the merchant's account in the local CBS.
 *
 * NOT used during the SEND (Payer Side) flow, where NPCI handles all
 * debit/credit routing directly.
 *
 * Mock behaviour:
 * - creditPayee() simulates 500ms network latency, returns boolean
 * - On success: fires CBS webhook callback after 500ms (informational)
 * - On failureMode=true: returns false immediately (no webhook)
 * - mid/msid/mtid echoed in every call for reconciliation
 *
 * Thread-safety: failureMode is volatile for cross-thread visibility.
 */
@Service
public class CbsAdapter {

    private static final Logger log = LoggerFactory.getLogger(CbsAdapter.class);

    private final WebhookController webhookController;

    /** Thread-safe toggle for demo failure simulation */
    private volatile boolean failureMode = false;

    public CbsAdapter(@Lazy WebhookController webhookController) {
        this.webhookController = webhookController;
    }

    /**
     * Simulates crediting the payee's account via CBS REST POST.
     *
     * @return true if credit succeeded, false if CBS rejected
     */
    public boolean creditPayee(String tid, String pa, BigDecimal amount,
            String mid, String msid, String mtid) {
        try {
            // Simulate CBS REST POST network latency (500ms)
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[CBS_ADAPTER] tid={} | Interrupted during REST call", tid);
            return false;
        }

        if (failureMode) {
            // CBS rejects the credit (insufficient funds, blocked account, etc.)
            log.info("[CBS_ADAPTER] tid={} | CREDIT_REJECTED | failureMode=true", tid);
            return false;
        }

        // CBS credit succeeded — fire informational webhook after 500ms
        fireCbsWebhookAsync(tid);
        return true;
    }

    /**
     * Fires the CBS webhook callback after 500ms (informational confirmation).
     * By the time this arrives, the orchestrator has already marked SUCCESS.
     */
    @Async("orchestratorExecutor")
    public void fireCbsWebhookAsync(String tid) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        CbsCallbackPayload payload = new CbsCallbackPayload(
                tid, "CREDITED", Instant.now().toString());

        // Call our own CBS webhook endpoint (simulates CBS POST to /api/v1/webhook/cbs)
        webhookController.handleCbsCallback(payload);
    }

    // --- Failure mode controls ---

    public void setFailureMode(boolean failureMode) {
        this.failureMode = failureMode;
    }

    public boolean isFailureMode() {
        return failureMode;
    }
}
