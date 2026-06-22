package com.pspswitch.orchestrator.service;

import com.pspswitch.orchestrator.exception.ValidationException;
import com.pspswitch.orchestrator.model.UpiPaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Validation Service — Step 4 of the orchestration saga.
 * 
 * Runs 9 sequential validation rules against the UPI payment request.
 * First failure throws ValidationException — caught by GlobalExceptionHandler →
 * HTTP 400.
 * 
 * Rules are ordered by business priority: amount checks first, then identity,
 * then mode-specific.
 */
@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    /**
     * Validates the UPI payment request. Throws ValidationException on first
     * failure.
     *
     * @param request the incoming payment request
     * @param tid     the generated transaction ID (for logging)
     */
    public void validate(UpiPaymentRequest request, String tid) {

        // Rule 1: Amount must be present and positive
        if (request.getAm() == null || request.getAm().compareTo(BigDecimal.ZERO) <= 0) {
            fail(tid, "Amount must be greater than zero");
        }

        // Rule 2: Amount must have at most 2 decimal places
        if (request.getAm() != null && request.getAm().scale() > 2) {
            fail(tid, "Amount must have at most 2 decimal places");
        }

        // Rule 3: If mam is set, amount must be >= mam
        if (request.getMam() != null && request.getAm() != null
                && request.getAm().compareTo(request.getMam()) < 0) {
            fail(tid, "Amount " + request.getAm() + " is below minimum " + request.getMam());
        }

        // Rule 4: Payee UPI ID is mandatory
        if (isBlank(request.getPa())) {
            fail(tid, "Payee UPI ID is mandatory");
        }

        // Rule 5: Payee name is mandatory
        if (isBlank(request.getPn())) {
            fail(tid, "Payee name is mandatory");
        }

        // Rule 6: Merchant category code is mandatory
        if (isBlank(request.getMc())) {
            fail(tid, "Merchant category code is mandatory");
        }

        // Rule 7: Currency must be INR
        if (!"INR".equals(request.getCu())) {
            fail(tid, "Only INR supported");
        }

        // Rule 8: Transaction reference is mandatory
        if (isBlank(request.getTr())) {
            fail(tid, "Transaction reference is mandatory");
        }

        // Rule 9: For mode 16, merchant terminal IDs are mandatory
        if ("16".equals(request.getMode())) {
            if (isBlank(request.getMid()) || isBlank(request.getMsid()) || isBlank(request.getMtid())) {
                fail(tid, "Merchant terminal IDs mandatory for mode 16");
            }
        }

        log.info("[VALIDATION] tid={} | ALL_CHECKS_PASSED", tid);
    }

    private void fail(String tid, String reason) {
        log.warn("[VALIDATION] tid={} | FAILED | reason={}", tid, reason);
        throw new ValidationException(reason);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
