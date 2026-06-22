package com.pspswitch.orchestrator.adapter;

import com.pspswitch.orchestrator.controller.WebhookController;
import com.pspswitch.orchestrator.model.NpciCallbackPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Random;

/**
 * NPCI Adapter — simulates REST communication with the NPCI switch.
 *
 * Communication model:
 * PSP → NPCI: REST POST (synchronous SUBMITTED ack)
 * NPCI → PSP: Webhook callback after async processing (1500ms delay)
 *
 * Mock behaviour:
 * - forward() simulates 800ms network latency, returns SUBMITTED
 * - After SUBMITTED, fires webhook callback internally after 1500ms
 * - When failureMode=true: callback sends responseCode="ZM" (FAILED)
 * - When failureMode=false: callback sends responseCode="00" (SUCCESS) + ARN
 * - reversal() simulates 500ms delay, always succeeds
 *
 * Thread-safety: failureMode is volatile for cross-thread visibility.
 * The @Lazy on WebhookController breaks the circular dependency:
 * WebhookController → NpciAdapter (for queryStatus)
 * NpciAdapter → WebhookController (for webhook simulation)
 */
@Service
public class NpciAdapter {

    private static final Logger log = LoggerFactory.getLogger(NpciAdapter.class);
    private static final Random RANDOM = new Random();

    private final WebhookController webhookController;

    /** Thread-safe toggle for demo failure simulation */
    private volatile boolean failureMode = false;

    /** Toggle to suppress webhook entirely (for timeout testing) */
    private volatile boolean suppressWebhook = false;

    public NpciAdapter(@Lazy WebhookController webhookController) {
        this.webhookController = webhookController;
    }

    /**
     * Simulates forwarding the transaction to NPCI via REST POST.
     * Returns immediately with SUBMITTED status after 800ms delay.
     * Then fires the webhook callback asynchronously after 1500ms.
     *
     * @param tid the PSP-generated transaction ID (correlation key)
     */
    public void forward(String tid) {
        try {
            // Simulate REST POST network latency (800ms)
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[NPCI_ADAPTER] tid={} | Interrupted during REST call", tid);
            return;
        }

        log.info("[NPCI_ADAPTER] tid={} | REST_CALL_SENT | state=SUBMITTED | awaiting webhook", tid);

        // Fire webhook callback asynchronously (simulates NPCI calling back after PIN
        // auth)
        if (!suppressWebhook) {
            java.util.concurrent.CompletableFuture.runAsync(() -> fireWebhookAsync(tid));
        } else {
            log.info("[NPCI_ADAPTER] tid={} | WEBHOOK_SUPPRESSED | Simulating timeout scenario", tid);
        }
    }

    /**
     * Fires the NPCI webhook callback after a 1500ms delay (simulates NPCI
     * processing time).
     * Runs on the orchestrator thread pool to avoid blocking the saga thread.
     */
    @Async("orchestratorExecutor")
    public void fireWebhookAsync(String tid) {
        try {
            // Simulate NPCI processing time (UPI PIN authorisation)
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        NpciCallbackPayload payload;

        if (failureMode) {
            // Simulate NPCI rejection (wrong PIN, blocked account, etc.)
            payload = new NpciCallbackPayload(tid, "ZM", null, "FAILED");
        } else {
            // Simulate NPCI success with approval reference number
            String arn = "ARN-" + String.format("%06d", RANDOM.nextInt(999999));
            payload = new NpciCallbackPayload(tid, "00", arn, "SUCCESS");
        }

        // Call our own webhook endpoint (simulates NPCI POST to /api/v1/webhook/npci)
        webhookController.handleNpciCallback(payload);
    }

    /**
     * Simulates sending a reversal to NPCI (COLLECT flow compensation after CBS
     * failure).
     * Always succeeds after 500ms delay.
     */
    public void reversal(String tid, BigDecimal amount) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[NPCI_ADAPTER] tid={} | REVERSAL_COMPLETED | amount={}", tid, amount);
    }

    /**
     * Queries NPCI for the final status of a transaction.
     * Used by ReconciliationService to resolve UNKNOWN transactions.
     *
     * Simulates a 500ms NPCI API call. Returns NpciCallbackPayload
     * with the final responseCode ("00" for success, "ZM" for failure).
     *
     * @param tid the PSP-generated transaction ID
     * @return NpciCallbackPayload with responseCode and approvalRefNo
     */
    public NpciCallbackPayload queryStatus(String tid) {
        try {
            // Simulate NPCI status query network latency (500ms)
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        NpciCallbackPayload response;

        if (failureMode) {
            response = new NpciCallbackPayload(tid, "ZM", null, "FAILED");
        } else {
            String arn = "ARN-RECON-" + String.format("%06d", RANDOM.nextInt(999999));
            response = new NpciCallbackPayload(tid, "00", arn, "SUCCESS");
        }

        log.info("[NPCI_ADAPTER] queryStatus | tid={} | responseCode={}", tid, response.getResponseCode());
        return response;
    }

    // --- Failure mode controls ---

    public void setFailureMode(boolean failureMode) {
        this.failureMode = failureMode;
    }

    public boolean isFailureMode() {
        return failureMode;
    }

    public void setSuppressWebhook(boolean suppressWebhook) {
        this.suppressWebhook = suppressWebhook;
    }

    public boolean isSuppressWebhook() {
        return suppressWebhook;
    }
}
