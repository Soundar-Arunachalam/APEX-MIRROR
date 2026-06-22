package com.pspswitch.ledger.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts and decrypts PII data (VPAs, names, merchant IDs) before it is
 * written to the PostgreSQL ledger_db.
 *
 * <p>Uses AES-256-GCM.
 */
@Slf4j
@Service
public class DataCryptoService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    @Value("${ledger.crypto.enabled:true}")
    private boolean cryptoEnabled;

    @Value("${ledger.crypto.secret-key}")
    private String secretKey;

    private SecretKeySpec keySpec;

    @PostConstruct
    public void init() {
        if (!cryptoEnabled) {
            log.warn("[CRYPTO-SERVICE] PII Encryption is DISABLED. Data will be stored in plaintext.");
            return;
        }

        if (secretKey == null || secretKey.length() != 32) {
            throw new IllegalStateException("crypto.secret-key must be exactly 32 bytes for AES-256");
        }

        this.keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
        log.info("[CRYPTO-SERVICE] PII Encryption initialized with AES-256-GCM");
    }

    public String encrypt(String plaintext) {
        if (!cryptoEnabled || plaintext == null || plaintext.isBlank()) return plaintext;

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Prefix IV to cipherText: base64(iv + cipherText)
            byte[] ivAndCipherText = new byte[GCM_IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, ivAndCipherText, 0, GCM_IV_LENGTH);
            System.arraycopy(cipherText, 0, ivAndCipherText, GCM_IV_LENGTH, cipherText.length);

            return Base64.getEncoder().encodeToString(ivAndCipherText);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt PII data", e);
        }
    }

    public String decrypt(String encryptedText) {
        if (!cryptoEnabled || encryptedText == null || encryptedText.isBlank()) return encryptedText;

        try {
            byte[] ivAndCipherText = Base64.getDecoder().decode(encryptedText);

            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(ivAndCipherText, 0, iv, 0, GCM_IV_LENGTH);

            byte[] cipherText = new byte[ivAndCipherText.length - GCM_IV_LENGTH];
            System.arraycopy(ivAndCipherText, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            byte[] plaintextBytes = cipher.doFinal(cipherText);
            return new String(plaintextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt data. Returning fallback.");
            return "***DECRYPT_FAILED***";
        }
    }
}
