package com.bankingswitch.cbsadapter.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CbsResponseEvent {
    private String txnId;
    private String operation; // BALANCE, DEBIT, CREDIT
    private String status; // SUCCESS, FAILED
    private String errorCode;
    private Double balance;
    private String xmlPayload;
}
