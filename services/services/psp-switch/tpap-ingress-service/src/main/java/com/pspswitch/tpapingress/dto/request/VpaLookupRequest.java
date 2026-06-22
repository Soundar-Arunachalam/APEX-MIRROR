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
public class VpaLookupRequest {
    private String txnId;
    private String phoneNumber;
    private String requesterVpa;
    private String mcc;
}
