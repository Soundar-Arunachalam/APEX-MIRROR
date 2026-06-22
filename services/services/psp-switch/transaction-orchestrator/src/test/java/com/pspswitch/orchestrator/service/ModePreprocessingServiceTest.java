package com.pspswitch.orchestrator.service;

import com.pspswitch.orchestrator.model.PreprocessingContext;
import com.pspswitch.orchestrator.model.UpiPaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ModePreprocessingService — tests all 3 modes produce correct
 * PreprocessingContext.
 */
class ModePreprocessingServiceTest {

    private ModePreprocessingService service;

    @BeforeEach
    void setUp() {
        service = new ModePreprocessingService();
    }

    private UpiPaymentRequest request(String mode, String mc, boolean signatureVerified) {
        UpiPaymentRequest req = new UpiPaymentRequest();
        req.setMode(mode);
        req.setMc(mc);
        req.setSignatureVerified(signatureVerified);
        return req;
    }

    @Test
    void mode04_unsigned_requiresPasscode() {
        PreprocessingContext ctx = service.process(request("04", "5411", false));
        assertTrue(ctx.isRequiresPasscode(), "mode=04 unsigned should require passcode");
        assertEquals("MERCHANT", ctx.getFlowType());
    }

    @Test
    void mode04_signed_noPasscode() {
        PreprocessingContext ctx = service.process(request("04", "5411", true));
        assertFalse(ctx.isRequiresPasscode(), "mode=04 signed should NOT require passcode");
        assertEquals("MERCHANT", ctx.getFlowType());
    }

    @Test
    void mode04_p2p() {
        PreprocessingContext ctx = service.process(request("04", "0000", false));
        assertTrue(ctx.isRequiresPasscode());
        assertEquals("P2P", ctx.getFlowType(), "mc=0000 should be P2P");
    }

    @Test
    void mode05_noPasscode() {
        PreprocessingContext ctx = service.process(request("05", "5411", true));
        assertFalse(ctx.isRequiresPasscode(), "mode=05 should never require passcode");
        assertEquals("MERCHANT", ctx.getFlowType());
    }

    @Test
    void mode05_p2p() {
        PreprocessingContext ctx = service.process(request("05", "0000", true));
        assertFalse(ctx.isRequiresPasscode());
        assertEquals("P2P", ctx.getFlowType());
    }

    @Test
    void mode16_alwaysMerchant() {
        PreprocessingContext ctx = service.process(request("16", "5411", true));
        assertFalse(ctx.isRequiresPasscode(), "mode=16 should never require passcode");
        assertEquals("MERCHANT", ctx.getFlowType(), "mode=16 is always MERCHANT");
    }

    @Test
    void mode16_ignoresMc0000() {
        // mode=16 is always MERCHANT regardless of mc
        PreprocessingContext ctx = service.process(request("16", "0000", true));
        assertFalse(ctx.isRequiresPasscode());
        assertEquals("MERCHANT", ctx.getFlowType(), "mode=16 should be MERCHANT even with mc=0000");
    }

    @Test
    void unknownMode_defaultsToMode16() {
        PreprocessingContext ctx = service.process(request("99", "5411", false));
        assertFalse(ctx.isRequiresPasscode());
        assertEquals("MERCHANT", ctx.getFlowType());
    }
}
