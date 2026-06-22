package com.pspswitch.tpapingress.controller;

import com.pspswitch.tpapingress.dto.request.BalanceInquiryRequest;
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

import static com.pspswitch.tpapingress.controller.VpaLookupController.*;

@RestController
@RequestMapping("/tpap/api/v1")
@RequiredArgsConstructor
public class BalanceInquiryController {

    private final KafkaPublisherService kafkaPublisher;
    private final IdempotencyService idempotencyService;

    private static final String MOBILE_PATTERN = "^[6-9]\\d{9}$";

    @PostMapping("/balance/inquiry")
    public ResponseEntity<AcceptedResponse> inquiry(@RequestBody BalanceInquiryRequest request,
                                                     HttpServletRequest httpRequest) {
        String tpapId = (String) httpRequest.getAttribute("tpapId");

        // Validate required fields
        validateRequired(request.getTxnId(), "txnId");
        validateRequired(request.getVpa(), "vpa");
        validateRequired(request.getEncryptedPin(), "encryptedPin");
        validateRequired(request.getDeviceFingerprint(), "deviceFingerprint");

        // Validate txnId format
        validateTxnId(request.getTxnId(), tpapId);

        // Validate VPA format
        validateVpa(request.getVpa());

        // Validate optional mobile number
        if (request.getMobileNumber() != null && !request.getMobileNumber().isEmpty()) {
            if (!request.getMobileNumber().matches(MOBILE_PATTERN)) {
                throw new RequestValidationException("INVALID_MOBILE_NUMBER",
                        "Mobile number must match ^[6-9]\\d{9}$", "mobileNumber");
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
        kafkaPublisher.publishBalanceInquiry(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
