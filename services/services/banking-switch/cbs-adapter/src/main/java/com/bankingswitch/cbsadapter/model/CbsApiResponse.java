package com.bankingswitch.cbsadapter.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CbsApiResponse {
    private String status;
    private String errorCode;
    private Double balance;
}
