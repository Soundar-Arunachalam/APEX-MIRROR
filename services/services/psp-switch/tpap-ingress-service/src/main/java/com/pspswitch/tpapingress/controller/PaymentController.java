package com.pspswitch.tpapingress.controller;

import com.pspswitch.tpapingress.dto.request.PaymentInitiateRequest;
import com.pspswitch.tpapingress.dto.response.AcceptedResponse;
import com.pspswitch.tpapingress.exception.RequestValidationException;
import com.pspswitch.tpapingress.service.IdempotencyService;
import com.pspswitch.tpapingress.service.KafkaPublisherService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

import static com.pspswitch.tpapingress.controller.VpaLookupController.*;

@RestController
@RequestMapping("/tpap/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final KafkaPublisherService kafkaPublisher;
    private final IdempotencyService idempotencyService;

    private static final String AMOUNT_PATTERN = "^\\d+(\\.\\d{1,2})?$";
    private static final String REMARKS_PATTERN = "^[a-zA-Z0-9 ]*$";
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("1.00");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000.00");

    @PostMapping("/payment/initiate")
    public ResponseEntity<AcceptedResponse> initiate(@RequestBody PaymentInitiateRequest request,
                                                      HttpServletRequest httpRequest) {
        String tpapId = (String) httpRequest.getAttribute("tpapId");

        // Validate txnId (null → MISSING, empty/bad format → INVALID_TXN_ID_FORMAT)
        if (request.getTxnId() == null) {
            throw new RequestValidationException("MISSING_REQUIRED_FIELD",
                    "Field 'txnId' is required", "txnId");
        }
        validateTxnId(request.getTxnId(), tpapId);

        // Validate other required fields
        validateRequired(request.getPayerVpa(), "payerVpa");
        validateRequired(request.getPayeeVpa(), "payeeVpa");
        validateRequired(request.getCurrency(), "currency");
        validateRequired(request.getEncryptedPin(), "encryptedPin");
        validateRequired(request.getDeviceFingerprint(), "deviceFingerprint");
        validateRequired(request.getTxnType(), "txnType");

        // Validate VPAs
        validateVpa(request.getPayerVpa());
        validateVpa(request.getPayeeVpa());

        // Payer != Payee
        if (request.getPayerVpa().equalsIgnoreCase(request.getPayeeVpa())) {
            throw new RequestValidationException("PAYER_PAYEE_SAME",
                    "Payer VPA must differ from Payee VPA");
        }

        // Validate amount (null → MISSING, empty/invalid → INVALID_AMOUNT)
        if (request.getAmount() == null) {
            throw new RequestValidationException("MISSING_REQUIRED_FIELD",
                    "Field 'amount' is required", "amount");
        }
        validateAmount(request.getAmount());

        // Validate currency
        if (!"INR".equals(request.getCurrency())) {
            throw new RequestValidationException("INVALID_CURRENCY",
                    "Currency must be INR", "currency");
        }

        // Validate txnType and conditional MCC
        if (!"PEER_TO_PEER".equals(request.getTxnType()) &&
                !"MERCHANT_PAYMENT".equals(request.getTxnType())) {
            throw new RequestValidationException("MISSING_REQUIRED_FIELD",
                    "txnType must be PEER_TO_PEER or MERCHANT_PAYMENT", "txnType");
        }
        if ("MERCHANT_PAYMENT".equals(request.getTxnType())) {
            if (request.getMcc() == null || request.getMcc().isEmpty()) {
                throw new RequestValidationException("INVALID_MCC",
                        "MCC is required for MERCHANT_PAYMENT", "mcc");
            }
            validateMcc(request.getMcc());
        }

        // Validate optional remarks
        if (request.getRemarks() != null && !request.getRemarks().isEmpty()) {
            if (request.getRemarks().length() > 50) {
                throw new RequestValidationException("INVALID_REMARKS",
                        "Remarks must not exceed 50 characters", "remarks");
            }
            if (!request.getRemarks().matches(REMARKS_PATTERN)) {
                throw new RequestValidationException("INVALID_REMARKS",
                        "Remarks must contain only alphanumeric characters and spaces", "remarks");
            }
        }

        // Validate optional expiry
        if (request.getExpiry() != null && !request.getExpiry().isEmpty()) {
            try {
                OffsetDateTime expiry = OffsetDateTime.parse(request.getExpiry());
                if (expiry.toInstant().isBefore(Instant.now())) {
                    throw new RequestValidationException("INVALID_EXPIRY",
                            "Expiry timestamp must be in the future", "expiry");
                }
            } catch (DateTimeParseException e) {
                // Try ISO instant format
                try {
                    Instant expiry = Instant.parse(request.getExpiry());
                    if (expiry.isBefore(Instant.now())) {
                        throw new RequestValidationException("INVALID_EXPIRY",
                                "Expiry timestamp must be in the future", "expiry");
                    }
                } catch (DateTimeParseException ex) {
                    throw new RequestValidationException("INVALID_EXPIRY",
                            "Expiry must be a valid ISO-8601 timestamp", "expiry");
                }
            }
        }

        // Idempotency check
        String idempotencyKey = IdempotencyService.computeKey(tpapId, request.getTxnId());
        Optional<AcceptedResponse> cached = idempotencyService.isDuplicate(idempotencyKey);
        if (cached.isPresent()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(cached.get());
        }

        // Build response
        AcceptedResponse response = AcceptedResponse.builder()
                .txnId(request.getTxnId())
                .correlationId(UUID.randomUUID().toString())
                .status("ACCEPTED")
                .message("Request accepted and queued for processing")
                .acceptedAt(Instant.now().toString())
                .idempotentReplay(false)
                .build();

        // Persist + publish
        idempotencyService.persist(idempotencyKey, response);
        kafkaPublisher.publishPaymentInitiate(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    private void validateAmount(String amountStr) {
        if (!amountStr.matches(AMOUNT_PATTERN)) {
            throw new RequestValidationException("INVALID_AMOUNT",
                    "Amount must be a positive number with at most 2 decimal places", "amount");
        }
        try {
            BigDecimal amount = new BigDecimal(amountStr);
            if (amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
                throw new RequestValidationException("INVALID_AMOUNT",
                        "Amount must be between 1.00 and 100000.00", "amount");
            }
        } catch (NumberFormatException e) {
            throw new RequestValidationException("INVALID_AMOUNT",
                    "Amount must be a valid number", "amount");
        }
    }
}
