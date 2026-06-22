package com.bankingswitch.orchestrator.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.bankingswitch.orchestrator.model.TransactionState;

@Entity
@Table(name = "transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEntity {
    @Id
    private String txnId;
    private String txnType;
    private TransactionState state;
    private String payerVpa;
    private String payeeVpa;
    private Double amount;
    private String xmlPayload;
    private long createdAt;
    private long updatedAt;
}
