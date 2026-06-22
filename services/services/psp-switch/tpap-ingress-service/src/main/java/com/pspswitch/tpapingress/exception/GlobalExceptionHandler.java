package com.pspswitch.tpapingress.exception;

import com.pspswitch.tpapingress.dto.response.ErrorDetail;
import com.pspswitch.tpapingress.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Global exception handler mapping custom exceptions to error responses.
 * Never exposes internal exception messages or stack traces.
 * See architecture_spec.md Section 7.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(RequestValidationException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .correlationId(UUID.randomUUID().toString())
                .timestamp(Instant.now().toString())
                .details(ex.getField() != null ?
                        List.of(ErrorDetail.builder().field(ex.getField()).issue(ex.getMessage()).build()) :
                        null)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(KafkaUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleKafkaUnavailable(KafkaUnavailableException ex) {
        log.error("Kafka broker unavailable", ex);
        return buildError(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "Service temporarily unavailable");
    }

    @ExceptionHandler(KafkaPublishFailureException.class)
    public ResponseEntity<ErrorResponse> handleKafkaPublishFailure(KafkaPublishFailureException ex) {
        log.error("Kafka publish failed", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "KAFKA_PUBLISH_FAILURE",
                "Failed to publish event after retries");
    }

    @ExceptionHandler(IdempotencyStoreException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyStore(IdempotencyStoreException ex) {
        log.error("Idempotency store error", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCY_STORE_ERROR",
                "Unable to access idempotency store");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex) {
        ErrorResponse error = ErrorResponse.builder()
                .errorCode("RATE_LIMIT_EXCEEDED")
                .message(ex.getMessage())
                .correlationId(UUID.randomUUID().toString())
                .timestamp(Instant.now().toString())
                .build();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")
                .body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        return buildError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY",
                "Request body is missing or contains malformed JSON");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return buildError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Content-Type must be application/json");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        // Never leak internal details
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An internal error occurred. Please retry.");
    }

    private ResponseEntity<ErrorResponse> buildError(HttpStatus status, String code, String message) {
        ErrorResponse error = ErrorResponse.builder()
                .errorCode(code)
                .message(message)
                .correlationId(UUID.randomUUID().toString())
                .timestamp(Instant.now().toString())
                .build();
        return ResponseEntity.status(status).body(error);
    }
}
