package com.pspswitch.tpapingress.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AcceptedResponse {
    private String txnId;
    private String correlationId;
    private String status;
    private String message;
    private String acceptedAt;
    private Boolean idempotentReplay;
}
