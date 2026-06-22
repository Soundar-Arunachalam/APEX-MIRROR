package com.bankingswitch.orchestrator.model;

public enum TransactionState {
    RECEIVED,
    CBS_PENDING,
    CBS_SUCCESS,
    CBS_FAILED,
    SUCCESS,
    FAILED
}
