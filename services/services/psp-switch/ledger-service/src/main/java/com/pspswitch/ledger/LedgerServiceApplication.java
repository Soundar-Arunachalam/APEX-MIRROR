package com.pspswitch.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PSP Switch Ledger Service.
 *
 * <p>System-of-record for all UPI transactions flowing through the PSP switch.
 * Consumes events from:
 * <ul>
 *   <li>{@code npci.inbound.response} — NPCI result events</li>
 *   <li>{@code psp.switch.completed.events} — completed switch events for final settlement</li>
 * </ul>
 *
 * <p>Provides:
 * <ul>
 *   <li>Immutable audit trail via {@code txn_status_events}</li>
 *   <li>Double-entry bookkeeping via {@code ledger_entries}</li>
 *   <li>Transaction query REST API</li>
 * </ul>
 */
@SpringBootApplication
public class LedgerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerServiceApplication.class, args);
    }
}
