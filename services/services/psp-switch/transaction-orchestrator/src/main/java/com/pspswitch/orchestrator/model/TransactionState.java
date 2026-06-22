package com.pspswitch.orchestrator.model;

/**
 * Transaction lifecycle states for the UPI PSP Switch orchestration saga.
 *
 * State machine:
 * PENDING → SUBMITTED → SUCCESS
 * → FAILED
 * → UNKNOWN (timeout)
 * → COMPENSATED (COLLECT flow: CBS failure after NPCI success)
 *
 * Note: DUPLICATE is NOT a state — it's a response behaviour handled by
 * IdempotencyService.
 */
public enum TransactionState {
    PENDING, // Written before any external call
    SUBMITTED, // NPCI REST call made, waiting for webhook callback
    SUCCESS, // NPCI webhook confirmed + ledger written (SEND) or CBS credited (COLLECT)
    FAILED, // Validation failed OR NPCI webhook returned failure
    UNKNOWN, // NPCI webhook never arrived within timeout
    COMPENSATED // COLLECT only: CBS failed after NPCI SUCCESS → reversal sent to NPCI
}
