package com.pspswitch.orchestrator.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA Entity for persisting ledger entries in PostgreSQL.
 *
 * Records completed transactions with all reconciliation fields.
 * Each entry represents a successfully settled payment.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntity {

    @Id
    @Column(name = "tid", length = 20)
    private String tid;

    @Column(name = "tr", nullable = false, length = 50)
    private String tr;

    @Column(name = "pa", nullable = false, length = 100)
    private String pa;

    @Column(name = "pn", length = 100)
    private String pn;

    @Column(name = "am", precision = 15, scale = 2)
    private BigDecimal am;

    @Column(name = "cu", length = 5)
    private String cu;

    @Column(name = "approval_ref_no", length = 20)
    private String approvalRefNo;

    @Column(name = "response_code", length = 10)
    private String responseCode;

    @Column(name = "mid", length = 50)
    private String mid;

    @Column(name = "msid", length = 50)
    private String msid;

    @Column(name = "mtid", length = 50)
    private String mtid;

    @Column(name = "settled_at")
    private Instant settledAt;

    public LedgerEntity() {
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

    public BigDecimal getAm() {
        return am;
    }

    public void setAm(BigDecimal am) {
        this.am = am;
    }

    public String getCu() {
        return cu;
    }

    public void setCu(String cu) {
        this.cu = cu;
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

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }
}
