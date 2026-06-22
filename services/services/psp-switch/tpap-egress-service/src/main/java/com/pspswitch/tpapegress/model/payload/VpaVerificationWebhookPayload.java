package com.pspswitch.tpapegress.model.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Webhook payload for VPA_VERIFICATION events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VpaVerificationWebhookPayload implements WebhookPayload {

    // ── Envelope fields ──
    private String  eventId;
    private String  eventType;
    private String  tpapId;
    private String  txnId;
    private String  correlationId;
    private Instant deliveredAt;

    // ── VPA-specific data ──
    private String  vpa;
    private String  accountHolderName;
    private String  bankName;
    private boolean verified;
    private String  failureReason;
}

