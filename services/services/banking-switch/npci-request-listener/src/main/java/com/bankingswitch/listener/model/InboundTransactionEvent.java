package com.bankingswitch.listener.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundTransactionEvent {
    private String txnId;
    private String txnType; // ReqBalEnq, ReqPay
    private String xmlPayload;
    private long timestamp;
}
