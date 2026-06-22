package com.bankingswitch.npciadapter.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NpciCallbackEvent {
    private String txnId;
    private String txnType; // RespBalEnq, RespPay
    private String status; // SUCCESS, FAILED
    private String errorCode;
    private Double balance;
    private String xmlPayload;
}
