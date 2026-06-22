package com.pspswitch.orchestrator.service;

import com.pspswitch.orchestrator.model.TransactionEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Data Crypto Service — encrypts PII fields before PostgreSQL persistence.
 *
 * Protects payment account identifiers (pa, pn, mid) at rest in the database.
 * Uses AES-256 encryption with a static key loaded from application.properties.
 *
 * Architecture context:
 * This represents the "Data Crypto Service" shown in the PSP Switch
 * architecture
 * diagram — any field that contains personally identifiable information (PII)
 * or sensitive payment data is encrypted before writing to PostgreSQL and
 * decrypted transparently when reading back.
 *
 * IMPORTANT PRODUCTION NOTE:
 * This demo uses AES/ECB/PKCS5Padding for simplicity. In a production
 * environment,
 * you MUST use AES/GCM/NoPadding with a random 12-byte IV per encryption
 * operation.
 * The IV would be prepended to the ciphertext. Key management should use
 * AWS KMS, HashiCorp Vault, or similar HSM-backed key management service
 * with automatic key rotation.
 *
 * Design principle: encrypt/decrypt NEVER throw exceptions — they return the
 * original value on error. The saga must never crash due to a crypto issue.
 */
@Service
public class DataCryptoService {

    private static final Logger log = LoggerFactory.getLogger(DataCryptoService.class);

    /**
     * AES/ECB/PKCS5Padding — acceptable for demo.
     * Production: AES/GCM/NoPadding with random IV per record.
     */
    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";

    @Value("${crypto.enabled:true}")
    private boolean cryptoEnabled;

    @Value("${crypto.secret-key:PSPSwitch2024DemoKey1234567890AB}")
    private String secretKey;

    private SecretKeySpec keySpec;

    @PostConstruct
    public void init() {
        if (cryptoEnabled) {
            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            // AES-256 requires exactly 32 bytes
            if (keyBytes.length != 32) {
                log.error("[CRYPTO] Secret key must be exactly 32 characters for AES-256. " +
                        "Got {} characters. Disabling encryption.", keyBytes.length);
                cryptoEnabled = false;
                return;
            }
            keySpec = new SecretKeySpec(keyBytes, "AES");
            log.info("[CRYPTO] AES-256 encryption initialized | enabled={}", cryptoEnabled);
        } else {
            log.info("[CRYPTO] Encryption disabled by configuration");
        }
    }

    /**
     * Encrypts a plaintext string using AES-256.
     * Returns the original value on error (never crashes the saga).
     */
    public String encrypt(String plaintext) {
        if (!cryptoEnabled || plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            String result = Base64.getEncoder().encodeToString(encrypted);
            log.debug("[CRYPTO] Encrypting field | length={} chars", plaintext.length());
            return result;
        } catch (Exception e) {
            log.warn("[CRYPTO] Encryption failed — returning plaintext | error={}", e.getMessage());
            return plaintext;
        }
    }

    /**
     * Decrypts a Base64-encoded AES-256 ciphertext.
     * Returns the original value on error (never crashes on read).
     */
    public String decrypt(String ciphertext) {
        if (!cryptoEnabled || ciphertext == null || ciphertext.isBlank()) {
            return ciphertext;
        }

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            byte[] decrypted = cipher.doFinal(decoded);
            String result = new String(decrypted, StandardCharsets.UTF_8);
            log.debug("[CRYPTO] Decrypting field | length={} chars", ciphertext.length());
            return result;
        } catch (Exception e) {
            // This can happen if the value was stored before encryption was enabled
            // or if the key has changed. Return as-is rather than crashing.
            log.debug("[CRYPTO] Decryption failed — returning as-is | error={}", e.getMessage());
            return ciphertext;
        }
    }

    /**
     * Encrypts PII fields on a TransactionEntity before PostgreSQL persistence.
     * Fields encrypted: pa (payee UPI ID), pn (payee name), mid (merchant ID)
     */
    public void encryptEntity(TransactionEntity entity) {
        if (!cryptoEnabled)
            return;

        entity.setPa(encrypt(entity.getPa()));
        entity.setPn(encrypt(entity.getPn()));
        entity.setMid(encrypt(entity.getMid()));

        log.info("[CRYPTO] tid={} | PII fields encrypted before persistence", entity.getTid());
    }

    /**
     * Decrypts PII fields on a TransactionEntity after reading from PostgreSQL.
     * Reverses encryptEntity — restores pa, pn, mid to plaintext.
     */
    public void decryptEntity(TransactionEntity entity) {
        if (!cryptoEnabled)
            return;

        entity.setPa(decrypt(entity.getPa()));
        entity.setPn(decrypt(entity.getPn()));
        entity.setMid(decrypt(entity.getMid()));

        log.info("[CRYPTO] tid={} | PII fields decrypted after read", entity.getTid());
    }

    public boolean isCryptoEnabled() {
        return cryptoEnabled;
    }
}
