package com.pspswitch.orchestrator.service;

import com.pspswitch.orchestrator.model.PreprocessingContext;
import com.pspswitch.orchestrator.model.UpiPaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Mode Preprocessing Service — Step 3 of the orchestration saga.
 * 
 * Determines preprocessing flags based on UPI transaction mode:
 * - mode=04 (Intent — unsigned): requiresPasscode=true, flowType by mc
 * - mode=05 (Secure Intent — signed): requiresPasscode=false, flowType by mc
 * - mode=16 (Dynamic QR — most common): requiresPasscode=false,
 * flowType=MERCHANT
 * 
 * Mode affects ONLY preprocessing flags. The core saga is identical for all
 * modes.
 */
@Service
public class ModePreprocessingService {

    private static final Logger log = LoggerFactory.getLogger(ModePreprocessingService.class);

    /**
     * Processes the request mode and returns preprocessing flags.
     */
    public PreprocessingContext process(UpiPaymentRequest request) {
        String mode = request.getMode();
        boolean requiresPasscode;
        String flowType;

        switch (mode) {
            case "04":
                // Intent (unsigned) — requires UPI PIN if not signature-verified
                requiresPasscode = !request.isSignatureVerified();
                flowType = determineFlowType(request.getMc());
                break;

            case "05":
                // Secure Intent (signed) — verified upstream, no passcode needed
                requiresPasscode = false;
                flowType = determineFlowType(request.getMc());
                break;

            case "16":
                // Dynamic QR — most common, always merchant flow
                requiresPasscode = false;
                flowType = "MERCHANT";
                break;

            default:
                // Default to mode 16 behaviour for unknown modes
                log.warn("[MODE] Unknown mode={} | Defaulting to mode=16 behaviour", mode);
                requiresPasscode = false;
                flowType = "MERCHANT";
                break;
        }

        return new PreprocessingContext(requiresPasscode, flowType);
    }

    /**
     * Determines flow type from merchant category code.
     * mc="0000" → P2P (person-to-person)
     * mc!="0000" → MERCHANT (person-to-merchant)
     */
    private String determineFlowType(String mc) {
        if ("0000".equals(mc)) {
            return "P2P";
        }
        return "MERCHANT";
    }
}
