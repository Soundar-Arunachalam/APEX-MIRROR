package com.hpe.upi.shared.model;

import java.math.BigDecimal;
import java.time.Instant;

public class UpiTransaction {
    private String txnId;
    private String rrn;
    private String payerVpa;
    private String payeeVpa;
    private String payerBank;
    private String payeeBank;
    private BigDecimal amount;
    private String status; // INITIATED, ROUTING, DEBIT_PENDING, DEBIT_SUCCESS, CREDIT_PENDING, SUCCESS, FAILED, REVERSAL_INITIATED, REVERSED
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;
    private int retryCount;
    private boolean reversalInitiated;
    private String reversalReason;

    public UpiTransaction() {}

    public UpiTransaction(String txnId, String payerVpa, String payeeVpa, String payerBank, String payeeBank, BigDecimal amount) {
        this.txnId = txnId;
        this.rrn = "RRN" + txnId.substring(txnId.length() - 8);
        this.payerVpa = payerVpa;
        this.payeeVpa = payeeVpa;
        this.payerBank = payerBank;
        this.payeeBank = payeeBank;
        this.amount = amount;
        this.status = "INITIATED";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.retryCount = 0;
        this.reversalInitiated = false;
    }

    public String getTxnId() { return txnId; }
    public void setTxnId(String txnId) { this.txnId = txnId; }
    public String getRrn() { return rrn; }
    public void setRrn(String rrn) { this.rrn = rrn; }
    public String getPayerVpa() { return payerVpa; }
    public void setPayerVpa(String payerVpa) { this.payerVpa = payerVpa; }
    public String getPayeeVpa() { return payeeVpa; }
    public void setPayeeVpa(String payeeVpa) { this.payeeVpa = payeeVpa; }
    public String getPayerBank() { return payerBank; }
    public void setPayerBank(String payerBank) { this.payerBank = payerBank; }
    public String getPayeeBank() { return payeeBank; }
    public void setPayeeBank(String payeeBank) { this.payeeBank = payeeBank; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.updatedAt = Instant.now(); }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public boolean isReversalInitiated() { return reversalInitiated; }
    public void setReversalInitiated(boolean reversalInitiated) { this.reversalInitiated = reversalInitiated; }
    public String getReversalReason() { return reversalReason; }
    public void setReversalReason(String reversalReason) { this.reversalReason = reversalReason; }
}
