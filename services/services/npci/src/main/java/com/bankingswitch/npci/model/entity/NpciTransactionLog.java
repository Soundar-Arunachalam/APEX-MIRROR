package com.bankingswitch.npci.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "transaction_log")
@Data
public class NpciTransactionLog {
    @Id
    private String txnId;
    private String txnType;
    private String payerVpa;
    private String payeeVpa;
    private BigDecimal amount;
    private String status;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    private String requestXml;
    private String responseXml;
}
