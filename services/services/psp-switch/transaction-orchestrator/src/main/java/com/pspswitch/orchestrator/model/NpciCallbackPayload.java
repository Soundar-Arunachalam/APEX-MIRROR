package com.pspswitch.orchestrator.model;

/**
 * NPCI Webhook Callback Payload — received at POST /api/v1/webhook/npci.
 * 
 * Simulates the NPCI calling back with the final transaction result
 * after processing the payer's UPI PIN authorisation.
 * The 'tid' is echoed back as the correlation ID.
 */
public class NpciCallbackPayload {

    private String tid; // Transaction ID (echoed from original request)
    private String responseCode; // "00" = SUCCESS, "ZM" = FAILED
    private String approvalRefNo; // Approval reference (only on success)
    private String status; // "SUCCESS" or "FAILED"

    public NpciCallbackPayload() {
    }

    public NpciCallbackPayload(String tid, String responseCode, String approvalRefNo, String status) {
        this.tid = tid;
        this.responseCode = responseCode;
        this.approvalRefNo = approvalRefNo;
        this.status = status;
    }

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getApprovalRefNo() {
        return approvalRefNo;
    }

    public void setApprovalRefNo(String approvalRefNo) {
        this.approvalRefNo = approvalRefNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
