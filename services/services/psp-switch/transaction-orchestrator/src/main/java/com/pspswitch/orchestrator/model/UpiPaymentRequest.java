package com.pspswitch.orchestrator.model;

import java.math.BigDecimal;

/**
 * UPI Payment Request DTO — received from upstream ingress layer.
 * 
 * All monetary values use BigDecimal for financial precision.
 * The 'tid' is NOT part of the request — the orchestrator always generates it.
 * The 'isSignatureVerified' flag is set by the upstream security layer.
 */
public class UpiPaymentRequest {

    private String tr; // Transaction reference / order ID
    private String pa; // Payee UPI ID
    private String pn; // Payee name
    private String mc; // Merchant category code (0000=P2P)
    private BigDecimal am; // Amount (2 decimal places)
    private BigDecimal mam; // Minimum amount (nullable)
    private String cu; // Currency (always INR)
    private String mode; // Transaction mode (04, 05, 16)
    private String mid; // Merchant ID
    private String msid; // Store ID
    private String mtid; // Terminal ID
    private boolean isSignatureVerified; // Set by upstream ingress layer
    private String flowDirection; // "SEND" or "COLLECT" (defaults to SEND)
    private String tid; // TPAP generated Transaction ID

    public UpiPaymentRequest() {
    }

    // --- Getters and Setters ---

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

    public boolean isSignatureVerified() {
        return isSignatureVerified;
    }

    public void setSignatureVerified(boolean signatureVerified) {
        isSignatureVerified = signatureVerified;
    }

    public String getFlowDirection() {
        return flowDirection;
    }

    public void setFlowDirection(String flowDirection) {
        this.flowDirection = flowDirection;
    }

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }
}
