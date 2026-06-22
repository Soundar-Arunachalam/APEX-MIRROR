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
public class TransactionStatus {
    private String txnId;
    private String correlationId;
    private String eventType;
    private String currentStatus;
    private String tpapId;
    private String createdAt;
    private String updatedAt;
    private Object resultPayload;
}
