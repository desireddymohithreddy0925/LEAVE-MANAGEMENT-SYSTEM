package com.leave_management_system.leave_management_system.security.sensitive;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

@Service
public class CryptoService {

    public static final String ENCRYPTION_PREFIX = "ENC:";
    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12; // 96-bit recommended for GCM
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final Set<String> INSECURE_DEFAULT_KEYS = Set.of(
            "leave-mgmt-system-master-encryption-key-256-bit",
            "defaultSecretKeyThatNeedsToBeVeryLongAndSecureEnoughForHS256AlgorithmToBeUsedSafelyAndSecurely",
            "defaultSecretKeyThatIsAtLeast32CharactersLongAndSecure!",
            "secret",
            "password",
            "12345678901234567890123456789012"
    );

    private final SecretKey secretKey;
    private final List<SecretKey> fallbackKeys;
    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoService(@Value("${app.security.encryption.key:}") String secret) {
        this(secret, null, null);
    }

    public CryptoService(String secret, Environment environment) {
        this(secret, null, environment);
    }

    public CryptoService(String secret, String previousKeys) {
        this(secret, previousKeys, null);
    }

    @Autowired
    public CryptoService(@Value("${app.security.encryption.key:}") String secret,
                         @Value("${app.security.encryption.previous-keys:}") String previousKeys,
                         @Autowired(required = false) Environment environment) {
        try {
            byte[] keyBytes = validateAndDeriveKey(secret, environment);
            this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);

            List<SecretKey> fallbacks = new ArrayList<>();

            // 1. Backward Compatibility: Always register legacy UTF-8 key derivation
            // if it differs from the primary derived key bytes.
            byte[] legacyUtf8KeyBytes = deriveLegacyUtf8Key(secret);
            if (legacyUtf8KeyBytes.length > 0 && !Arrays.equals(keyBytes, legacyUtf8KeyBytes)) {
                fallbacks.add(new SecretKeySpec(legacyUtf8KeyBytes, ALGORITHM));
            }

            // 2. Key Rotation: Register configured previous keys (both primary and legacy derivations)
            if (previousKeys != null && !previousKeys.isBlank()) {
                for (String prevKey : previousKeys.split(",")) {
                    String trimmed = prevKey.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            byte[] prevBytes = validateAndDeriveKey(trimmed, null);
                            if (!Arrays.equals(keyBytes, prevBytes)) {
                                fallbacks.add(new SecretKeySpec(prevBytes, ALGORITHM));
                            }
                        } catch (Exception ignored) {
                        }
                        byte[] prevLegacyBytes = deriveLegacyUtf8Key(trimmed);
                        if (prevLegacyBytes.length > 0 && !Arrays.equals(keyBytes, prevLegacyBytes)) {
                            fallbacks.add(new SecretKeySpec(prevLegacyBytes, ALGORITHM));
                        }
                    }
                }
            }

            this.fallbackKeys = Collections.unmodifiableList(fallbacks);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize cryptographic secret key", e);
        }
    }

    public static byte[] validateAndDeriveKey(String secret, Environment environment) {
        boolean isProd = environment != null && Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (secret == null || secret.isBlank()) {
            if (isProd) {
                throw new IllegalStateException("Production startup failed: ENCRYPTION_KEY environment variable is missing.");
            }
            throw new IllegalArgumentException("ENCRYPTION_KEY cannot be null or blank.");
        }

        String trimmed = secret.trim();
        if (isProd) {
            if (INSECURE_DEFAULT_KEYS.contains(trimmed) || trimmed.startsWith("REPLACE_WITH_") || trimmed.equalsIgnoreCase("CHANGE_ME")) {
                throw new IllegalStateException("Production startup failed: An insecure default or placeholder ENCRYPTION_KEY is configured. A unique, 256-bit key is required in production.");
            }
        }

        byte[] keyMaterial = resolveKeyBytes(trimmed);
        if (keyMaterial.length < 32) {
            String errorMsg = "ENCRYPTION_KEY must provide at least 256 bits (32 bytes) of key material for AES-256 security. Current length: " + keyMaterial.length + " bytes (" + (keyMaterial.length * 8) + " bits).";
            if (isProd) {
                throw new IllegalStateException("Production startup failed: " + errorMsg);
            }
            throw new IllegalArgumentException(errorMsg);
        }

        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return sha.digest(keyMaterial);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to digest cryptographic key", e);
        }
    }

    public static byte[] deriveLegacyUtf8Key(String secret) {
        if (secret == null || secret.isBlank()) {
            return new byte[0];
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            return sha.digest(secret.trim().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new byte[0];
        }
    }

    public static byte[] resolveKeyBytes(String secret) {
        if (secret == null || secret.isBlank()) {
            return new byte[0];
        }
        String trimmed = secret.trim();
        // 1. Try Base64 decoding if input matches base64 structure and length >= 32
        try {
            byte[] decoded = Base64.getDecoder().decode(trimmed);
            if (decoded.length >= 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
        }

        // 2. Try Hex decoding if 64+ hex characters (256 bits)
        if (trimmed.length() >= 64 && trimmed.matches("^[0-9a-fA-F]+$")) {
            try {
                byte[] hexBytes = HexFormat.of().parseHex(trimmed);
                if (hexBytes.length >= 32) {
                    return hexBytes;
                }
            } catch (Exception ignored) {
            }
        }

        // 3. Fall back to raw UTF-8 bytes
        return trimmed.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Encrypts plaintext using AES-256-GCM with a fresh random 12-byte IV.
     * Returns a string prefixed with "ENC:" and base64 encoded payload: [IV (12 bytes)][Ciphertext + Tag (16 bytes)].
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertextWithTag = cipher.doFinal(plaintextBytes);

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertextWithTag.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertextWithTag);

            return ENCRYPTION_PREFIX + Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new SecurityException("Failed to encrypt sensitive data", e);
        }
    }

    /**
     * Decrypts ciphertext previously encrypted by encrypt().
     * If input is null, returns null.
     * If input is not prefixed with "ENC:", returns the input as legacy plaintext.
     *
     * Compatibility & Key Rotation:
     * Attempts decryption using the current primary key. If authentication fails,
     * it systematically tries legacy key derivations and configured previous keys.
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        if (!isEncrypted(ciphertext)) {
            // Backward-compatible fallback for unencrypted legacy data
            return ciphertext;
        }

        // 1. Attempt decryption with primary secretKey
        try {
            return decryptWithKey(ciphertext, this.secretKey);
        } catch (Exception primaryException) {
            // 2. Attempt decryption with fallback / previous rotated keys
            if (this.fallbackKeys != null) {
                for (SecretKey fallbackKey : this.fallbackKeys) {
                    try {
                        return decryptWithKey(ciphertext, fallbackKey);
                    } catch (Exception ignored) {
                    }
                }
            }
            throw new SecurityException("Failed to decrypt or authenticate sensitive data", primaryException);
        }
    }

    private String decryptWithKey(String ciphertext, SecretKey key) throws Exception {
        String base64Payload = ciphertext.substring(ENCRYPTION_PREFIX.length());
        byte[] combined = Base64.getDecoder().decode(base64Payload);

        if (combined.length < GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
            throw new IllegalArgumentException("Invalid encrypted payload size");
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(combined);
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        byteBuffer.get(iv);

        byte[] encryptedBytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(encryptedBytes);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    /**
     * Re-encrypts ciphertext using the current primary key.
     * If the ciphertext was encrypted using a previous key or legacy derivation,
     * it safely decrypts it using the fallback key and re-encrypts with the primary key.
     */
    public String rotateCiphertext(String ciphertext) {
        if (ciphertext == null || !isEncrypted(ciphertext)) {
            return ciphertext;
        }
        String decrypted = decrypt(ciphertext);
        return encrypt(decrypted);
    }

    /**
     * Determines whether a given string is encrypted with the system prefix.
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTION_PREFIX);
    }

    /**
     * Generates a deterministic HMAC-SHA256 blind index for searching encrypted values.
     */
    public String generateHmacBlindIndex(String value) {
        if (value == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(value.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hmacBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new SecurityException("Failed to generate blind index", e);
        }
    }
}
