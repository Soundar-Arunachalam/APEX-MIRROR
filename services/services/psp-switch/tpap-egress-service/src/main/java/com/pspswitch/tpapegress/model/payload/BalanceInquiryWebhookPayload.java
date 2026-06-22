package com.pspswitch.tpapegress.model.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Webhook payload for BALANCE_INQUIRY events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceInquiryWebhookPayload implements WebhookPayload {

    // ── Envelope fields ──
    private String  eventId;
    private String  eventType;
    private String  tpapId;
    private String  txnId;
    private String  correlationId;
    private Instant deliveredAt;

    // ── Balance-specific data ──
    private String vpa;
    private String availableBalance;
    private String currency;
    private String inquiryStatus;
}

