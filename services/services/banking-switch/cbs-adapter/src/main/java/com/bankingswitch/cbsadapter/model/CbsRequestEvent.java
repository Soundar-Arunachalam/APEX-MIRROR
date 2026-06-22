package com.bankingswitch.cbsadapter.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CbsRequestEvent {
    private String txnId;
    private String operation; // BALANCE, DEBIT, CREDIT
    private String vpa;
    private Double amount;
    private String xmlPayload;
}
