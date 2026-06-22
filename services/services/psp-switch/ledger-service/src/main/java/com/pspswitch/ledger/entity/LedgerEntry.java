package com.pspswitch.ledger.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Double-entry bookkeeping ledger entry.
 * One DEBIT and one CREDIT entry are created per successfully settled transaction.
 */
@Entity
@Table(name = "ledger_entries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tid",           length = 35, nullable = false)
    private String tid;

    /** DEBIT | CREDIT | REVERSAL */
    @Column(name = "entry_type",    length = 10, nullable = false)
    private String entryType;

    /** AES-256 encrypted VPA of the account. */
    @Column(name = "account_vpa",   columnDefinition = "TEXT", nullable = false)
    private String accountVpa;

    @Column(name = "amount",        precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency",      length = 3, nullable = false)
    private String currency;

    @Column(name = "rrn",           length = 12)
    private String rrn;

    @Column(name = "approval_number", length = 12)
    private String approvalNumber;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "batch_id",      length = 50)
    private String batchId;

    @Column(name = "settled",       nullable = false)
    private boolean settled;

    @Column(name = "recorded_at",   nullable = false)
    private Instant recordedAt;

    @PrePersist
    public void prePersist() {
        if (recordedAt == null) recordedAt = Instant.now();
        if (currency == null) currency = "INR";
    }
}
