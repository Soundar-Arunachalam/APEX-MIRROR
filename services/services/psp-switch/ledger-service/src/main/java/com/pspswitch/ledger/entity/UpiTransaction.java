package com.pspswitch.ledger.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity for the {@code upi_transactions} table.
 *
 * <p>System-of-record for every UPI transaction through the PSP switch.
 * PII fields (payer_vpa, payee_vpa, merchant_id) are AES-256 encrypted
 * by {@link com.pspswitch.ledger.service.DataCryptoService} before persistence.
 */
@Entity
@Table(name = "upi_transactions", indexes = {
        @Index(name = "idx_upi_txn_ref",      columnList = "txn_ref_id"),
        @Index(name = "idx_upi_status",        columnList = "status"),
        @Index(name = "idx_upi_initiated_at",  columnList = "initiated_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UpiTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tid",           length = 35, nullable = false, unique = true)
    private String tid;

    @Column(name = "txn_ref_id",    length = 35, nullable = false)
    private String txnRefId;

    @Column(name = "rrn",           length = 12)
    private String rrn;

    @Column(name = "approval_number", length = 12)
    private String approvalNumber;

    // PII — AES-256 encrypted
    @Column(name = "payer_vpa",     columnDefinition = "TEXT", nullable = false)
    private String payerVpa;

    @Column(name = "payee_vpa",     columnDefinition = "TEXT", nullable = false)
    private String payeeVpa;

    @Column(name = "payer_name",    columnDefinition = "TEXT")
    private String payerName;

    @Column(name = "payee_name",    columnDefinition = "TEXT")
    private String payeeName;

    @Column(name = "psp_id",        length = 20, nullable = false)
    private String pspId;

    @Column(name = "merchant_code", length = 10)
    private String merchantCode;

    @Column(name = "amount",        precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency",      length = 3, nullable = false)
    private String currency;

    @Column(name = "min_amount",    precision = 15, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "txn_type",      length = 20, nullable = false)
    private String txnType;

    @Column(name = "flow_direction", length = 10, nullable = false)
    private String flowDirection;

    @Column(name = "upi_mode",      length = 5)
    private String upiMode;

    @Column(name = "requires_passcode", nullable = false)
    private boolean requiresPasscode;

    @Column(name = "status",        length = 20, nullable = false)
    private String status;

    @Column(name = "npci_response_code", length = 4)
    private String npciResponseCode;

    @Column(name = "npci_error_code", length = 64)
    private String npciErrorCode;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "npci_msg_id",   length = 35)
    private String npciMsgId;

    @Column(name = "npci_txn_id",   length = 35)
    private String npciTxnId;

    @Column(name = "merchant_id",   columnDefinition = "TEXT")
    private String merchantId;

    @Column(name = "sub_merchant_id", length = 50)
    private String subMerchantId;

    @Column(name = "merchant_txn_id", length = 50)
    private String merchantTxnId;

    @Column(name = "source_service", length = 50)
    private String sourceService;

    @Column(name = "kafka_event_id", length = 100)
    private String kafkaEventId;

    @Column(name = "initiated_at",  nullable = false)
    private Instant initiatedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "updated_at",    nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (initiatedAt == null) initiatedAt = Instant.now();
        updatedAt = Instant.now();
        if (currency == null) currency = "INR";
        if (status == null) status = "PENDING";
        if (txnType == null) txnType = "PAY";
        if (flowDirection == null) flowDirection = "SEND";
        if (pspId == null) pspId = "UNKNOWN";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
