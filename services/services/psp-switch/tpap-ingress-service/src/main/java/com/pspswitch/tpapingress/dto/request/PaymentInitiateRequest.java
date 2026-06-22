package com.pspswitch.tpapingress.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class PaymentInitiateRequest {
    private String txnId;
    private String payerVpa;
    private String payeeVpa;
    private String amount;
    private String currency;
    private String encryptedPin;
    private String deviceFingerprint;
    private String txnType;
    private String mcc;
    private String remarks;
    private String expiry;
    private String payeeName;
    private String payerName;
}
