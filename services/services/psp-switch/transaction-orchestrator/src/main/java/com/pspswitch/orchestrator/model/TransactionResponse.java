package com.pspswitch.orchestrator.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Transaction Response DTO — returned to the caller from all endpoints.
 * 
 * Built from TransactionContext using the static factory method fromContext().
 */
public class TransactionResponse {

    private String txnId;
    private String tr;
    private String pa;
    private String pn;
    private BigDecimal am;
    private String state;
    private String approvalRefNo;
    private String responseCode;
    private String mode;
    private boolean requiresPasscode;
    private String flowType;
    private String flowDirection;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;
    private String message;

    public TransactionResponse() {
    }

    /**
     * Factory method — builds TransactionResponse from a TransactionContext.
     */
    public static TransactionResponse fromContext(TransactionContext ctx) {
        TransactionResponse resp = new TransactionResponse();
        resp.txnId = ctx.getTid();
        resp.tr = ctx.getTr();
        resp.pa = ctx.getPa();
        resp.pn = ctx.getPn();
        resp.am = ctx.getAm();
        resp.state = ctx.getState() != null ? ctx.getState().name() : null;
        resp.approvalRefNo = ctx.getApprovalRefNo();
        resp.responseCode = ctx.getResponseCode();
        resp.mode = ctx.getMode();
        resp.requiresPasscode = ctx.isRequiresPasscode();
        resp.flowType = ctx.getFlowType();
        resp.flowDirection = ctx.getFlowDirection() != null ? ctx.getFlowDirection().name() : null;
        resp.failureReason = ctx.getFailureReason();
        resp.createdAt = ctx.getCreatedAt();
        resp.updatedAt = ctx.getUpdatedAt();
        return resp;
    }

    // --- Getters and Setters ---

    public String getTxnId() {
        return txnId;
    }

    public void setTxnId(String txnId) {
        this.txnId = txnId;
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

    public BigDecimal getAm() {
        return am;
    }

    public void setAm(BigDecimal am) {
        this.am = am;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
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

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
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

    public String getFlowDirection() {
        return flowDirection;
    }

    public void setFlowDirection(String flowDirection) {
        this.flowDirection = flowDirection;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
