package com.pspswitch.orchestrator.model;

import java.time.Instant;

/**
 * Event published to Kafka topic {@code npci.outbound.request}.
 * Consumed by the npci-adapter service which builds and POSTs the UPI XML to NPCI.
 *
 * <p>Field naming follows the NPCI UPI spec abbreviations:
 * <ul>
 *   <li>txnId — PSP-generated transaction ID (correlation key)</li>
 *   <li>type — PAY | BALANCE | COLLECT</li>
 *   <li>payerVpa — payer's UPI Virtual Payment Address</li>
 *   <li>payeeVpa — payee's UPI Virtual Payment Address</li>
 *   <li>amount — transaction amount as string (2 decimal places)</li>
 * </ul>
 */
public class NpciOutboundRequestEvent {

    private String txnId;
    private String msgId;
    private String type;
    private String payerVpa;
    private String payeeVpa;
    private String amount;
    private String currency;
    private String pspId;
    private String timestamp;

    public NpciOutboundRequestEvent() {
    }

    public NpciOutboundRequestEvent(String txnId, String msgId, String type,
                                     String payerVpa, String payeeVpa,
                                     String amount, String currency,
                                     String pspId) {
        this.txnId = txnId;
        this.msgId = msgId;
        this.type = type;
        this.payerVpa = payerVpa;
        this.payeeVpa = payeeVpa;
        this.amount = amount;
        this.currency = currency;
        this.pspId = pspId;
        this.timestamp = Instant.now().toString();
    }

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }

    public String getMsgId() { return msgId; }
    public void setMsgId(String msgId) { this.msgId = msgId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPayerVpa() { return payerVpa; }
    public void setPayerVpa(String payerVpa) { this.payerVpa = payerVpa; }

    public String getPayeeVpa() { return payeeVpa; }
    public void setPayeeVpa(String payeeVpa) { this.payeeVpa = payeeVpa; }

    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPspId() { return pspId; }
    public void setPspId(String pspId) { this.pspId = pspId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
