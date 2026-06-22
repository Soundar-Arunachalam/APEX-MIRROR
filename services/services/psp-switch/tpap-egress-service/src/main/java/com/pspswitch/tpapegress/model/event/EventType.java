package com.pspswitch.tpapegress.model.event;

/**
 * Represents the categories of switch events flowing through the egress service.
 *
 * @since 1.0
 */
public enum EventType {
    /** Denotes a payment transfer request dispatched to an account or wallet. */
    PAYMENT_PUSH,
    
    /** Denotes a request aiming to check account balance constraints. */
    BALANCE_INQUIRY,
    
    /** Denotes a confirmation request validating recipient VPA mapping. */
    VPA_VERIFICATION
}
