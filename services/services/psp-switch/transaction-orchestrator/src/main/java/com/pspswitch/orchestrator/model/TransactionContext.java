package com.pspswitch.orchestrator.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Mutable saga context — the single object passed through all 10 orchestration
 * steps.
 * 
 * Contains all transaction data, the current state, timestamps, and NPCI
 * response fields.
 * Updated in-place by each saga step and persisted to TransactionStateService.
 */
public class TransactionContext {

    // --- Core identifiers ---
    private String tid; // PSP-generated transaction ID (correlation ID)
    private String tr; // Transaction reference from request
    private String pa; // Payee UPI ID
    private String pn; // Payee name
    private String mc; // Merchant category code
    private BigDecimal am; // Amount
    private BigDecimal mam; // Minimum amount
    private String cu; // Currency
    private String mode; // Transaction mode

    // --- Merchant reconciliation fields ---
    private String mid; // Merchant ID
    private String msid; // Store ID
    private String mtid; // Terminal ID

    // --- Preprocessing result ---
    private boolean requiresPasscode;
    private String flowType; // "MERCHANT" or "P2P"

    // --- Saga state ---
    private TransactionState state;
    private FlowDirection flowDirection; // SEND or COLLECT
    private String approvalRefNo; // From NPCI webhook (e.g., ARN-782341)
    private String responseCode; // From NPCI webhook (e.g., "00" or "ZM")
    private String failureReason; // Set on validation or NPCI failure

    // --- Timestamps ---
    private Instant createdAt;
    private Instant updatedAt;

    public TransactionContext() {
    }

    // --- Getters and Setters ---

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getTr() {
        return tr;
    }

    public void setTr(String tr) {
        this.tr = tr;
    }

    public String getPa() {
        return pa;
    }

    public void setPa(String pa) {
        this.pa = pa;
    }

    public String getPn() {
        return pn;
    }

    public void setPn(String pn) {
        this.pn = pn;
    }

    public String getMc() {
        return mc;
    }

    public void setMc(String mc) {
        this.mc = mc;
    }

    public BigDecimal getAm() {
        return am;
    }

    public void setAm(BigDecimal am) {
        this.am = am;
    }

    public BigDecimal getMam() {
        return mam;
    }

    public void setMam(BigDecimal mam) {
        this.mam = mam;
    }

    public String getCu() {
        return cu;
    }

    public void setCu(String cu) {
        this.cu = cu;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getMid() {
        return mid;
    }

    public void setMid(String mid) {
        this.mid = mid;
    }

    public String getMsid() {
        return msid;
    }

    public void setMsid(String msid) {
        this.msid = msid;
    }

    public String getMtid() {
        return mtid;
    }

    public void setMtid(String mtid) {
        this.mtid = mtid;
    }

    public boolean isRequiresPasscode() {
        return requiresPasscode;
    }

    public void setRequiresPasscode(boolean requiresPasscode) {
        this.requiresPasscode = requiresPasscode;
    }

    public String getFlowType() {
        return flowType;
    }

    public void setFlowType(String flowType) {
        this.flowType = flowType;
    }

    public TransactionState getState() {
        return state;
    }

    public void setState(TransactionState state) {
        this.state = state;
        this.updatedAt = Instant.now();
    }

    public String getApprovalRefNo() {
        return approvalRefNo;
    }

    public void setApprovalRefNo(String approvalRefNo) {
        this.approvalRefNo = approvalRefNo;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public FlowDirection getFlowDirection() {
        return flowDirection;
    }

    public void setFlowDirection(FlowDirection flowDirection) {
        this.flowDirection = flowDirection;
    }
}
