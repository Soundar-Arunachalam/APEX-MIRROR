package com.pspswitch.tpapegress.model.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for VPA_VERIFICATION events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VpaVerificationEvent {

    private String  vpa;
    private String  accountHolderName;
    private String  bankName;
    private boolean verified;
    private String  failureReason;
}

