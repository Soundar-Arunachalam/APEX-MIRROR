package com.bankingswitch.cbs.model.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditRequest {
    private String vpa;
    private BigDecimal amount;
    private String txnId;
}
