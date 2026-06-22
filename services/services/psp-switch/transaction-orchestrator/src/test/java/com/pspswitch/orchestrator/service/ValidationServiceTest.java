package com.pspswitch.orchestrator.service;

import com.pspswitch.orchestrator.exception.ValidationException;
import com.pspswitch.orchestrator.model.UpiPaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ValidationService — tests each of the 9 validation rules.
 */
class ValidationServiceTest {

    private ValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new ValidationService();
    }

    private UpiPaymentRequest validRequest() {
        UpiPaymentRequest req = new UpiPaymentRequest();
        req.setTr("ORD-001");
        req.setPa("merchant@yesbank");
        req.setPn("Fresh Mart");
        req.setMc("5411");
        req.setAm(new BigDecimal("500.00"));
        req.setCu("INR");
        req.setMode("16");
        req.setMid("MID-001");
        req.setMsid("STORE-01");
        req.setMtid("POS-01");
        return req;
    }

    @Test
    void rule1_amountNull() {
        UpiPaymentRequest req = validRequest();
        req.setAm(null);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validationService.validate(req, "TEST-001"));
        assertTrue(ex.getReason().contains("greater than zero"));
    }

    @Test
    void rule1_amountZero() {
        UpiPaymentRequest req = validRequest();
        req.setAm(BigDecimal.ZERO);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validationService.validate(req, "TEST-002"));
        assertTrue(ex.getReason().contains("greater than zero"));
    }

    @Test
    void rule1_amountNegative() {
        UpiPaymentRequest req = validRequest();
        req.setAm(new BigDecimal("-10.00"));
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validationService.validate(req, "TEST-003"));
        assertTrue(ex.getReason().contains("greater than zero"));
    }

    @Test
    void rule2_tooManyDecimalPlaces() {
        UpiPaymentRequest req = validRequest();
        req.setAm(new BigDecimal("100.123"));
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validationService.validate(req, "TEST-004"));
        assertTrue(ex.getReason().contains("decimal places"));
    }

    @Test
    void rule3_amountBelowMinimum() {
        UpiPaymentRequest req = validRequest();
        req.setAm(new BigDecimal("50.00"));
        req.setMam(new BigDecimal("100.00"));
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validationService.validate(req, "TEST-005"));
        assertTrue(ex.getReason().contains("minimum"));
    }

    @Test
    void rule3_mamNullIsOk() {
        UpiPaymentRequest req = validRequest();
        req.setMam(null);
        assertDoesNotThrow(() -> validationService.validate(req, "TEST-006"));
    }

    @Test
    void rule4_payeeBlank() {
        UpiPaymentRequest req = validRequest();
        req.setPa("");
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validationService.validate(req, "TEST-007"));
        assertTrue(ex.getReason().contains("Payee UPI ID"));
    }

    @Test
    void rule5_payeeNameBlank() {
        UpiPaymentRequest req = validRequest();
        req.setPn("");
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validationService.validate(req, "TEST-008"));
        assertTrue(ex.getReason().contains("Payee name"));
    }

    @Test
    void rule6_mcBlank() {
        UpiPaymentRequest req = validRequest();
        req.setMc("");
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validationService.validate(req, "TEST-009"));
        assertTrue(ex.getReason().contains("Merchant category"));
    }

    @Test
    void rule7_wrongCurrency() {
        UpiPaymentRequest req = validRequest();
        req.setCu("USD");
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validationService.validate(req, "TEST-010"));
        assertTrue(ex.getReason().contains("INR"));
    }

    @Test
    void rule8_trBlank() {
        UpiPaymentRequest req = validRequest();
        req.setTr("");
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validationService.validate(req, "TEST-011"));
        assertTrue(ex.getReason().contains("Transaction reference"));
    }

    @Test
    void rule9_mode16MissingMid() {
        UpiPaymentRequest req = validRequest();
        req.setMid("");
        ValidationException ex = assertThrows(ValidationException.class,
                () -> validationService.validate(req, "TEST-012"));
        assertTrue(ex.getReason().contains("terminal IDs"));
    }

    @Test
    void rule9_mode04DoesNotRequireMid() {
        UpiPaymentRequest req = validRequest();
        req.setMode("04");
        req.setMid("");
        assertDoesNotThrow(() -> validationService.validate(req, "TEST-013"));
    }

    @Test
    void allRulesPass() {
        UpiPaymentRequest req = validRequest();
        assertDoesNotThrow(() -> validationService.validate(req, "TEST-014"));
    }
}
