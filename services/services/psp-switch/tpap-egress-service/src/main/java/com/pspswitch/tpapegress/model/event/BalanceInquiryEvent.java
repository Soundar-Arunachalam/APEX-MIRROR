package com.pspswitch.tpapegress.model.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for BALANCE_INQUIRY events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceInquiryEvent {

    private String vpa;
    private String availableBalance;
    private String currency;
    private String inquiryStatus;
}

