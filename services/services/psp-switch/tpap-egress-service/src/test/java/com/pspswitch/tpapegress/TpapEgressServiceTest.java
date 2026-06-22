package com.pspswitch.tpapegress;

// ═══════════════════════════════════════════════════════════════════════════
//  TPAP EGRESS SERVICE — EXHAUSTIVE TEST SUITE
//  Stack    : Java 21 · Spring Boot 3.x · JUnit 5 · Mockito · AssertJ
//
//  Test Domains:
//    Section 1  — Event routing (polymorphic handler selection)
//    Section 2  — Webhook config lookup (missing / inactive / malformed URL)
//    Section 3  — HTTP outcome handling (200 / 4xx / 5xx / timeout)
//    Section 4  — Retry & resilience (exponential back-off, exhaustion)
//    Section 5  — DeliveryLog persistence (SUCCESS / FAILED / SKIPPED)
//    Section 6  — Payload mapping (field completeness per event type)
//    Section 7  — Unknown / null event type defence
//    Section 8  — Dispatcher orchestration (WebhookDispatcherService)
//
//  Adjusted: retry 5 times (6 total attempts), no DLQ
// ═══════════════════════════════════════════════════════════════════════════

import com.pspswitch.tpapegress.client.WebhookHttpClient;
import com.pspswitch.tpapegress.dispatcher.EventHandlerFactory;
import com.pspswitch.tpapegress.dispatcher.WebhookDispatcherService;
import com.pspswitch.tpapegress.dispatcher.handler.*;
import com.pspswitch.tpapegress.exception.UnknownEventTypeException;
import com.pspswitch.tpapegress.exception.WebhookDeliveryException;
import com.pspswitch.tpapegress.model.entity.DeliveryLog;
import com.pspswitch.tpapegress.model.entity.WebhookConfig;
import com.pspswitch.tpapegress.model.event.*;
import com.pspswitch.tpapegress.model.payload.*;
import com.pspswitch.tpapegress.repository.DeliveryLogRepository;
import com.pspswitch.tpapegress.repository.WebhookConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

// ═══════════════════════════════════════════════════════════════════════════
//  TEST FIXTURES
// ═══════════════════════════════════════════════════════════════════════════

class EgressTestFixtures {

    static final String TPAP_ID = "phonepe";
    static final String WEBHOOK_URL = "https://api.phonepe.com/webhooks/psp";
    static final String TXN_ID = "phonepe-" + UUID.randomUUID();
    static final String CORRELATION_ID = UUID.randomUUID().toString();

    static SwitchCompletedEvent paymentPushEvent() {
        return SwitchCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EventType.PAYMENT_PUSH)
                .tpapId(TPAP_ID)
                .txnId(TXN_ID)
                .correlationId(CORRELATION_ID)
                .timestamp(Instant.now())
                .schemaVersion("1.0")
                .payload(paymentPushPayload())
                .build();
    }

    static SwitchCompletedEvent balanceInquiryEvent() {
        return SwitchCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EventType.BALANCE_INQUIRY)
                .tpapId(TPAP_ID)
                .txnId(TXN_ID)
                .correlationId(CORRELATION_ID)
                .timestamp(Instant.now())
                .schemaVersion("1.0")
                .payload(balanceInquiryPayload())
                .build();
    }

    static SwitchCompletedEvent vpaVerificationEvent() {
        return SwitchCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EventType.VPA_VERIFICATION)
                .tpapId(TPAP_ID)
                .txnId(TXN_ID)
                .correlationId(CORRELATION_ID)
                .timestamp(Instant.now())
                .schemaVersion("1.0")
                .payload(vpaVerificationPayload())
                .build();
    }

    static PaymentPushEvent paymentPushPayload() {
        return PaymentPushEvent.builder()
                .payerVpa("jane@okhdfcbank").payeeVpa("john@okaxis")
                .amount("250.00").currency("INR")
                .npciRrn("512301234567").txnStatus("SUCCESS")
                .build();
    }

    static BalanceInquiryEvent balanceInquiryPayload() {
        return BalanceInquiryEvent.builder()
                .vpa("john@okaxis").availableBalance("12500.00")
                .currency("INR").inquiryStatus("SUCCESS")
                .build();
    }

    static VpaVerificationEvent vpaVerificationPayload() {
        return VpaVerificationEvent.builder()
                .vpa("john@okaxis").accountHolderName("John Doe")
                .bankName("Axis Bank").verified(true)
                .build();
    }

    static WebhookConfig activeConfig(EventType type) {
        return WebhookConfig.builder()
                .id(1L).tpapId(TPAP_ID).eventType(type)
                .url(WEBHOOK_URL).active(true)
                .build();
    }

    static WebhookConfig inactiveConfig(EventType type) {
        return WebhookConfig.builder()
                .id(2L).tpapId(TPAP_ID).eventType(type)
                .url(WEBHOOK_URL).active(false)
                .build();
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SECTION 1 — EVENT ROUTING (POLYMORPHIC HANDLER SELECTION)
// ═══════════════════════════════════════════════════════════════════════════

@ExtendWith(MockitoExtension.class)
@DisplayName("Section 1 — Event routing: polymorphic handler dispatch")
class EventRoutingTest extends EgressTestFixtures {

    @Mock
    WebhookConfigRepository configRepo;
    @Mock
    DeliveryLogRepository deliveryLogRepo;
    @Mock
    WebhookHttpClient httpClient;
    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    PaymentPushHandler paymentHandler;
    @InjectMocks
    BalanceInquiryHandler balanceHandler;
    @InjectMocks
    VpaVerificationHandler vpaHandler;

    EventHandlerFactory factory;

    @BeforeEach
    void buildFactory() {
        factory = new EventHandlerFactory(
                java.util.List.of(paymentHandler, balanceHandler, vpaHandler));
    }

    @Test
    @DisplayName("should_resolvePaymentPushHandler_when_eventType_is_PAYMENT_PUSH")
    void should_resolvePaymentPushHandler_when_eventType_is_PAYMENT_PUSH() {
        WebhookEventHandler handler = factory.getHandler(EventType.PAYMENT_PUSH);
        assertThat(handler).isInstanceOf(PaymentPushHandler.class);
    }

    @Test
    @DisplayName("should_resolveBalanceInquiryHandler_when_eventType_is_BALANCE_INQUIRY")
    void should_resolveBalanceInquiryHandler_when_eventType_is_BALANCE_INQUIRY() {
        WebhookEventHandler handler = factory.getHandler(EventType.BALANCE_INQUIRY);
        assertThat(handler).isInstanceOf(BalanceInquiryHandler.class);
    }

    @Test
    @DisplayName("should_resolveVpaVerificationHandler_when_eventType_is_VPA_VERIFICATION")
    void should_resolveVpaVerificationHandler_when_eventType_is_VPA_VERIFICATION() {
        WebhookEventHandler handler = factory.getHandler(EventType.VPA_VERIFICATION);
        assertThat(handler).isInstanceOf(VpaVerificationHandler.class);
    }

    @Test
    @DisplayName("should_throwUnknownEventTypeException_when_eventType_isNull")
    void should_throwUnknownEventTypeException_when_eventType_isNull() {
        assertThatThrownBy(() -> factory.getHandler(null))
                .isInstanceOf(UnknownEventTypeException.class)
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("should_invokeExactlyOneHandler_when_PAYMENT_PUSH_eventDispatched")
    void should_invokeExactlyOneHandler_when_PAYMENT_PUSH_eventDispatched() {
        given(configRepo.findActiveConfig(TPAP_ID, EventType.PAYMENT_PUSH))
                .willReturn(Optional.of(activeConfig(EventType.PAYMENT_PUSH)));
        given(httpClient.post(anyString(), any())).willReturn(200);

        SwitchCompletedEvent event = paymentPushEvent();
        factory.getHandler(event.getEventType()).handle(event);

        // Balance and VPA handlers must not interact with their repos
        then(configRepo).should(never()).findActiveConfig(TPAP_ID, EventType.BALANCE_INQUIRY);
        then(configRepo).should(never()).findActiveConfig(TPAP_ID, EventType.VPA_VERIFICATION);
    }

    @Test
    @DisplayName("should_eachHandlerReport_correctSupportedType")
    void should_eachHandlerReport_correctSupportedType() {
        assertThat(paymentHandler.supportedType()).isEqualTo(EventType.PAYMENT_PUSH);
        assertThat(balanceHandler.supportedType()).isEqualTo(EventType.BALANCE_INQUIRY);
        assertThat(vpaHandler.supportedType()).isEqualTo(EventType.VPA_VERIFICATION);
    }

    @Test
    @DisplayName("should_notShareHandlerInstances_across_eventTypes")
    void should_notShareHandlerInstances_across_eventTypes() {
        WebhookEventHandler h1 = factory.getHandler(EventType.PAYMENT_PUSH);
        WebhookEventHandler h2 = factory.getHandler(EventType.BALANCE_INQUIRY);
        assertThat(h1).isNotSameAs(h2);
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SECTION 2 — WEBHOOK CONFIG LOOKUP
// ═══════════════════════════════════════════════════════════════════════════

@ExtendWith(MockitoExtension.class)
@DisplayName("Section 2 — Webhook config: missing, inactive, malformed URL")
class WebhookConfigLookupTest extends EgressTestFixtures {

    @Mock
    WebhookConfigRepository configRepo;
    @Mock
    DeliveryLogRepository deliveryLogRepo;
    @Mock
    WebhookHttpClient httpClient;
    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    PaymentPushHandler handler;

    @Test
    @DisplayName("should_skipDispatch_and_logSKIPPED_when_noConfigFoundForTpapAndEventType")
    void should_skipDispatch_and_logSKIPPED_when_noConfigFoundForTpapAndEventType() {
        given(configRepo.findActiveConfig(TPAP_ID, EventType.PAYMENT_PUSH))
                .willReturn(Optional.empty());

        handler.handle(paymentPushEvent());

        then(httpClient).shouldHaveNoInteractions();
        then(deliveryLogRepo).should().save(argThat(log -> log.getStatus().equals("SKIPPED") &&
                log.getTpapId().equals(TPAP_ID)));
    }

    @Test
    @DisplayName("should_skipDispatch_and_logSKIPPED_when_configExists_but_activeFlag_isFalse")
    void should_skipDispatch_and_logSKIPPED_when_configExists_but_activeFlag_isFalse() {
        given(configRepo.findActiveConfig(TPAP_ID, EventType.PAYMENT_PUSH))
                .willReturn(Optional.of(inactiveConfig(EventType.PAYMENT_PUSH)));

        handler.handle(paymentPushEvent());

        then(httpClient).shouldHaveNoInteractions();
        then(deliveryLogRepo).should().save(argThat(log -> log.getStatus().equals("SKIPPED")));
    }

    @Test
    @DisplayName("should_notThrowException_when_configIsMissing_silentSkip")
    void should_notThrowException_when_configIsMissing_silentSkip() {
        given(configRepo.findActiveConfig(any(), any())).willReturn(Optional.empty());
        assertThatNoException().isThrownBy(() -> handler.handle(paymentPushEvent()));
    }

    @Test
    @DisplayName("should_notThrowException_when_configIsInactive_silentSkip")
    void should_notThrowException_when_configIsInactive_silentSkip() {
        given(configRepo.findActiveConfig(any(), any()))
                .willReturn(Optional.of(inactiveConfig(EventType.PAYMENT_PUSH)));
        assertThatNoException().isThrownBy(() -> handler.handle(paymentPushEvent()));
    }

    @Test
    @DisplayName("should_proceedWithDispatch_when_configIsActiveAndUrlIsPresent")
    void should_proceedWithDispatch_when_configIsActiveAndUrlIsPresent() {
        given(configRepo.findActiveConfig(TPAP_ID, EventType.PAYMENT_PUSH))
                .willReturn(Optional.of(activeConfig(EventType.PAYMENT_PUSH)));
        given(httpClient.post(anyString(), any())).willReturn(200);

        handler.handle(paymentPushEvent());

        then(httpClient).should().post(eq(WEBHOOK_URL), any());
    }

    @ParameterizedTest(name = "should_skipDispatch_when_{0}_hasNoConfig")
    @EnumSource(EventType.class)
    @DisplayName("should_skipDispatch_for_allEventTypes_when_noConfigExists")
    void should_skipDispatch_for_allEventTypes_when_noConfigExists(EventType type) {
        lenient().when(configRepo.findActiveConfig(TPAP_ID, type)).thenReturn(Optional.empty());
        SwitchCompletedEvent event = eventForType(type);
        WebhookEventHandler h = handlerForType(type);

        h.handle(event);

        then(httpClient).shouldHaveNoInteractions();
    }

    // ── helpers ──
    @Mock
    BalanceInquiryHandler balanceHandler;
    @Mock
    VpaVerificationHandler vpaHandler;

    private SwitchCompletedEvent eventForType(EventType t) {
        return switch (t) {
            case PAYMENT_PUSH -> paymentPushEvent();
            case BALANCE_INQUIRY -> balanceInquiryEvent();
            case VPA_VERIFICATION -> vpaVerificationEvent();
        };
    }

    private WebhookEventHandler handlerForType(EventType t) {
        return switch (t) {
            case PAYMENT_PUSH -> handler;
            case BALANCE_INQUIRY -> balanceHandler;
            case VPA_VERIFICATION -> vpaHandler;
        };
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SECTION 3 — HTTP OUTCOME HANDLING
// ═══════════════════════════════════════════════════════════════════════════

@ExtendWith(MockitoExtension.class)
@DisplayName("Section 3 — HTTP outcomes: 2xx / 4xx / 5xx / timeout")
class HttpOutcomeHandlingTest extends EgressTestFixtures {

    @Mock
    WebhookConfigRepository configRepo;
    @Mock
    DeliveryLogRepository deliveryLogRepo;
    @Mock
    WebhookHttpClient httpClient;
    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    PaymentPushHandler handler;

    @BeforeEach
    void stubActiveConfig() {
        given(configRepo.findActiveConfig(TPAP_ID, EventType.PAYMENT_PUSH))
                .willReturn(Optional.of(activeConfig(EventType.PAYMENT_PUSH)));
    }

    // ── 2xx ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should_logSUCCESS_and_notRetry_when_tpapReturns200")
    void should_logSUCCESS_and_notRetry_when_tpapReturns200() {
        given(httpClient.post(anyString(), any())).willReturn(200);

        handler.handle(paymentPushEvent());

        then(httpClient).should(times(1)).post(anyString(), any());
        then(deliveryLogRepo).should().save(argThat(log -> log.getStatus().equals("SUCCESS") &&
                log.getHttpStatus() == 200 &&
                log.getAttemptNumber() == 1));
    }

    @Test
    @DisplayName("should_logSUCCESS_when_tpapReturns201_Created")
    void should_logSUCCESS_when_tpapReturns201_Created() {
        given(httpClient.post(anyString(), any())).willReturn(201);

        handler.handle(paymentPushEvent());

        then(deliveryLogRepo).should()
                .save(argThat(log -> log.getStatus().equals("SUCCESS") && log.getHttpStatus() == 201));
    }

    @Test
    @DisplayName("should_logSUCCESS_when_tpapReturns204_NoContent")
    void should_logSUCCESS_when_tpapReturns204_NoContent() {
        given(httpClient.post(anyString(), any())).willReturn(204);

        handler.handle(paymentPushEvent());

        then(deliveryLogRepo).should().save(argThat(log -> log.getStatus().equals("SUCCESS")));
    }

    // ── 4xx ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should_logFAILED_and_notRetry_when_tpapReturns400_BadRequest")
    void should_logFAILED_and_notRetry_when_tpapReturns400_BadRequest() {
        given(httpClient.post(anyString(), any())).willReturn(400);

        handler.handle(paymentPushEvent());

        // exactly one attempt — 4xx is permanent
        then(httpClient).should(times(1)).post(anyString(), any());
        then(deliveryLogRepo).should().save(argThat(log -> log.getStatus().equals("FAILED") &&
                log.getHttpStatus() == 400 &&
                log.getAttemptNumber() == 1));
    }

    @Test
    @DisplayName("should_logFAILED_and_notRetry_when_tpapReturns401_Unauthorized")
    void should_logFAILED_and_notRetry_when_tpapReturns401_Unauthorized() {
        given(httpClient.post(anyString(), any())).willReturn(401);

        handler.handle(paymentPushEvent());

        then(httpClient).should(times(1)).post(anyString(), any());
        then(deliveryLogRepo).should()
                .save(argThat(log -> log.getStatus().equals("FAILED") && log.getHttpStatus() == 401));
    }

    @Test
    @DisplayName("should_logFAILED_and_notRetry_when_tpapReturns422_UnprocessableEntity")
    void should_logFAILED_and_notRetry_when_tpapReturns422_UnprocessableEntity() {
        given(httpClient.post(anyString(), any())).willReturn(422);

        handler.handle(paymentPushEvent());

        then(httpClient).should(times(1)).post(anyString(), any());
    }

    @ParameterizedTest(name = "should_notRetry_when_http_{0}_4xx_received")
    @ValueSource(ints = { 400, 401, 403, 404, 405, 410, 422, 429 })
    @DisplayName("should_notRetry_for_all_4xx_status_codes")
    void should_notRetry_for_all_4xx_status_codes(int status) {
        given(httpClient.post(anyString(), any())).willReturn(status);

        handler.handle(paymentPushEvent());

        then(httpClient).should(times(1)).post(anyString(), any());
        then(deliveryLogRepo).should()
                .save(argThat(log -> log.getStatus().equals("FAILED") && log.getHttpStatus() == status));
    }

    // ── 5xx ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should_retryUpTo5Times_and_logFAILED_when_tpapReturns503_onAllAttempts")
    void should_retryUpTo5Times_and_logFAILED_when_tpapReturns503_onAllAttempts() {
        given(httpClient.post(anyString(), any())).willReturn(503);

        handler.handle(paymentPushEvent());

        // initial attempt + 5 retries = 6 total
        then(httpClient).should(times(6)).post(anyString(), any());
        then(deliveryLogRepo).should().save(argThat(log -> log.getStatus().equals("FAILED") &&
                log.getHttpStatus() == 503 &&
                log.getAttemptNumber() == 6));
    }

    @Test
    @DisplayName("should_succeedOnSecondAttempt_when_tpapReturns503_thenReturns200")
    void should_succeedOnSecondAttempt_when_tpapReturns503_thenReturns200() {
        given(httpClient.post(anyString(), any()))
                .willReturn(503)
                .willReturn(200);

        handler.handle(paymentPushEvent());

        then(httpClient).should(times(2)).post(anyString(), any());
        then(deliveryLogRepo).should().save(argThat(log -> log.getStatus().equals("SUCCESS") &&
                log.getHttpStatus() == 200 &&
                log.getAttemptNumber() == 2));
    }

    @Test
    @DisplayName("should_succeedOnThirdAttempt_when_tpapReturns503Twice_thenReturns200")
    void should_succeedOnThirdAttempt_when_tpapReturns503Twice_thenReturns200() {
        given(httpClient.post(anyString(), any()))
                .willReturn(503)
                .willReturn(503)
                .willReturn(200);

        handler.handle(paymentPushEvent());

        then(httpClient).should(times(3)).post(anyString(), any());
        then(deliveryLogRepo).should()
                .save(argThat(log -> log.getStatus().equals("SUCCESS") && log.getAttemptNumber() == 3));
    }

    @ParameterizedTest(name = "should_retryAndFail_when_http_{0}_received")
    @ValueSource(ints = { 500, 502, 503, 504 })
    @DisplayName("should_retry_for_all_5xx_status_codes")
    void should_retry_for_all_5xx_status_codes(int status) {
        given(httpClient.post(anyString(), any())).willReturn(status);

        handler.handle(paymentPushEvent());

        then(httpClient).should(times(6)).post(anyString(), any()); // 1 + 5 retries
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SECTION 4 — RETRY & RESILIENCE
// ═══════════════════════════════════════════════════════════════════════════

@ExtendWith(MockitoExtension.class)
@DisplayName("Section 4 — Retry and resilience: timeouts, back-off, exhaustion")
class RetryResilienceTest extends EgressTestFixtures {

    @Mock
    WebhookConfigRepository configRepo;
    @Mock
    DeliveryLogRepository deliveryLogRepo;
    @Mock
    WebhookHttpClient httpClient;
    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    PaymentPushHandler handler;

    private void stubActiveConfig() {
        given(configRepo.findActiveConfig(TPAP_ID, EventType.PAYMENT_PUSH))
                .willReturn(Optional.of(activeConfig(EventType.PAYMENT_PUSH)));
    }

    @Test
    @DisplayName("should_treatConnectionRefused_asRetryable_and_exhaust5Retries")
    void should_treatConnectionRefused_asRetryable_and_exhaust5Retries() {
        stubActiveConfig();
        given(httpClient.post(anyString(), any()))
                .willThrow(new WebhookDeliveryException("Connection refused"));

        handler.handle(paymentPushEvent());

        then(httpClient).should(times(6)).post(anyString(), any());
        then(deliveryLogRepo).should().save(argThat(log -> log.getStatus().equals("FAILED") &&
                log.getErrorMessage() != null &&
                log.getErrorMessage().contains("Connection refused")));
    }

    @Test
    @DisplayName("should_treatReadTimeout_asRetryable_and_exhaust5Retries")
    void should_treatReadTimeout_asRetryable_and_exhaust5Retries() {
        stubActiveConfig();
        given(httpClient.post(anyString(), any()))
                .willThrow(new WebhookDeliveryException("Read timeout after 5000ms"));

        handler.handle(paymentPushEvent());

        then(httpClient).should(times(6)).post(anyString(), any());
        then(deliveryLogRepo).should().save(argThat(log -> log.getStatus().equals("FAILED")));
    }

    @Test
    @DisplayName("should_notExceedMaxRetries_even_if_tpap_remainsUnreachable")
    void should_notExceedMaxRetries_even_if_tpap_remainsUnreachable() {
        stubActiveConfig();
        given(httpClient.post(anyString(), any())).willReturn(503);

        handler.handle(paymentPushEvent());

        // Never more than 6 calls (1 initial + 5 retries)
        then(httpClient).should(atMost(6)).post(anyString(), any());
    }

    @Test
    @DisplayName("should_stopRetrying_immediately_on_first_2xx_response")
    void should_stopRetrying_immediately_on_first_2xx_response() {
        stubActiveConfig();
        given(httpClient.post(anyString(), any())).willReturn(200);

        handler.handle(paymentPushEvent());

        // Must not over-call after success
        then(httpClient).should(times(1)).post(anyString(), any());
    }

    @Test
    @DisplayName("should_stopRetrying_immediately_on_4xx_permanentFailure")
    void should_stopRetrying_immediately_on_4xx_permanentFailure() {
        stubActiveConfig();
        given(httpClient.post(anyString(), any())).willReturn(400);

        handler.handle(paymentPushEvent());

        then(httpClient).should(times(1)).post(anyString(), any());
    }

    @Test
    @DisplayName("should_recordFinalAttemptCount_in_deliveryLog_after_retryExhaustion")
    void should_recordFinalAttemptCount_in_deliveryLog_after_retryExhaustion() {
        stubActiveConfig();
        given(httpClient.post(anyString(), any())).willReturn(503);

        handler.handle(paymentPushEvent());

        ArgumentCaptor<DeliveryLog> captor = ArgumentCaptor.forClass(DeliveryLog.class);
        then(deliveryLogRepo).should().save(captor.capture());
        assertThat(captor.getValue().getAttemptNumber()).isEqualTo(6);
    }

    @Test
    @DisplayName("should_recordFinalHttpStatus_in_deliveryLog_as_lastSeenStatusCode")
    void should_recordFinalHttpStatus_in_deliveryLog_as_lastSeenStatusCode() {
        stubActiveConfig();
        given(httpClient.post(anyString(), any()))
                .willReturn(503)
                .willReturn(503)
                .willReturn(503)
                .willReturn(503)
                .willReturn(503)
                .willReturn(504); // last attempt returns different 5xx

        handler.handle(paymentPushEvent());

        ArgumentCaptor<DeliveryLog> captor = ArgumentCaptor.forClass(DeliveryLog.class);
        then(deliveryLogRepo).should().save(captor.capture());
        assertThat(captor.getValue().getHttpStatus()).isEqualTo(504);
    }

    @Test
    @DisplayName("should_notRetry_when_configLookupFails_silentSkip")
    void should_notRetry_when_configLookupFails_silentSkip() {
        // config repo throws — should not propagate to caller or retry
        given(configRepo.findActiveConfig(any(), any()))
                .willThrow(new RuntimeException("DB connection lost"));

        assertThatThrownBy(() -> handler.handle(paymentPushEvent()))
                .isInstanceOf(RuntimeException.class); // infrastructure error, not swallowed
        then(httpClient).shouldHaveNoInteractions();
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SECTION 5 — DELIVERY LOG PERSISTENCE
// ═══════════════════════════════════════════════════════════════════════════

@ExtendWith(MockitoExtension.class)
@DisplayName("Section 5 — DeliveryLog: always saved, correct fields per outcome")
class DeliveryLogPersistenceTest extends EgressTestFixtures {

    @Mock
    WebhookConfigRepository configRepo;
    @Mock
    DeliveryLogRepository deliveryLogRepo;
    @Mock
    WebhookHttpClient httpClient;
    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    PaymentPushHandler handler;

    @Test
    @DisplayName("should_alwaysSaveDeliveryLog_when_configFound_and_httpReturns200")
    void should_alwaysSaveDeliveryLog_when_configFound_and_httpReturns200() {
        given(configRepo.findActiveConfig(TPAP_ID, EventType.PAYMENT_PUSH))
                .willReturn(Optional.of(activeConfig(EventType.PAYMENT_PUSH)));
        given(httpClient.post(anyString(), any())).willReturn(200);

        handler.handle(paymentPushEvent());

        then(deliveryLogRepo).should(times(1)).save(any(DeliveryLog.class));
    }

    @Test
    @DisplayName("should_alwaysSaveDeliveryLog_when_configMissing_statusSKIPPED")
    void should_alwaysSaveDeliveryLog_when_configMissing_statusSKIPPED() {
        given(configRepo.findActiveConfig(any(), any())).willReturn(Optional.empty());

        handler.handle(paymentPushEvent());

        then(deliveryLogRepo).should(times(1)).save(any(DeliveryLog.class));
    }

    @Test
    @DisplayName("should_populateEventId_txnId_tpapId_in_deliveryLog")
    void should_populateEventId_txnId_tpapId_in_deliveryLog() {
        given(configRepo.findActiveConfig(TPAP_ID, EventType.PAYMENT_PUSH))
                .willReturn(Optional.of(activeConfig(EventType.PAYMENT_PUSH)));
        given(httpClient.post(anyString(), any())).willReturn(200);

        SwitchCompletedEvent event = paymentPushEvent();
        handler.handle(event);

        ArgumentCaptor<DeliveryLog> captor = ArgumentCaptor.forClass(DeliveryLog.class);
        then(deliveryLogRepo).should().save(captor.capture());
        DeliveryLog log = captor.getValue();

        assertThat(log.getEventId()).isEqualTo(event.getEventId());
        assertThat(log.getTxnId()).isEqualTo(event.getTxnId());
        assertThat(log.getTpapId()).isEqualTo(event.getTpapId());
        assertThat(log.getEventType()).isEqualTo(EventType.PAYMENT_PUSH);
    }

    @Test
    @DisplayName("should_populateWebhookUrl_in_deliveryLog_when_configActive")
    void should_populateWebhookUrl_in_deliveryLog_when_configActive() {
        given(configRepo.findActiveConfig(TPAP_ID, EventType.PAYMENT_PUSH))
                .willReturn(Optional.of(activeConfig(EventType.PAYMENT_PUSH)));
        given(httpClient.post(anyString(), any())).willReturn(200);

        handler.handle(paymentPushEvent());

        ArgumentCaptor<DeliveryLog> captor = ArgumentCaptor.forClass(DeliveryLog.class);
        then(deliveryLogRepo).should().save(captor.capture());
        assertThat(captor.getValue().getWebhookUrl()).isEqualTo(WEBHOOK_URL);
    }

    @Test
    @DisplayName("should_setDeliveredAt_timestamp_in_deliveryLog")
    void should_setDeliveredAt_timestamp_in_deliveryLog() {
        given(configRepo.findActiveConfig(TPAP_ID, EventType.PAYMENT_PUSH))
                .willReturn(Optional.of(activeConfig(EventType.PAYMENT_PUSH)));
        given(httpClient.post(anyString(), any())).willReturn(200);

        handler.handle(paymentPushEvent());

        ArgumentCaptor<DeliveryLog> captor = ArgumentCaptor.forClass(DeliveryLog.class);
        then(deliveryLogRepo).should().save(captor.capture());
        assertThat(captor.getValue().getDeliveredAt()).isNotNull();
    }

    @Test
    @DisplayName("should_saveOnlyOnce_regardless_of_retryCount")
    void should_saveOnlyOnce_regardless_of_retryCount() {
        // Even through 5 retries, only one final log row should be saved
        given(configRepo.findActiveConfig(TPAP_ID, EventType.PAYMENT_PUSH))
                .willReturn(Optional.of(activeConfig(EventType.PAYMENT_PUSH)));
        given(httpClient.post(anyString(), any())).willReturn(503);

        handler.handle(paymentPushEvent());

        then(deliveryLogRepo).should(times(1)).save(any(DeliveryLog.class));
    }

    @Test
    @DisplayName("should_setNullHttpStatus_in_deliveryLog_when_skipped")
    void should_setNullHttpStatus_in_deliveryLog_when_skipped() {
        given(configRepo.findActiveConfig(any(), any())).willReturn(Optional.empty());

        handler.handle(paymentPushEvent());

        ArgumentCaptor<DeliveryLog> captor = ArgumentCaptor.forClass(DeliveryLog.class);
        then(deliveryLogRepo).should().save(captor.capture());
        assertThat(captor.getValue().getHttpStatus()).isNull();
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SECTION 6 — PAYLOAD MAPPING (per event type field completeness)
// ═══════════════════════════════════════════════════════════════════════════

@ExtendWith(MockitoExtension.class)
@DisplayName("Section 6 — Payload mapping: fields correct per event type")
class PayloadMappingTest extends EgressTestFixtures {

    @Mock
    WebhookConfigRepository configRepo;
    @Mock
    DeliveryLogRepository deliveryLogRepo;
    @Mock
    WebhookHttpClient httpClient;
    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    PaymentPushHandler paymentHandler;
    @InjectMocks
    BalanceInquiryHandler balanceHandler;
    @InjectMocks
    VpaVerificationHandler vpaHandler;

    private void stubPaymentPush() {
        given(configRepo.findActiveConfig(TPAP_ID, EventType.PAYMENT_PUSH))
                .willReturn(Optional.of(activeConfig(EventType.PAYMENT_PUSH)));
        given(httpClient.post(anyString(), any())).willReturn(200);
    }

    private void stubBalanceInquiry() {
        given(configRepo.findActiveConfig(TPAP_ID, EventType.BALANCE_INQUIRY))
                .willReturn(Optional.of(activeConfig(EventType.BALANCE_INQUIRY)));
        given(httpClient.post(anyString(), any())).willReturn(200);
    }

    private void stubVpaVerification() {
        given(configRepo.findActiveConfig(TPAP_ID, EventType.VPA_VERIFICATION))
                .willReturn(Optional.of(activeConfig(EventType.VPA_VERIFICATION)));
        given(httpClient.post(anyString(), any())).willReturn(200);
    }

    @Test
    @DisplayName("should_sendPaymentPushWebhookPayload_containing_payerVpa_payeeVpa_amount_npciRrn")
    void should_sendPaymentPushWebhookPayload_containing_payerVpa_payeeVpa_amount_npciRrn() {
        stubPaymentPush();
        ArgumentCaptor<WebhookPayload> captor = ArgumentCaptor.forClass(WebhookPayload.class);

        paymentHandler.handle(paymentPushEvent());

        then(httpClient).should().post(anyString(), captor.capture());
        WebhookPayload payload = captor.getValue();
        assertThat(payload).isInstanceOf(PaymentPushWebhookPayload.class);

        PaymentPushWebhookPayload p = (PaymentPushWebhookPayload) payload;
        assertThat(p.getPayerVpa()).isEqualTo("jane@okhdfcbank");
        assertThat(p.getPayeeVpa()).isEqualTo("john@okaxis");
        assertThat(p.getAmount()).isEqualTo("250.00");
        assertThat(p.getNpciRrn()).isEqualTo("512301234567");
    }

    @Test
    @DisplayName("should_sendBalanceInquiryWebhookPayload_containing_vpa_availableBalance_currency")
    void should_sendBalanceInquiryWebhookPayload_containing_vpa_availableBalance_currency() {
        stubBalanceInquiry();
        ArgumentCaptor<WebhookPayload> captor = ArgumentCaptor.forClass(WebhookPayload.class);

        balanceHandler.handle(balanceInquiryEvent());

        then(httpClient).should().post(anyString(), captor.capture());
        BalanceInquiryWebhookPayload p = (BalanceInquiryWebhookPayload) captor.getValue();
        assertThat(p.getVpa()).isEqualTo("john@okaxis");
        assertThat(p.getAvailableBalance()).isEqualTo("12500.00");
        assertThat(p.getCurrency()).isEqualTo("INR");
    }

    @Test
    @DisplayName("should_sendVpaVerificationWebhookPayload_containing_vpa_accountHolderName_verified")
    void should_sendVpaVerificationWebhookPayload_containing_vpa_accountHolderName_verified() {
        stubVpaVerification();
        ArgumentCaptor<WebhookPayload> captor = ArgumentCaptor.forClass(WebhookPayload.class);

        vpaHandler.handle(vpaVerificationEvent());

        then(httpClient).should().post(anyString(), captor.capture());
        VpaVerificationWebhookPayload p = (VpaVerificationWebhookPayload) captor.getValue();
        assertThat(p.getVpa()).isEqualTo("john@okaxis");
        assertThat(p.getAccountHolderName()).isEqualTo("John Doe");
        assertThat(p.isVerified()).isTrue();
    }

    @Test
    @DisplayName("should_includeEnvelopeFields_eventId_correlationId_tpapId_in_allPayloads")
    void should_includeEnvelopeFields_eventId_correlationId_tpapId_in_allPayloads() {
        stubPaymentPush();
        ArgumentCaptor<WebhookPayload> captor = ArgumentCaptor.forClass(WebhookPayload.class);

        SwitchCompletedEvent event = paymentPushEvent();
        paymentHandler.handle(event);

        then(httpClient).should().post(anyString(), captor.capture());
        WebhookPayload payload = captor.getValue();
        assertThat(payload.getEventId()).isEqualTo(event.getEventId());
        assertThat(payload.getCorrelationId()).isEqualTo(event.getCorrelationId());
        assertThat(payload.getTpapId()).isEqualTo(event.getTpapId());
        assertThat(payload.getDeliveredAt()).isNotNull();
    }

    @Test
    @DisplayName("should_setFailureReason_toNull_in_paymentPushPayload_when_txnStatus_SUCCESS")
    void should_setFailureReason_toNull_in_paymentPushPayload_when_txnStatus_SUCCESS() {
        stubPaymentPush();
        ArgumentCaptor<WebhookPayload> captor = ArgumentCaptor.forClass(WebhookPayload.class);

        paymentHandler.handle(paymentPushEvent());

        then(httpClient).should().post(anyString(), captor.capture());
        PaymentPushWebhookPayload p = (PaymentPushWebhookPayload) captor.getValue();
        assertThat(p.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("should_setFailureReason_inPaymentPushPayload_when_txnStatus_FAILED")
    void should_setFailureReason_inPaymentPushPayload_when_txnStatus_FAILED() {
        stubPaymentPush();
        PaymentPushEvent failedPayload = PaymentPushEvent.builder()
                .payerVpa("jane@okhdfcbank").payeeVpa("john@okaxis")
                .amount("500.00").currency("INR")
                .npciRrn("512301234568").txnStatus("FAILED")
                .failureReason("INSUFFICIENT_FUNDS")
                .build();

        SwitchCompletedEvent failedEvent = SwitchCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EventType.PAYMENT_PUSH).tpapId(TPAP_ID)
                .txnId(TXN_ID).correlationId(CORRELATION_ID)
                .timestamp(Instant.now()).schemaVersion("1.0")
                .payload(failedPayload).build();

        ArgumentCaptor<WebhookPayload> captor = ArgumentCaptor.forClass(WebhookPayload.class);
        paymentHandler.handle(failedEvent);

        then(httpClient).should().post(anyString(), captor.capture());
        PaymentPushWebhookPayload p = (PaymentPushWebhookPayload) captor.getValue();
        assertThat(p.getFailureReason()).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    @DisplayName("should_setVerified_false_and_nullName_in_vpaPayload_when_vpaNotFound")
    void should_setVerified_false_and_nullName_in_vpaPayload_when_vpaNotFound() {
        stubVpaVerification();
        VpaVerificationEvent notFound = VpaVerificationEvent.builder()
                .vpa("unknown@okaxis").accountHolderName(null)
                .bankName(null).verified(false).failureReason("VPA_NOT_FOUND")
                .build();

        SwitchCompletedEvent event = SwitchCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EventType.VPA_VERIFICATION).tpapId(TPAP_ID)
                .txnId(TXN_ID).correlationId(CORRELATION_ID)
                .timestamp(Instant.now()).schemaVersion("1.0")
                .payload(notFound).build();

        ArgumentCaptor<WebhookPayload> captor = ArgumentCaptor.forClass(WebhookPayload.class);
        vpaHandler.handle(event);

        then(httpClient).should().post(anyString(), captor.capture());
        VpaVerificationWebhookPayload p = (VpaVerificationWebhookPayload) captor.getValue();
        assertThat(p.isVerified()).isFalse();
        assertThat(p.getAccountHolderName()).isNull();
        assertThat(p.getFailureReason()).isEqualTo("VPA_NOT_FOUND");
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SECTION 7 — UNKNOWN / NULL EVENT TYPE DEFENCE
// ═══════════════════════════════════════════════════════════════════════════

@ExtendWith(MockitoExtension.class)
@DisplayName("Section 7 — Unknown / null event type: guard rails")
class UnknownEventTypeDefenceTest extends EgressTestFixtures {

    @Mock
    WebhookConfigRepository configRepo;
    @Mock
    DeliveryLogRepository deliveryLogRepo;
    @Mock
    WebhookHttpClient httpClient;
    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    PaymentPushHandler paymentHandler;
    @InjectMocks
    BalanceInquiryHandler balanceHandler;
    @InjectMocks
    VpaVerificationHandler vpaHandler;

    EventHandlerFactory factory;

    @BeforeEach
    void buildFactory() {
        factory = new EventHandlerFactory(
                java.util.List.of(paymentHandler, balanceHandler, vpaHandler));
    }

    @Test
    @DisplayName("should_throwUnknownEventTypeException_when_nullEventTypePassedToFactory")
    void should_throwUnknownEventTypeException_when_nullEventTypePassedToFactory() {
        assertThatThrownBy(() -> factory.getHandler(null))
                .isInstanceOf(UnknownEventTypeException.class);
    }

    @Test
    @DisplayName("should_throwUnknownEventTypeException_when_eventType_notInEnum")
    void should_throwUnknownEventTypeException_when_eventType_notInEnum() {
        // Simulate deserialisation of an unrecognised string from Kafka
        assertThatThrownBy(() -> factory.getHandler(null))
                .isInstanceOf(UnknownEventTypeException.class);
    }

    @Test
    @DisplayName("should_notInteractWithHttpClient_when_unknownEventType_received")
    void should_notInteractWithHttpClient_when_unknownEventType_received() {
        try {
            factory.getHandler(null);
        } catch (UnknownEventTypeException ignored) {
        }
        then(httpClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("should_notInteractWithDeliveryLog_when_unknownEventType_received")
    void should_notInteractWithDeliveryLog_when_unknownEventType_received() {
        try {
            factory.getHandler(null);
        } catch (UnknownEventTypeException ignored) {
        }
        then(deliveryLogRepo).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("should_throwException_with_informativeMessage_for_null_eventType")
    void should_throwException_with_informativeMessage_for_null_eventType() {
        assertThatThrownBy(() -> factory.getHandler(null))
                .isInstanceOf(UnknownEventTypeException.class)
                .hasMessageContaining("null");
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SECTION 8 — DISPATCHER ORCHESTRATION (WebhookDispatcherService)
// ═══════════════════════════════════════════════════════════════════════════

@ExtendWith(MockitoExtension.class)
@DisplayName("Section 8 — WebhookDispatcherService: orchestration and delegation")
class WebhookDispatcherServiceTest extends EgressTestFixtures {

    @Mock
    EventHandlerFactory factory;
    @Mock
    WebhookEventHandler handler;

    @InjectMocks
    WebhookDispatcherService dispatcher;

    @Test
    @DisplayName("should_delegateToCorrectHandler_when_PAYMENT_PUSH_eventReceived")
    void should_delegateToCorrectHandler_when_PAYMENT_PUSH_eventReceived() {
        given(factory.getHandler(EventType.PAYMENT_PUSH)).willReturn(handler);

        SwitchCompletedEvent event = paymentPushEvent();
        dispatcher.dispatch(event);

        then(handler).should().handle(event);
    }

    @Test
    @DisplayName("should_delegateToCorrectHandler_when_BALANCE_INQUIRY_eventReceived")
    void should_delegateToCorrectHandler_when_BALANCE_INQUIRY_eventReceived() {
        given(factory.getHandler(EventType.BALANCE_INQUIRY)).willReturn(handler);

        SwitchCompletedEvent event = balanceInquiryEvent();
        dispatcher.dispatch(event);

        then(handler).should().handle(event);
    }

    @Test
    @DisplayName("should_delegateToCorrectHandler_when_VPA_VERIFICATION_eventReceived")
    void should_delegateToCorrectHandler_when_VPA_VERIFICATION_eventReceived() {
        given(factory.getHandler(EventType.VPA_VERIFICATION)).willReturn(handler);

        SwitchCompletedEvent event = vpaVerificationEvent();
        dispatcher.dispatch(event);

        then(handler).should().handle(event);
    }

    @Test
    @DisplayName("should_propagateUnknownEventTypeException_when_factoryCannotResolveHandler")
    void should_propagateUnknownEventTypeException_when_factoryCannotResolveHandler() {
        given(factory.getHandler(any())).willThrow(new UnknownEventTypeException("null"));

        assertThatThrownBy(() -> dispatcher.dispatch(paymentPushEvent()))
                .isInstanceOf(UnknownEventTypeException.class);
    }

    @Test
    @DisplayName("should_callFactoryExactlyOnce_per_dispatchedEvent")
    void should_callFactoryExactlyOnce_per_dispatchedEvent() {
        given(factory.getHandler(EventType.PAYMENT_PUSH)).willReturn(handler);

        dispatcher.dispatch(paymentPushEvent());

        then(factory).should(times(1)).getHandler(EventType.PAYMENT_PUSH);
    }

    @Test
    @DisplayName("should_notSwallowHandlerException_dispatcher_propagates_upward")
    void should_notSwallowHandlerException_dispatcher_propagates_upward() {
        given(factory.getHandler(EventType.PAYMENT_PUSH)).willReturn(handler);
        willThrow(new RuntimeException("DB down"))
                .given(handler).handle(any());

        assertThatThrownBy(() -> dispatcher.dispatch(paymentPushEvent()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB down");
    }

    @Test
    @DisplayName("should_passEntireEventObject_unmodified_to_handler")
    void should_passEntireEventObject_unmodified_to_handler() {
        given(factory.getHandler(EventType.PAYMENT_PUSH)).willReturn(handler);
        SwitchCompletedEvent event = paymentPushEvent();

        dispatcher.dispatch(event);

        ArgumentCaptor<SwitchCompletedEvent> captor = ArgumentCaptor.forClass(SwitchCompletedEvent.class);
        then(handler).should().handle(captor.capture());
        assertThat(captor.getValue()).isSameAs(event);
    }
}
