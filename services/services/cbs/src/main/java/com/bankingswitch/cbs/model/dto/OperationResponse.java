package com.bankingswitch.cbs.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationResponse {
    private String status;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String message;
}
