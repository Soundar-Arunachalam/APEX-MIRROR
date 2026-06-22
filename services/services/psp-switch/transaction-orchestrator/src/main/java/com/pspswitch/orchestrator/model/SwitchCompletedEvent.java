package com.pspswitch.orchestrator.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published to Kafka topic {@code psp.switch.completed.events}.
 * Consumed by tpap-egress-service which dispatches typed webhooks to TPAPs.
 *
 * <p>The {@code eventType} field determines which handler in tpap-egress processes it:
 * <ul>
 *   <li>PAYMENT_PUSH — payment result (success or failure)</li>
 *   <li>BALANCE_INQUIRY — balance enquiry result</li>
 *   <li>VPA_VERIFICATION — VPA lookup result</li>
 * </ul>
 *
 * <p>The {@code payload} object is a JSON-serialisable map containing transaction details.
 */
public class SwitchCompletedEvent {

    private String eventId;
    private String eventType;
    private String tpapId;
    private String txnId;
    private String correlationId;
    private String timestamp;
    private String schemaVersion;
    private Object payload;

    public SwitchCompletedEvent() {
    }

    /** Factory — creates a PAYMENT_PUSH event from a completed transaction context. */
    public static SwitchCompletedEvent forPayment(TransactionContext ctx) {
        SwitchCompletedEvent evt = new SwitchCompletedEvent();
        evt.eventId = UUID.randomUUID().toString();
        evt.eventType = "PAYMENT_PUSH";
        evt.tpapId = deriveTpapId(ctx.getTr());
        evt.txnId = ctx.getTid();
        evt.correlationId = ctx.getTr();
        evt.timestamp = Instant.now().toString();
        evt.schemaVersion = "1.0";
        evt.payload = PaymentPushPayload.from(ctx);
        return evt;
    }

    /** Factory — creates a BALANCE_INQUIRY event. */
    public static SwitchCompletedEvent forBalance(TransactionContext ctx, String balance, String currency) {
        SwitchCompletedEvent evt = new SwitchCompletedEvent();
        evt.eventId = UUID.randomUUID().toString();
        evt.eventType = "BALANCE_INQUIRY";
        evt.tpapId = deriveTpapId(ctx.getTr());
        evt.txnId = ctx.getTid();
        evt.correlationId = ctx.getTr();
        evt.timestamp = Instant.now().toString();
        evt.schemaVersion = "1.0";
        evt.payload = new BalancePayload(ctx.getTid(), balance, currency,
                ctx.getState() != null ? ctx.getState().name() : "UNKNOWN");
        return evt;
    }

    public static SwitchCompletedEvent forVpa(TransactionContext ctx, String status, String name) {
        SwitchCompletedEvent evt = new SwitchCompletedEvent();
        evt.eventId = UUID.randomUUID().toString();
        evt.eventType = "VPA_VERIFICATION";
        evt.tpapId = deriveTpapId(ctx.getTr());
        evt.txnId = ctx.getTid();
        evt.correlationId = ctx.getTr();
        evt.timestamp = Instant.now().toString();
        evt.schemaVersion = "1.0";
        evt.payload = new VpaPayload(ctx.getTid(), ctx.getPa(), status, name);
        return evt;
    }

    private static String deriveTpapId(String tr) {
        if (tr != null && tr.contains("-")) return tr.substring(0, tr.indexOf('-'));
        return "unknown";
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getTpapId() { return tpapId; }
    public void setTpapId(String tpapId) { this.tpapId = tpapId; }

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }

    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }

    // ── Inner payload types ───────────────────────────────────────────────────

    public record PaymentPushPayload(
            String txnId, String txnRef, String payeeVpa, String status,
            String responseCode, String approvalRefNo, String failureReason,
            String amount, String currency) {

        static PaymentPushPayload from(TransactionContext ctx) {
            return new PaymentPushPayload(
                    ctx.getTid(),
                    ctx.getTr(),
                    ctx.getPa(),
                    ctx.getState() != null ? ctx.getState().name() : "UNKNOWN",
                    ctx.getResponseCode(),
                    ctx.getApprovalRefNo(),
                    ctx.getFailureReason(),
                    ctx.getAm() != null ? ctx.getAm().toPlainString() : "0.00",
                    ctx.getCu() != null ? ctx.getCu() : "INR"
            );
        }
    }

    public record BalancePayload(String txnId, String balance, String currency, String status) {
    }

    public record VpaPayload(String txnId, String vpa, String status, String name) {
    }
}
