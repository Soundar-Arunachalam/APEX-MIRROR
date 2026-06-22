package com.pspswitch.orchestrator.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA Entity for persisting transaction state in PostgreSQL.
 *
 * Maps to the 'transactions' table. The tid (PSP-generated) is the primary key.
 * All saga state transitions are persisted here for durability and
 * auditability.
 */
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_tr_pa", columnList = "tr, pa")
})
public class TransactionEntity {

    @Id
    @Column(name = "tid", length = 20)
    private String tid;

    @Column(name = "tr", nullable = false, length = 50)
    private String tr;

    @Column(name = "pa", nullable = false, length = 100)
    private String pa;

    @Column(name = "pn", length = 100)
    private String pn;

    @Column(name = "mc", length = 10)
    private String mc;

    @Column(name = "am", precision = 15, scale = 2)
    private BigDecimal am;

    @Column(name = "mam", precision = 15, scale = 2)
    private BigDecimal mam;

    @Column(name = "cu", length = 5)
    private String cu;

    @Column(name = "mode", length = 5)
    private String mode;

    @Column(name = "mid", length = 50)
    private String mid;

    @Column(name = "msid", length = 50)
    private String msid;

    @Column(name = "mtid", length = 50)
    private String mtid;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private TransactionState state;

    @Column(name = "approval_ref_no", length = 20)
    private String approvalRefNo;

    @Column(name = "response_code", length = 10)
    private String responseCode;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "requires_passcode")
    private boolean requiresPasscode;

    @Column(name = "flow_type", length = 20)
    private String flowType;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_direction", length = 10)
    private FlowDirection flowDirection;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public TransactionEntity() {
    }

    /**
     * Converts this entity to a TransactionContext (used by the saga).
     */
    public TransactionContext toContext() {
        TransactionContext ctx = new TransactionContext();
        ctx.setTid(this.tid);
        ctx.setTr(this.tr);
        ctx.setPa(this.pa);
        ctx.setPn(this.pn);
        ctx.setMc(this.mc);
        ctx.setAm(this.am);
        ctx.setMam(this.mam);
        ctx.setCu(this.cu);
        ctx.setMode(this.mode);
        ctx.setMid(this.mid);
        ctx.setMsid(this.msid);
        ctx.setMtid(this.mtid);
        ctx.setState(this.state);
        ctx.setApprovalRefNo(this.approvalRefNo);
        ctx.setResponseCode(this.responseCode);
        ctx.setFailureReason(this.failureReason);
        ctx.setRequiresPasscode(this.requiresPasscode);
        ctx.setFlowType(this.flowType);
        ctx.setFlowDirection(this.flowDirection);
        ctx.setCreatedAt(this.createdAt);
        ctx.setUpdatedAt(this.updatedAt);
        return ctx;
    }

    /**
     * Creates an entity from a TransactionContext (for persistence).
     */
    public static TransactionEntity fromContext(TransactionContext ctx) {
        TransactionEntity entity = new TransactionEntity();
        entity.tid = ctx.getTid();
        entity.tr = ctx.getTr();
        entity.pa = ctx.getPa();
        entity.pn = ctx.getPn();
        entity.mc = ctx.getMc();
        entity.am = ctx.getAm();
        entity.mam = ctx.getMam();
        entity.cu = ctx.getCu();
        entity.mode = ctx.getMode();
        entity.mid = ctx.getMid();
        entity.msid = ctx.getMsid();
        entity.mtid = ctx.getMtid();
        entity.state = ctx.getState();
        entity.approvalRefNo = ctx.getApprovalRefNo();
        entity.responseCode = ctx.getResponseCode();
        entity.failureReason = ctx.getFailureReason();
        entity.requiresPasscode = ctx.isRequiresPasscode();
        entity.flowType = ctx.getFlowType();
        entity.flowDirection = ctx.getFlowDirection();
        entity.createdAt = ctx.getCreatedAt();
        entity.updatedAt = ctx.getUpdatedAt();
        return entity;
    }

    // --- Getters and Setters ---
    // Used by DataCryptoService (encrypt/decrypt PII), ReconciliationService, etc.

    public String getTid() {
        return tid;
    }

    public String getTr() {
        return tr;
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

    public String getMid() {
        return mid;
    }

    public void setMid(String mid) {
        this.mid = mid;
    }

    public BigDecimal getAm() {
        return am;
    }

    public TransactionState getState() {
        return state;
    }

    public void setState(TransactionState state) {
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

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public FlowDirection getFlowDirection() {
        return flowDirection;
    }

    public void setFlowDirection(FlowDirection flowDirection) {
        this.flowDirection = flowDirection;
    }
}
