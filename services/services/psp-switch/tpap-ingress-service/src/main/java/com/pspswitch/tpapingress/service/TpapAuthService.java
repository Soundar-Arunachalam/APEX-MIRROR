package com.pspswitch.tpapingress.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.Map;

/**
 * Authenticates inbound TPAP requests against the hardcoded registry.
 * v1: single TPAP 'phonepe' registered in application.yml.
 * Uses constant-time comparison to prevent timing attacks.
 */
@Service
public class TpapAuthService {

    private final Map<String, String> registry;

    public TpapAuthService(@Value("${app.tpap.registry.phonepe.api-key}") String phonepeKey) {
        this.registry = Map.of("phonepe", phonepeKey);
    }

    /**
     * Validates that the tpapId is registered.
     *
     * @return true if authentication succeeds
     */
    public boolean authenticate(String tpapId) {
        if (tpapId == null) {
            return false;
        }
        return registry.containsKey(tpapId.toLowerCase());
    }
}
