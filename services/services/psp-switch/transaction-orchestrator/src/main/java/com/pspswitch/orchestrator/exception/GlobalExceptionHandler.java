package com.pspswitch.orchestrator.exception;

import com.pspswitch.orchestrator.model.TransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for the Transaction Orchestrator.
 * 
 * Catches ValidationException from Step 4 and returns a clean
 * HTTP 400 response with the failure reason and FAILED state.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<TransactionResponse> handleValidationException(ValidationException ex) {
        log.warn("[VALIDATION] FAILED | reason={}", ex.getReason());

        TransactionResponse response = new TransactionResponse();
        response.setState("FAILED");
        response.setFailureReason(ex.getReason());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<TransactionResponse> handleGenericException(Exception ex) {
        log.error("[ORCHESTRATOR] Unexpected error: {}", ex.getMessage(), ex);

        TransactionResponse response = new TransactionResponse();
        response.setState("FAILED");
        response.setFailureReason("Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
