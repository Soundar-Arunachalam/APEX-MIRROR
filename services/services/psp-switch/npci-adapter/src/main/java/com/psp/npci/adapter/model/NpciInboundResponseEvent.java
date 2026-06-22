package com.psp.npci.adapter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the event published by the Adapter to Kafka topic
 * {@code npci.inbound.response}. Both the Orchestrator and Notification Service
 * consume this event.
 *
 * <p>
 * JSON shape:
 * 
 * <pre>
 * {
 *   "txnId":     "uuid",
 *   "msgId":     "uuid",
 *   "type":      "PAY" | "BALANCE" | "COLLECT",
 *   "result":    "SUCCESS" | "FAILURE" | "TIMEOUT" | "DEEMED",
 *   "balance":   "25000.00",
 *   "currency":  "INR",
 *   "errCode":   "",
 *   "timestamp": "2026-04-18T10:30:05Z"
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NpciInboundResponseEvent {

    /**
     * Unique transaction identifier (UUID). Matches the original outbound txnId.
     */
    private String txnId;

    /**
     * Message identifier echoed from the outbound request or generated for inbound
     * collect.
     */
    private String msgId;

    /**
     * Transaction type.
     * <ul>
     * <li>{@code PAY} — Result of a PSP-initiated PAY request</li>
     * <li>{@code BALANCE} — Result of a balance enquiry</li>
     * <li>{@code COLLECT} — NPCI-initiated collect/credit request forwarded to
     * PSP</li>
     * </ul>
     */
    private String type;

    /**
     * Final result of the transaction.
     * <ul>
     * <li>{@code SUCCESS} — Transaction approved by NPCI</li>
     * <li>{@code FAILURE} — Transaction declined or error from NPCI</li>
     * <li>{@code TIMEOUT} — NPCI did not respond within the expected window</li>
     * <li>{@code DEEMED} — Deemed success (treated as SUCCESS downstream)</li>
     * </ul>
     */
    private String result;

    /** Account balance; populated for BALANCE enquiry responses. Null otherwise. */
    private String balance;

    /** Currency code for balance; e.g. {@code INR}. Null for non-BALANCE flows. */
    private String currency;

    /** NPCI error code; empty string when no error. */
    private String errCode;

    /** ISO-8601 UTC timestamp of when the Adapter generated this response event. */
    private String timestamp;
}
