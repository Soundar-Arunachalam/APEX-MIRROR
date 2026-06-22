package com.psp.npci.adapter.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Provides MPIN encryption for outbound NPCI payment requests.
 *
 * <h2>Demo vs Production</h2>
 * <p>
 * <b>Demo (current)</b>: Returns {@code Base64("MOCK_MPIN_" + payerVpa)}.
 * This is intentionally simplistic so the demo works without any HSM or key
 * management.
 *
 * <p>
 * <b>Production</b>: AES-256-GCM encryption using NPCI's published RSA/ECDH
 * public key
 * (obtained from NPCI's key exchange endpoint). The encrypted blob is wrapped
 * in
 * the NPCI-specified format and placed inside the {@code <CredData>} XML
 * element.
 * The same {@link #encryptMpin} interface applies — only the implementation
 * changes.
 */
@Slf4j
@Service
public class EncryptionService {

    /**
     * Encrypts (demo: Base64-encodes) the MPIN credential for the given payer VPA.
     *
     * <p>
     * In production this method would:
     * <ol>
     * <li>Generate a random AES-256 session key</li>
     * <li>Encrypt the MPIN block with AES-256-GCM</li>
     * <li>Wrap the session key with NPCI's RSA-2048/ECDH public key</li>
     * <li>Return the combined payload in NPCI's binary-to-text encoding</li>
     * </ol>
     *
     * @param payerVpa the payer's UPI VPA, used to derive a mock MPIN for demo
     * @return the encrypted-MPIN string to embed in {@code <CredData>}
     */
    public String encryptMpin(String payerVpa) {
        String mockMpin = "MOCK_MPIN_" + payerVpa;
        String encrypted = Base64.getEncoder()
                .encodeToString(mockMpin.getBytes(StandardCharsets.UTF_8));
        log.debug("[ENCRYPTION] MPIN encrypted for VPA: {} (demo Base64 mode)", payerVpa);
        return encrypted;
    }
}
