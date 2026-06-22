package com.pspswitch.tpapingress;

// ═══════════════════════════════════════════════════════════════════════════
//  TPAP INGRESS SERVICE — COMPREHENSIVE TEST SUITE
//  Stack : Java 21, Spring Boot 3.x, JUnit 5, Mockito, AssertJ
//  Scope : Input validation · Idempotency · Downstream error handling
//  Out of scope: Signature validation · KMS gRPC calls (v1)
//
//  Adapted from spec document 03_TpapIngressServiceTest.java
//  URL prefix updated: /tpap/v1 → /tpap/api/v1
// ═══════════════════════════════════════════════════════════════════════════

import com.pspswitch.tpapingress.controller.BalanceInquiryController;
import com.pspswitch.tpapingress.controller.PaymentController;
import com.pspswitch.tpapingress.controller.VpaLookupController;
import com.pspswitch.tpapingress.dto.response.AcceptedResponse;
import com.pspswitch.tpapingress.exception.*;
import com.pspswitch.tpapingress.service.IdempotencyService;
import com.pspswitch.tpapingress.service.KafkaPublisherService;
import com.pspswitch.tpapingress.service.TpapAuthService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


// ═══════════════════════════════════════════════════════════════════════════
//  SECTION 1 — VPA LOOKUP INPUT VALIDATION
// ═══════════════════════════════════════════════════════════════════════════
@WebMvcTest(VpaLookupController.class)
@DisplayName("VPA Lookup — Input Validation")
class VpaLookupInputValidationTest {

    @Autowired MockMvc mockMvc;
    @MockBean TpapAuthService authService;
    @MockBean KafkaPublisherService kafkaPublisher;
    @MockBean IdempotencyService idempotencyService;

    private static final String VALID_TXN_ID = "phonepe-" + UUID.randomUUID();
    private static final String URL = "/tpap/api/v1/vpa/lookup";

    @BeforeEach
    void stubAuthAndIdempotency() {
        given(authService.authenticate(eq("phonepe"))).willReturn(true);
        given(idempotencyService.isDuplicate(anyString())).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("should_return202_when_validVpaLookupRequestWithAllRequiredFields")
    void should_return202_when_validVpaLookupRequestWithAllRequiredFields() throws Exception {
        given(kafkaPublisher.publishVpaLookup(any())).willReturn(true);

        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "phoneNumber": "9876543210",
                      "requesterVpa": "jane.smith@okhdfcbank"
                    }
                """.formatted(VALID_TXN_ID)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.txnId").value(VALID_TXN_ID))
            .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    @DisplayName("should_return202_when_validVpaLookupWithOptionalMccProvided")
    void should_return202_when_validVpaLookupWithOptionalMccProvided() throws Exception {
        given(kafkaPublisher.publishVpaLookup(any())).willReturn(true);

        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "phoneNumber": "9876543210",
                      "requesterVpa": "user@okhdfcbank",
                      "mcc": "5411"
                    }
                """.formatted(VALID_TXN_ID)))
            .andExpect(status().isAccepted());
    }

    @ParameterizedTest(name = "should_return400_when_vpaFormat_is_{0}")
    @CsvSource({
        "noAtSign,            'johndoeokaxis'",
        "emptyLocalPart,      '@okaxis'",
        "emptyHandle,         'john.doe@'",
        "doubleAt,            'john@doe@okaxis'",
        "specialCharsInLocal, 'john#doe@okaxis'",
        "exceeds255chars,     'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa@okaxis'",
        "whitespaceOnly,      '   @okaxis'",
    })
    @DisplayName("should_return400_when_phoneNumber_hasInvalidFormat")
    void should_return400_when_phoneNumber_hasInvalidFormat(String scenario, String invalidVpa) throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "phoneNumber": %s,
                      "requesterVpa": "jane@okhdfcbank"
                    }
                """.formatted(VALID_TXN_ID, "\"" + invalidVpa + "\"")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_MOBILE_NUMBER"));
    }

    @Test
    @DisplayName("should_return400_when_phoneNumber_isNull")
    void should_return400_when_phoneNumber_isNull() throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "txnId": "%s", "requesterVpa": "jane@okhdfcbank" }
                """.formatted(VALID_TXN_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("MISSING_REQUIRED_FIELD"));
    }

    @Test
    @DisplayName("should_return400_when_requesterVpa_isNull")
    void should_return400_when_requesterVpa_isNull() throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "txnId": "%s", "phoneNumber": "9876543210" }
                """.formatted(VALID_TXN_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("MISSING_REQUIRED_FIELD"));
    }

    @Test
    @DisplayName("should_return400_when_mcc_isNonNumeric")
    void should_return400_when_mcc_isNonNumeric() throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "phoneNumber": "9876543210",
                      "requesterVpa": "user@okhdfcbank",
                      "mcc": "ABCD"
                    }
                """.formatted(VALID_TXN_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_MCC"));
    }

    @Test
    @DisplayName("should_return400_when_mcc_isThreeDigits")
    void should_return400_when_mcc_isThreeDigits() throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "phoneNumber": "9876543210",
                      "requesterVpa": "user@okhdfcbank",
                      "mcc": "541"
                    }
                """.formatted(VALID_TXN_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_MCC"));
    }
}


// ═══════════════════════════════════════════════════════════════════════════
//  SECTION 2 — TXN ID FORMAT VALIDATION (shared across all endpoints)
// ═══════════════════════════════════════════════════════════════════════════
@WebMvcTest(VpaLookupController.class)
@DisplayName("TxnId Format Validation — All Endpoints")
class TxnIdFormatValidationTest {

    @Autowired MockMvc mockMvc;
    @MockBean TpapAuthService authService;
    @MockBean KafkaPublisherService kafkaPublisher;
    @MockBean IdempotencyService idempotencyService;

    private static final String URL = "/tpap/api/v1/vpa/lookup";

    @BeforeEach
    void stubAuth() {
        given(authService.authenticate(eq("phonepe"))).willReturn(true);
        given(idempotencyService.isDuplicate(anyString())).willReturn(Optional.empty());
    }

    @ParameterizedTest(name = "should_return400_when_txnId_is_{0}")
    @CsvSource({
        "missingTpapPrefix,       '550e8400-e29b-41d4-a716-446655440000'",
        "wrongTpapPrefix,         'gpay-550e8400-e29b-41d4-a716-446655440000'",
        "noUuidSuffix,            'phonepe-notauuid'",
        "emptyString,             ''",
        "tpapNameOnlyNoUuid,      'phonepe-'",
        "invalidUuidVersion,      'phonepe-ZZZZZZZZ-ZZZZ-ZZZZ-ZZZZ-ZZZZZZZZZZZZ'",
    })
    @DisplayName("should_return400_when_txnId_doesNotMatchTpapNameUuidFormat")
    void should_return400_when_txnId_doesNotMatchTpapNameUuidFormat(String scenario, String invalidTxnId) throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "phoneNumber": "9876543210",
                      "requesterVpa": "jane@okhdfcbank"
                    }
                """.formatted(invalidTxnId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_TXN_ID_FORMAT"));
    }

    @Test
    @DisplayName("should_return400_when_txnId_isAbsent")
    void should_return400_when_txnId_isAbsent() throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "phoneNumber": "9876543210",
                      "requesterVpa": "jane@okhdfcbank" }
                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("MISSING_REQUIRED_FIELD"));
    }
}


// ═══════════════════════════════════════════════════════════════════════════
//  SECTION 3 — BALANCE INQUIRY INPUT VALIDATION
// ═══════════════════════════════════════════════════════════════════════════
@WebMvcTest(BalanceInquiryController.class)
@DisplayName("Balance Inquiry — Input Validation")
class BalanceInquiryInputValidationTest {

    @Autowired MockMvc mockMvc;
    @MockBean TpapAuthService authService;
    @MockBean KafkaPublisherService kafkaPublisher;
    @MockBean IdempotencyService idempotencyService;

    private static final String URL = "/tpap/api/v1/balance/inquiry";
    private static final String VALID_TXN_ID = "phonepe-" + UUID.randomUUID();

    @BeforeEach
    void stubAuthAndIdempotency() {
        given(authService.authenticate(eq("phonepe"))).willReturn(true);
        given(idempotencyService.isDuplicate(anyString())).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("should_return202_when_validBalanceInquiryWithAllRequiredFields")
    void should_return202_when_validBalanceInquiryWithAllRequiredFields() throws Exception {
        given(kafkaPublisher.publishBalanceInquiry(any())).willReturn(true);

        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "vpa": "john.doe@okaxis",
                      "encryptedPin": "AnyBase64PinBlock==",
                      "deviceFingerprint": "fp123abc"
                    }
                """.formatted(VALID_TXN_ID)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("should_return202_when_encryptedPin_isAnyNonEmptyString_v1HardcodedPassthrough")
    void should_return202_when_encryptedPin_isAnyNonEmptyString_v1HardcodedPassthrough() throws Exception {
        given(kafkaPublisher.publishBalanceInquiry(any())).willReturn(true);

        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "vpa": "john.doe@okaxis",
                      "encryptedPin": "dummy-pin-v1",
                      "deviceFingerprint": "fp123abc"
                    }
                """.formatted(VALID_TXN_ID)))
            .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("should_return400_when_encryptedPin_isBlankOrEmpty")
    void should_return400_when_encryptedPin_isBlankOrEmpty() throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "vpa": "john.doe@okaxis",
                      "encryptedPin": "",
                      "deviceFingerprint": "fp123abc"
                    }
                """.formatted(VALID_TXN_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("MISSING_REQUIRED_FIELD"));
    }

    @Test
    @DisplayName("should_return400_when_deviceFingerprint_isAbsent")
    void should_return400_when_deviceFingerprint_isAbsent() throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "vpa": "john.doe@okaxis",
                      "encryptedPin": "AnyBase64PinBlock=="
                    }
                """.formatted(VALID_TXN_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("MISSING_REQUIRED_FIELD"));
    }

    @ParameterizedTest(name = "should_return400_when_mobileNumber_is_{0}")
    @CsvSource({
        "eightDigits,      '98765432'",
        "startsWithZero,   '0987654321'",
        "startsWithOne,    '1987654321'",
        "containsLetters,  '9876ABCDEF'",
        "elevenDigits,     '98765432101'",
    })
    @DisplayName("should_return400_when_optionalMobileNumber_hasInvalidFormat")
    void should_return400_when_optionalMobileNumber_hasInvalidFormat(String scenario, String phone) throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "vpa": "john.doe@okaxis",
                      "encryptedPin": "AnyBase64PinBlock==",
                      "deviceFingerprint": "fp123abc",
                      "mobileNumber": "%s"
                    }
                """.formatted(VALID_TXN_ID, phone)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_MOBILE_NUMBER"));
    }
}


// ═══════════════════════════════════════════════════════════════════════════
//  SECTION 4 — PAYMENT INITIATION INPUT VALIDATION
// ═══════════════════════════════════════════════════════════════════════════
@WebMvcTest(PaymentController.class)
@DisplayName("Payment Initiation — Input Validation")
class PaymentInitiationInputValidationTest {

    @Autowired MockMvc mockMvc;
    @MockBean TpapAuthService authService;
    @MockBean KafkaPublisherService kafkaPublisher;
    @MockBean IdempotencyService idempotencyService;

    private static final String URL = "/tpap/api/v1/payment/initiate";
    private static final String VALID_TXN_ID = "phonepe-" + UUID.randomUUID();

    @BeforeEach
    void stubAuthAndIdempotency() {
        given(authService.authenticate(eq("phonepe"))).willReturn(true);
        given(idempotencyService.isDuplicate(anyString())).willReturn(Optional.empty());
        given(kafkaPublisher.publishPaymentInitiate(any())).willReturn(true);
    }

    @Test
    @DisplayName("should_return202_when_validP2pPaymentRequest")
    void should_return202_when_validP2pPaymentRequest() throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "payerVpa": "jane@okhdfcbank",
                      "payeeVpa": "john@okaxis",
                      "amount": "250.00",
                      "currency": "INR",
                      "encryptedPin": "AnyPin==",
                      "deviceFingerprint": "fp123",
                      "txnType": "PEER_TO_PEER"
                    }
                """.formatted(VALID_TXN_ID)))
            .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("should_return202_when_validMerchantPaymentWithMcc")
    void should_return202_when_validMerchantPaymentWithMcc() throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "payerVpa": "jane@okhdfcbank",
                      "payeeVpa": "shop@okicici",
                      "amount": "1299.00",
                      "currency": "INR",
                      "encryptedPin": "AnyPin==",
                      "deviceFingerprint": "fp123",
                      "txnType": "MERCHANT_PAYMENT",
                      "mcc": "5411"
                    }
                """.formatted(VALID_TXN_ID)))
            .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("should_return400_when_payerVpa_equalsPayeeVpa")
    void should_return400_when_payerVpa_equalsPayeeVpa() throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "payerVpa": "same@okaxis",
                      "payeeVpa": "same@okaxis",
                      "amount": "100.00",
                      "currency": "INR",
                      "encryptedPin": "AnyPin==",
                      "deviceFingerprint": "fp123",
                      "txnType": "PEER_TO_PEER"
                    }
                """.formatted(VALID_TXN_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("PAYER_PAYEE_SAME"));
    }

    @ParameterizedTest(name = "should_return400_when_amount_is_{0}")
    @CsvSource({
        "zero,             '0.00'",
        "negative,         '-100.00'",
        "exceedsMax,       '100001.00'",
        "threeDecimals,    '10.001'",
        "empty,            ''",
        "alphabetic,       'abc'",
        "justDot,          '.'",
        "zeroDecimalOnly,  '.00'",
    })
    @DisplayName("should_return400_when_amount_hasInvalidValue")
    void should_return400_when_amount_hasInvalidValue(String scenario, String invalidAmount) throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "payerVpa": "jane@okhdfcbank",
                      "payeeVpa": "john@okaxis",
                      "amount": "%s",
                      "currency": "INR",
                      "encryptedPin": "AnyPin==",
                      "deviceFingerprint": "fp123",
                      "txnType": "PEER_TO_PEER"
                    }
                """.formatted(VALID_TXN_ID, invalidAmount)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_AMOUNT"));
    }

    @Test
    @DisplayName("should_return400_when_currency_isNotINR")
    void should_return400_when_currency_isNotINR() throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "payerVpa": "jane@okhdfcbank",
                      "payeeVpa": "john@okaxis",
                      "amount": "100.00",
                      "currency": "USD",
                      "encryptedPin": "AnyPin==",
                      "deviceFingerprint": "fp123",
                      "txnType": "PEER_TO_PEER"
                    }
                """.formatted(VALID_TXN_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_CURRENCY"));
    }

    @Test
    @DisplayName("should_return400_when_txnType_isMerchantPaymentAndMcc_isAbsent")
    void should_return400_when_txnType_isMerchantPaymentAndMcc_isAbsent() throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "payerVpa": "jane@okhdfcbank",
                      "payeeVpa": "shop@okicici",
                      "amount": "500.00",
                      "currency": "INR",
                      "encryptedPin": "AnyPin==",
                      "deviceFingerprint": "fp123",
                      "txnType": "MERCHANT_PAYMENT"
                    }
                """.formatted(VALID_TXN_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_MCC"));
    }

    @Test
    @DisplayName("should_return400_when_remarks_exceedsFiftyChars")
    void should_return400_when_remarks_exceedsFiftyChars() throws Exception {
        String longRemarks = "A".repeat(51);
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "payerVpa": "jane@okhdfcbank",
                      "payeeVpa": "john@okaxis",
                      "amount": "100.00",
                      "currency": "INR",
                      "encryptedPin": "AnyPin==",
                      "deviceFingerprint": "fp123",
                      "txnType": "PEER_TO_PEER",
                      "remarks": "%s"
                    }
                """.formatted(VALID_TXN_ID, longRemarks)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_REMARKS"));
    }

    @Test
    @DisplayName("should_return400_when_remarks_containsSpecialCharacters")
    void should_return400_when_remarks_containsSpecialCharacters() throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "payerVpa": "jane@okhdfcbank",
                      "payeeVpa": "john@okaxis",
                      "amount": "100.00",
                      "currency": "INR",
                      "encryptedPin": "AnyPin==",
                      "deviceFingerprint": "fp123",
                      "txnType": "PEER_TO_PEER",
                      "remarks": "Pay<script>alert(1)</script>"
                    }
                """.formatted(VALID_TXN_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_REMARKS"));
    }

    @Test
    @DisplayName("should_return400_when_expiryTimestamp_isInThePast")
    void should_return400_when_expiryTimestamp_isInThePast() throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "%s",
                      "payerVpa": "jane@okhdfcbank",
                      "payeeVpa": "john@okaxis",
                      "amount": "100.00",
                      "currency": "INR",
                      "encryptedPin": "AnyPin==",
                      "deviceFingerprint": "fp123",
                      "txnType": "PEER_TO_PEER",
                      "expiry": "2020-01-01T00:00:00.000Z"
                    }
                """.formatted(VALID_TXN_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_EXPIRY"));
    }

    @Test
    @DisplayName("should_return400_when_requestBodyIsCompletelyEmpty")
    void should_return400_when_requestBodyIsCompletelyEmpty() throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }
}


// ═══════════════════════════════════════════════════════════════════════════
//  SECTION 5 — AUTHENTICATION VALIDATION
// ═══════════════════════════════════════════════════════════════════════════
@WebMvcTest(VpaLookupController.class)
@DisplayName("Authentication — Header Validation")
class AuthenticationHeaderValidationTest {

    @Autowired MockMvc mockMvc;
    @MockBean TpapAuthService authService;
    @MockBean KafkaPublisherService kafkaPublisher;
    @MockBean IdempotencyService idempotencyService;

    private static final String URL = "/tpap/api/v1/vpa/lookup";
    private static final String VALID_TXN_ID = "phonepe-" + UUID.randomUUID();
    private static final String VALID_BODY = """
            { "txnId": "%s", "vpa": "john.doe@okaxis",
                      "requesterVpa": "jane@okhdfcbank" }
        """.formatted(VALID_TXN_ID);


    @Test
    @DisplayName("should_return401_when_xTpapIdHeader_isAbsent")
    void should_return401_when_xTpapIdHeader_isAbsent() throws Exception {
        mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("MISSING_TPAP_ID"));
    }


    @Test
    @DisplayName("should_return401_when_tpapId_isNotRegistered")
    void should_return401_when_tpapId_isNotRegistered() throws Exception {
        given(authService.authenticate(eq("unknownapp"))).willReturn(false);

        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "unknownapp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("INVALID_TPAP_ID"));
    }

}


// ═══════════════════════════════════════════════════════════════════════════
//  SECTION 6 — IDEMPOTENCY
// ═══════════════════════════════════════════════════════════════════════════
@WebMvcTest(VpaLookupController.class)
@DisplayName("Idempotency — Duplicate Detection")
class IdempotencyTest {

    @Autowired MockMvc mockMvc;
    @MockBean TpapAuthService authService;
    @MockBean KafkaPublisherService kafkaPublisher;
    @MockBean IdempotencyService idempotencyService;

    private static final String URL = "/tpap/api/v1/vpa/lookup";
    private static final String TXN_ID = "phonepe-" + UUID.randomUUID();
    private static final String CORRELATION_ID = UUID.randomUUID().toString();
    private static final String BODY = """
            { "txnId": "%s", "phoneNumber": "9876543210",
                      "requesterVpa": "jane@okhdfcbank" }
        """.formatted(TXN_ID);

    @BeforeEach
    void stubAuth() {
        given(authService.authenticate(eq("phonepe"))).willReturn(true);
    }

    @Test
    @DisplayName("should_return202WithCachedResponse_when_duplicateTxnId_detectedInRedis")
    void should_return202WithCachedResponse_when_duplicateTxnId_detectedInRedis() throws Exception {
        AcceptedResponse cached = AcceptedResponse.builder()
            .txnId(TXN_ID).correlationId(CORRELATION_ID)
            .status("ACCEPTED").idempotentReplay(true).build();
        given(idempotencyService.isDuplicate(anyString())).willReturn(Optional.of(cached));

        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.idempotentReplay").value(true))
            .andExpect(jsonPath("$.txnId").value(TXN_ID))
            .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID));

        // Kafka must NOT be called again for a duplicate
        then(kafkaPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("should_return202WithCachedResponse_when_duplicateTxnId_detectedInPostgres_afterRedisMiss")
    void should_return202WithCachedResponse_when_duplicateTxnId_detectedInPostgres_afterRedisMiss() throws Exception {
        AcceptedResponse cached = AcceptedResponse.builder()
            .txnId(TXN_ID).correlationId(CORRELATION_ID)
            .status("ACCEPTED").idempotentReplay(true).build();
        // Redis misses (empty), Postgres hits
        given(idempotencyService.isDuplicate(anyString())).willReturn(Optional.of(cached));

        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.idempotentReplay").value(true));

        then(kafkaPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("should_publishToKafkaAndPersistIdempotencyKey_when_txnId_isNew")
    void should_publishToKafkaAndPersistIdempotencyKey_when_txnId_isNew() throws Exception {
        given(idempotencyService.isDuplicate(anyString())).willReturn(Optional.empty());
        given(kafkaPublisher.publishVpaLookup(any())).willReturn(true);

        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("ACCEPTED"));

        then(idempotencyService).should().persist(anyString(), any());
        then(kafkaPublisher).should().publishVpaLookup(any());
    }

    @Test
    @DisplayName("should_generateDifferentCorrelationId_for_eachNewRequest_evenWithSameTxnId_content")
    void should_generateDifferentCorrelationId_for_eachNewRequest_evenWithSameTxnId_content() throws Exception {
        // Two different txnIds should each get fresh correlationIds
        given(idempotencyService.isDuplicate(anyString())).willReturn(Optional.empty());
        given(kafkaPublisher.publishVpaLookup(any())).willReturn(true);

        MvcResult r1 = mockMvc.perform(post(URL)
                .header("X-TPAP-API-Key", "valid-key").header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "txnId": "phonepe-%s", "phoneNumber": "9876543210",
                      "requesterVpa": "b@okhdfc" }
                """.formatted(UUID.randomUUID())))
            .andExpect(status().isAccepted()).andReturn();

        MvcResult r2 = mockMvc.perform(post(URL)
                .header("X-TPAP-API-Key", "valid-key").header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "txnId": "phonepe-%s", "phoneNumber": "9876543210",
                      "requesterVpa": "b@okhdfc" }
                """.formatted(UUID.randomUUID())))
            .andExpect(status().isAccepted()).andReturn();

        String cid1 = com.jayway.jsonpath.JsonPath.read(r1.getResponse().getContentAsString(), "$.correlationId");
        String cid2 = com.jayway.jsonpath.JsonPath.read(r2.getResponse().getContentAsString(), "$.correlationId");
        assertThat(cid1).isNotEqualTo(cid2);
    }
}


// ═══════════════════════════════════════════════════════════════════════════
//  SECTION 7 — DOWNSTREAM / KAFKA ERROR HANDLING
// ═══════════════════════════════════════════════════════════════════════════
@WebMvcTest(VpaLookupController.class)
@DisplayName("Downstream Error Handling — Kafka & Stores")
class DownstreamErrorHandlingTest {

    @Autowired MockMvc mockMvc;
    @MockBean TpapAuthService authService;
    @MockBean KafkaPublisherService kafkaPublisher;
    @MockBean IdempotencyService idempotencyService;

    private static final String URL = "/tpap/api/v1/vpa/lookup";
    private static final String BODY = """
        { "txnId": "phonepe-%s", "phoneNumber": "9876543210",
                      "requesterVpa": "jane@okhdfc" }
    """.formatted(UUID.randomUUID());

    @BeforeEach
    void stubAuth() {
        given(authService.authenticate(eq("phonepe"))).willReturn(true);
        given(idempotencyService.isDuplicate(anyString())).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("should_return503_when_kafkaBroker_isCompletelyUnavailable")
    void should_return503_when_kafkaBroker_isCompletelyUnavailable() throws Exception {
        given(kafkaPublisher.publishVpaLookup(any()))
            .willThrow(new KafkaUnavailableException("Kafka broker not reachable"));

        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.errorCode").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("should_return500_when_kafkaPublish_failsAfterAllRetries")
    void should_return500_when_kafkaPublish_failsAfterAllRetries() throws Exception {
        given(kafkaPublisher.publishVpaLookup(any()))
            .willThrow(new KafkaPublishFailureException("All 3 retries exhausted"));

        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.errorCode").value("KAFKA_PUBLISH_FAILURE"));
    }

    @Test
    @DisplayName("should_return500_when_redisAndPostgres_areBothUnreachable_duringIdempotencyCheck")
    void should_return500_when_redisAndPostgres_areBothUnreachable_duringIdempotencyCheck() throws Exception {
        given(idempotencyService.isDuplicate(anyString()))
            .willThrow(new IdempotencyStoreException("Both Redis and Postgres unreachable"));

        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_STORE_ERROR"));
    }

    @Test
    @DisplayName("should_return500_when_idempotencyPersist_failsAfterKafkaPublishSucceeds")
    void should_return500_when_idempotencyPersist_failsAfterKafkaPublishSucceeds() throws Exception {
        given(kafkaPublisher.publishVpaLookup(any())).willReturn(true);
        willThrow(new IdempotencyStoreException("Cannot write to idempotency store"))
            .given(idempotencyService).persist(anyString(), any());

        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_STORE_ERROR"));
    }

    @Test
    @DisplayName("should_return429_when_rateLimitExceeded_forRegisteredTpap")
    void should_return429_when_rateLimitExceeded_forRegisteredTpap() throws Exception {
        given(authService.authenticate(eq("phonepe")))
            .willThrow(new RateLimitExceededException("Rate limit exceeded: 300 req/min"));

        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
            .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("should_notLeakInternalExceptionMessages_in_errorResponse")
    void should_notLeakInternalExceptionMessages_in_errorResponse() throws Exception {
        given(kafkaPublisher.publishVpaLookup(any()))
            .willThrow(new RuntimeException("Internal DB credentials exposed!"));

        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("DB credentials"))));
    }
}


// ═══════════════════════════════════════════════════════════════════════════
//  SECTION 8 — CONTENT-TYPE & REQUEST FORMAT EDGE CASES
// ═══════════════════════════════════════════════════════════════════════════
@WebMvcTest(VpaLookupController.class)
@DisplayName("Request Format Edge Cases")
class RequestFormatEdgeCasesTest {

    @Autowired MockMvc mockMvc;
    @MockBean TpapAuthService authService;
    @MockBean KafkaPublisherService kafkaPublisher;
    @MockBean IdempotencyService idempotencyService;

    private static final String URL = "/tpap/api/v1/vpa/lookup";

    @BeforeEach
    void stubAuth() {
        given(authService.authenticate(anyString())).willReturn(true);
    }

    @Test
    @DisplayName("should_return415_when_contentType_isNotApplicationJson")
    void should_return415_when_contentType_isNotApplicationJson() throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.TEXT_PLAIN)
                .content("some plain text"))
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @DisplayName("should_return400_when_requestBody_isMalformedJson")
    void should_return400_when_requestBody_isMalformedJson() throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ this is not valid json }"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_return400_when_requestBody_isAbsent")
    void should_return400_when_requestBody_isAbsent() throws Exception {
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("should_return400_when_requestBody_hasUnknownExtraFields_strictMode")
    void should_return400_when_requestBody_hasUnknownExtraFields_strictMode() throws Exception {
        // Service is configured with FAIL_ON_UNKNOWN_PROPERTIES=true
        mockMvc.perform(post(URL)
                .header("X-TPAP-ID", "phonepe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "txnId": "phonepe-%s",
                      "phoneNumber": "9876543210",
                      "requesterVpa": "jane@okhdfc",
                      "injectField": "<script>alert(1)</script>"
                    }
                """.formatted(UUID.randomUUID())))
            .andExpect(status().isBadRequest());
    }
}
