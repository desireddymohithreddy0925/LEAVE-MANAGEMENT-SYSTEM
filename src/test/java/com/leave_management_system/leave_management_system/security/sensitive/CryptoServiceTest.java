package com.leave_management_system.leave_management_system.security.sensitive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CryptoServiceTest {

    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService("my-secure-32-byte-test-key-for-aes-256");
    }

    @Test
    @DisplayName("encrypt and decrypt: round trip restores original plaintext")
    void encryptAndDecrypt_RoundTrip_Success() {
        String plaintext = "SuperSensitiveData!@#123";

        String ciphertext = cryptoService.encrypt(plaintext);
        assertNotNull(ciphertext);
        assertTrue(ciphertext.startsWith(CryptoService.ENCRYPTION_PREFIX));
        assertNotEquals(plaintext, ciphertext);

        String decrypted = cryptoService.decrypt(ciphertext);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("encrypt: null input returns null")
    void encrypt_Null_ReturnsNull() {
        assertNull(cryptoService.encrypt(null));
    }

    @Test
    @DisplayName("decrypt: null input returns null")
    void decrypt_Null_ReturnsNull() {
        assertNull(cryptoService.decrypt(null));
    }

    @Test
    @DisplayName("decrypt: legacy unencrypted data returns as-is")
    void decrypt_LegacyUnencrypted_ReturnsAsIs() {
        String unencrypted = "plain-unencrypted-legacy-string";

        String result = cryptoService.decrypt(unencrypted);
        assertEquals(unencrypted, result);
    }

    @Test
    @DisplayName("decrypt: tampered ciphertext throws SecurityException")
    void decrypt_TamperedCiphertext_ThrowsSecurityException() {
        String encrypted = cryptoService.encrypt("Sensitive Information");
        // Tamper with payload characters
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "AAAA";

        assertThrows(SecurityException.class, () -> cryptoService.decrypt(tampered));
    }

    @Test
    @DisplayName("isEncrypted: returns true only for strings starting with ENC:")
    void isEncrypted_ChecksPrefix() {
        assertTrue(cryptoService.isEncrypted("ENC:dGVzdA=="));
        assertFalse(cryptoService.isEncrypted("dGVzdA=="));
        assertFalse(cryptoService.isEncrypted(null));
    }

    @Test
    @DisplayName("generateHmacBlindIndex: returns deterministic HMAC-SHA256 hex string")
    void generateHmacBlindIndex_Deterministic() {
        String input1 = "user@example.com";
        String input2 = " USER@EXAMPLE.COM "; // differing whitespace and case

        String hmac1 = cryptoService.generateHmacBlindIndex(input1);
        String hmac2 = cryptoService.generateHmacBlindIndex(input2);

        assertNotNull(hmac1);
        assertEquals(hmac1, hmac2, "Normalized input must produce identical blind index");
        assertNull(cryptoService.generateHmacBlindIndex(null));
    }
}

