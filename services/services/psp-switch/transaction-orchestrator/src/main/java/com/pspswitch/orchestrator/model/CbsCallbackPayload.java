package com.pspswitch.orchestrator.model;

import java.time.Instant;

/**
 * CBS Webhook Callback Payload — received at POST /api/v1/webhook/cbs.
 * 
 * Informational confirmation from CBS after credit has been processed.
 * By the time this arrives, the transaction is already in SUCCESS state.
 */
public class CbsCallbackPayload {

    private String tid; // Transaction ID
    private String status; // "CREDITED"
    private String timestamp; // ISO timestamp string

    public CbsCallbackPayload() {
    }

    public CbsCallbackPayload(String tid, String status, String timestamp) {
        this.tid = tid;
        this.status = status;
        this.timestamp = timestamp;
    }

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
