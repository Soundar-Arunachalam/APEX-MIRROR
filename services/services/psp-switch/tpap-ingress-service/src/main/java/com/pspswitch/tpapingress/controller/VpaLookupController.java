package com.pspswitch.tpapingress.controller;

import com.pspswitch.tpapingress.dto.request.VpaLookupRequest;
import com.pspswitch.tpapingress.dto.response.AcceptedResponse;
import com.pspswitch.tpapingress.exception.RequestValidationException;
import com.pspswitch.tpapingress.service.IdempotencyService;
import com.pspswitch.tpapingress.service.KafkaPublisherService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/tpap/api/v1")
@RequiredArgsConstructor
public class VpaLookupController {

    private final KafkaPublisherService kafkaPublisher;
    private final IdempotencyService idempotencyService;

    private static final String VPA_PATTERN = "^[a-zA-Z0-9.\\-]+@[a-zA-Z0-9]+$";
    private static final String TXN_ID_PATTERN =
            "^[a-zA-Z0-9]+-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    private static final String MCC_PATTERN = "^\\d{4}$";
    private static final String PHONE_PATTERN = "^[1-9][0-9]{9}$";

    @PostMapping("/vpa/lookup")
    public ResponseEntity<AcceptedResponse> lookup(@RequestBody VpaLookupRequest request,
                                                    HttpServletRequest httpRequest) {
        String tpapId = (String) httpRequest.getAttribute("tpapId");

        // Validate txnId (null → MISSING_REQUIRED_FIELD, empty/invalid format → INVALID_TXN_ID_FORMAT)
        if (request.getTxnId() == null) {
            throw new RequestValidationException("MISSING_REQUIRED_FIELD",
                    "Field 'txnId' is required", "txnId");
        }
        validateTxnId(request.getTxnId(), tpapId);

        // Validate required fields
        validateRequired(request.getPhoneNumber(), "phoneNumber");
        validateRequired(request.getRequesterVpa(), "requesterVpa");

        // Validate txnId format and prefix is already done above

        // Validate formats
        validatePhoneNumber(request.getPhoneNumber());
        validateVpa(request.getRequesterVpa());

        // Validate optional MCC
        if (request.getMcc() != null) {
            validateMcc(request.getMcc());
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

        // Persist idempotency (must succeed before Kafka publish)
        idempotencyService.persist(idempotencyKey, response);

        // Publish to Kafka
        kafkaPublisher.publishVpaLookup(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    // ── Validation helpers ──

    static void validateRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new RequestValidationException("MISSING_REQUIRED_FIELD",
                    "Field '" + fieldName + "' is required", fieldName);
        }
    }

    static void validateTxnId(String txnId, String tpapId) {
        if (txnId.isEmpty() || !txnId.matches(TXN_ID_PATTERN)) {
            throw new RequestValidationException("INVALID_TXN_ID_FORMAT",
                    "txnId must be in format {tpapName}-{UUID}");
        }
        String prefix = txnId.substring(0, txnId.indexOf('-'));
        if (!prefix.equalsIgnoreCase(tpapId)) {
            throw new RequestValidationException("INVALID_TXN_ID_FORMAT",
                    "txnId prefix must match authenticated TPAP ID");
        }
    }

    static void validateVpa(String vpa) {
        if (vpa.length() > 255 || !vpa.matches(VPA_PATTERN)) {
            throw new RequestValidationException("INVALID_VPA_FORMAT",
                    "VPA must match pattern localPart@handle", "vpa");
        }
    }

    static void validateMcc(String mcc) {
        if (!mcc.matches(MCC_PATTERN)) {
            throw new RequestValidationException("INVALID_MCC",
                    "MCC must be exactly 4 numeric digits", "mcc");
        }
    }

    static void validatePhoneNumber(String phoneNumber) {
        if (!phoneNumber.matches(PHONE_PATTERN)) {
            throw new RequestValidationException("INVALID_MOBILE_NUMBER",
                    "Phone number must be a valid 10-digit number", "phoneNumber");
        }
    }
}
