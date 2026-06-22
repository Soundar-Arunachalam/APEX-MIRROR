
package com.pspswitch.orchestrator.model;

/**
 * Flow direction for the dual-direction PSP Orchestrator.
 *
 * SEND: Payer Side — TPAP requests money transfer to NPCI.
 * PSP does NOT touch CBS. NPCI handles all debit/credit routing.
 *
 * COLLECT: Receiver Side — NPCI instructs PSP to credit the merchant's CBS.
 * PSP credits the local CBS account. If CBS fails, triggers NPCI reversal.
 */
public enum FlowDirection {
    SEND, // Payer Side PSP (e.g., Google Pay sending money)
    COLLECT // Receiver Side PSP (e.g., Merchant bank crediting payee)
}
