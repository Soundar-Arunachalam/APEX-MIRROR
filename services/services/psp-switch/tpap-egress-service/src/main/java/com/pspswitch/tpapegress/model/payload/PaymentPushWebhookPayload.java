package com.pspswitch.tpapegress.model.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Webhook payload for PAYMENT_PUSH events.
 * Delivered to the TPAP-registered webhook URL as JSON.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPushWebhookPayload implements WebhookPayload {

    // ── Envelope fields ──
    private String  eventId;
    private String  eventType;
    private String  tpapId;
    private String  txnId;
    private String  correlationId;
    private Instant deliveredAt;

    // ── Payment-specific data ──
    private String payerVpa;
    private String payeeVpa;
    private String amount;
    private String currency;
    private String npciRrn;
    private String txnStatus;
    private String failureReason;
}

