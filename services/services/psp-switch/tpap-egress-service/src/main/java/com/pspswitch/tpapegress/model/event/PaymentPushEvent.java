package com.pspswitch.tpapegress.model.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for PAYMENT_PUSH events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPushEvent {

    private String payerVpa;
    private String payeeVpa;
    private String amount;
    private String currency;
    private String npciRrn;
    private String txnStatus;
    private String failureReason;
}

