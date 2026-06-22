package com.pspswitch.tpapegress.dispatcher.handler;

import com.pspswitch.tpapegress.client.WebhookHttpClient;
import com.pspswitch.tpapegress.exception.WebhookDeliveryException;
import com.pspswitch.tpapegress.model.entity.DeliveryLog;
import com.pspswitch.tpapegress.model.entity.WebhookConfig;
import com.pspswitch.tpapegress.model.event.EventType;
import com.pspswitch.tpapegress.model.event.SwitchCompletedEvent;
import com.pspswitch.tpapegress.model.event.VpaVerificationEvent;
import com.pspswitch.tpapegress.model.payload.VpaVerificationWebhookPayload;
import com.pspswitch.tpapegress.model.payload.WebhookPayload;
import com.pspswitch.tpapegress.repository.DeliveryLogRepository;
import com.pspswitch.tpapegress.repository.WebhookConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

/**
 * Handles VPA_VERIFICATION events.
 * Dispatches payload built for the VPA verification context to the target TPAP URL via webhook client.
 *
 * Retries up to 3 times on 5xx or timeout. Does not retry on 4xx (see ADR-002).
 * Returns silently without throwing if no active config is found — see ADR-003.
 *
 * @since 1.0
 */
@Component
@Slf4j
public class VpaVerificationHandler implements WebhookEventHandler {

    private static final int MAX_RETRIES = 5;

    private final WebhookConfigRepository configRepo;
    private final DeliveryLogRepository   deliveryLogRepo;
    private final WebhookHttpClient       httpClient;
    private final ObjectMapper            objectMapper;

    /**
     * Constructs the handler injecting required repositories and client.
     *
     * @param configRepo repository to locate registered TPAP configs
     * @param deliveryLogRepo repository to log attempt statistics and details
     * @param httpClient Webhook HTTP client to POST outbound envelopes
     */
    public VpaVerificationHandler(WebhookConfigRepository configRepo,
                                  DeliveryLogRepository deliveryLogRepo,
                                  WebhookHttpClient httpClient,
                                  ObjectMapper objectMapper) {
        this.configRepo      = configRepo;
        this.deliveryLogRepo = deliveryLogRepo;
        this.httpClient      = httpClient;
        this.objectMapper    = objectMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventType supportedType() {
        return EventType.VPA_VERIFICATION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handle(SwitchCompletedEvent event) {

        Optional<WebhookConfig> configOpt =
                configRepo.findActiveConfig(event.getTpapId(), supportedType());

        if (configOpt.isEmpty() || !configOpt.get().isActive()) {
            log.info("SKIPPED webhook for tpapId={} eventType={}", event.getTpapId(), supportedType());
            deliveryLogRepo.save(DeliveryLog.builder()
                    .eventId(event.getEventId())
                    .txnId(event.getTxnId())
                    .tpapId(event.getTpapId())
                    .eventType(supportedType())
                    .webhookUrl("N/A")
                    .status("SKIPPED")
                    .httpStatus(null)
                    .attemptNumber(0)
                    .deliveredAt(Instant.now())
                    .build());
            return;
        }

        WebhookConfig  config  = configOpt.get();
        WebhookPayload payload = buildPayload(event);

        int     attempt          = 0;
        Integer lastHttpStatus   = null;
        String  lastErrorMessage = null;
        boolean success          = false;
        int     maxTotalAttempts = 1 + MAX_RETRIES;

        for (int i = 0; i < maxTotalAttempts; i++) {
            attempt++;
            try {
                int httpStatus = httpClient.post(config.getUrl(), payload);
                lastHttpStatus   = httpStatus;
                lastErrorMessage = null;

                if (httpStatus >= 200 && httpStatus < 300) {
                    success = true;
                    break;
                } else if (httpStatus >= 400 && httpStatus < 500) {
                    break;
                }
            } catch (WebhookDeliveryException e) {
                lastErrorMessage = e.getMessage();
                log.warn("Attempt {}/{} failed for tpapId={}: {}",
                        attempt, maxTotalAttempts, event.getTpapId(), e.getMessage());
            }
        }

        deliveryLogRepo.save(DeliveryLog.builder()
                .eventId(event.getEventId())
                .txnId(event.getTxnId())
                .tpapId(event.getTpapId())
                .eventType(supportedType())
                .webhookUrl(config.getUrl())
                .status(success ? "SUCCESS" : "FAILED")
                .httpStatus(lastHttpStatus)
                .attemptNumber(attempt)
                .errorMessage(lastErrorMessage)
                .deliveredAt(Instant.now())
                .build());
    }

    private WebhookPayload buildPayload(SwitchCompletedEvent event) {
        VpaVerificationEvent data = objectMapper.convertValue(event.getPayload(), VpaVerificationEvent.class);
        return VpaVerificationWebhookPayload.builder()
                .eventId(event.getEventId())
                .eventType(supportedType().name())
                .tpapId(event.getTpapId())
                .txnId(event.getTxnId())
                .correlationId(event.getCorrelationId())
                .deliveredAt(Instant.now())
                .vpa(data.getVpa())
                .accountHolderName(data.getAccountHolderName())
                .bankName(data.getBankName())
                .verified(data.isVerified())
                .failureReason(data.getFailureReason())
                .build();
    }
}
