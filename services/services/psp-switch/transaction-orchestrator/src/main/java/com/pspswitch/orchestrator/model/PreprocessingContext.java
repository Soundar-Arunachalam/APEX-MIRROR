package com.pspswitch.orchestrator.model;

/**
 * Result of mode-based preprocessing (Step 3).
 * 
 * Determines whether UPI PIN entry is required and the flow type (MERCHANT vs
 * P2P).
 * Mode affects only these preprocessing flags — the core saga is identical for
 * all modes.
 */
public class PreprocessingContext {

    private boolean requiresPasscode;
    private String flowType; // "MERCHANT" or "P2P"

    public PreprocessingContext() {
    }

    public PreprocessingContext(boolean requiresPasscode, String flowType) {
        this.requiresPasscode = requiresPasscode;
        this.flowType = flowType;
    }

    public boolean isRequiresPasscode() {
        return requiresPasscode;
    }

    public void setRequiresPasscode(boolean requiresPasscode) {
        this.requiresPasscode = requiresPasscode;
    }

    public String getFlowType() {
        return flowType;
    }

    public void setFlowType(String flowType) {
        this.flowType = flowType;
    }
}
