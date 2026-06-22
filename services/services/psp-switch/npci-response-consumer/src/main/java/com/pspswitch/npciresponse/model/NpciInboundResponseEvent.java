package com.pspswitch.npciresponse.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Kafka event published to {@code npci.inbound.response}.
 * Consumed by the transaction-orchestrator to complete the payment saga.
 *
 * <p>Field semantics match the npci-adapter's NpciInboundResponseEvent
 * for cross-service compatibility.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NpciInboundResponseEvent {

    /** PSP-generated Transaction ID (correlation key across services). */
    private String txnId;

    /** NPCI message ID echoed back from the request. */
    private String msgId;

    /**
     * Type of transaction: PAY | BALANCE | COLLECT.
     * Determines downstream routing in the orchestrator.
     */
    private String type;

    /**
     * NPCI result: SUCCESS | FAILURE | TIMEOUT | DEEMED.
     * DEEMED is treated as SUCCESS by the orchestrator.
     */
    private String result;

    /** Account balance — populated for BALANCE enquiry responses only. */
    private String balance;

    /** ISO-4217 currency code (always INR for UPI). */
    private String currency;

    /** NPCI error code — empty string on success. */
    private String errCode;

    /** ISO-8601 timestamp of when this event was created. */
    private String timestamp;

    public static NpciInboundResponseEvent success(String txnId, String msgId, String type, String approvalNum) {
        return NpciInboundResponseEvent.builder()
                .txnId(txnId)
                .msgId(msgId != null ? msgId : "")
                .type(type)
                .result("SUCCESS")
                .errCode("")
                .timestamp(Instant.now().toString())
                .build();
    }

    public static NpciInboundResponseEvent failure(String txnId, String msgId, String type, String errCode) {
        return NpciInboundResponseEvent.builder()
                .txnId(txnId)
                .msgId(msgId != null ? msgId : "")
                .type(type)
                .result("FAILURE")
                .errCode(errCode != null ? errCode : "ZM")
                .timestamp(Instant.now().toString())
                .build();
    }

    public static NpciInboundResponseEvent balance(String txnId, String msgId, String bal, String currency) {
        return NpciInboundResponseEvent.builder()
                .txnId(txnId)
                .msgId(msgId != null ? msgId : "")
                .type("BALANCE")
                .result("SUCCESS")
                .balance(bal)
                .currency(currency != null ? currency : "INR")
                .errCode("")
                .timestamp(Instant.now().toString())
                .build();
    }
}
