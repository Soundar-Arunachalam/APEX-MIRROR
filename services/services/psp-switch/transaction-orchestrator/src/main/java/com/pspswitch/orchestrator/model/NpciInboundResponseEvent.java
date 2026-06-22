package com.pspswitch.orchestrator.model;

/**
 * Event consumed from Kafka topic {@code npci.inbound.response}.
 * Published by the npci-adapter (or npci-response-consumer) after receiving
 * the NPCI XML response/callback.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>txnId — PSP-generated transaction ID (correlation key)</li>
 *   <li>type — PAY | BALANCE | COLLECT</li>
 *   <li>result — SUCCESS | FAILURE | TIMEOUT | DEEMED</li>
 *   <li>errCode — NPCI error code (empty string on success)</li>
 *   <li>balance — account balance (BALANCE enquiry only)</li>
 *   <li>currency — ISO-4217 currency code (INR)</li>
 * </ul>
 */
public class NpciInboundResponseEvent {

    private String txnId;
    private String msgId;
    private String type;
    private String result;
    private String balance;
    private String currency;
    private String errCode;
    private String timestamp;

    public NpciInboundResponseEvent() {
    }

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }

    public String getMsgId() { return msgId; }
    public void setMsgId(String msgId) { this.msgId = msgId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getBalance() { return balance; }
    public void setBalance(String balance) { this.balance = balance; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getErrCode() { return errCode; }
    public void setErrCode(String errCode) { this.errCode = errCode; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    /** Returns true if result is SUCCESS or DEEMED (NPCI deemed = treated as success). */
    public boolean isSuccess() {
        return "SUCCESS".equalsIgnoreCase(result) || "DEEMED".equalsIgnoreCase(result);
    }

    /** Returns true if result indicates timeout. */
    public boolean isTimeout() {
        return "TIMEOUT".equalsIgnoreCase(result);
    }
}
