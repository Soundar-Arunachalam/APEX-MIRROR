package com.bankingswitch.cbsadapter.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CbsOperationRequest {
    private String txnId;
    private String vpa;
    private Double amount;
}
