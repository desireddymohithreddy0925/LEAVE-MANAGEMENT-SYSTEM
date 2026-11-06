package com.leave_management_system.leave_management_system.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly-typed configuration properties for the Leave Management System.
 * Binds properties under the 'app' namespace from environment-specific configuration files.
 */
@Configuration
@ConfigurationProperties(prefix = "app")
@Validated
public class AppProperties {

    @Valid
    private JwtProperties jwt = new JwtProperties();

    @Valid
    private SecurityProperties security = new SecurityProperties();

    @Valid
    private IdempotencyProperties idempotency = new IdempotencyProperties();

    public JwtProperties getJwt() {
        return jwt;
    }

    public void setJwt(JwtProperties jwt) {
        this.jwt = jwt;
    }

    public SecurityProperties getSecurity() {
        return security;
    }

    public void setSecurity(SecurityProperties security) {
        this.security = security;
    }

    public IdempotencyProperties getIdempotency() {
        return idempotency;
    }

    public void setIdempotency(IdempotencyProperties idempotency) {
        this.idempotency = idempotency;
    }

    /**
     * JWT token expiration and signing properties.
     */
    public static class JwtProperties {

        @NotBlank(message = "JWT secret cannot be blank")
        @Size(min = 32, message = "JWT secret must provide at least 256 bits (32 bytes) of key material")
        private String secret;

        @NotNull
        @Min(value = 60000, message = "Access token expiration must be at least 1 minute (60,000 ms)")
        private Long expirationMs = 900000L; // 15 minutes default

        @NotNull
        @Min(value = 300000, message = "Refresh token expiration must be at least 5 minutes")
        private Long refreshExpirationMs = 604800000L; // 7 days default

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Long getExpirationMs() {
            return expirationMs;
        }

        public void setExpirationMs(Long expirationMs) {
            this.expirationMs = expirationMs;
        }

        public Long getRefreshExpirationMs() {
            return refreshExpirationMs;
        }

        public void setRefreshExpirationMs(Long refreshExpirationMs) {
            this.refreshExpirationMs = refreshExpirationMs;
        }
    }

    /**
     * Security, proxy trust, lockout, and encryption configuration.
     */
    public static class SecurityProperties {

        @NotBlank(message = "Trusted proxies list cannot be blank")
        private String trustedProxies = "127.0.0.1,::1,0:0:0:0:0:0:0:1";

        @Valid
        private LockoutProperties lockout = new LockoutProperties();

        @Valid
        private EncryptionProperties encryption = new EncryptionProperties();

        public String getTrustedProxies() {
            return trustedProxies;
        }

        public void setTrustedProxies(String trustedProxies) {
            this.trustedProxies = trustedProxies;
        }

        public LockoutProperties getLockout() {
            return lockout;
        }

        public void setLockout(LockoutProperties lockout) {
            this.lockout = lockout;
        }

        public EncryptionProperties getEncryption() {
            return encryption;
        }

        public void setEncryption(EncryptionProperties encryption) {
            this.encryption = encryption;
        }
    }

    /**
     * Brute force attack prevention & account lockout thresholds.
     */
    public static class LockoutProperties {

        @Min(value = 1, message = "Max failed attempts must be at least 1")
        @Max(value = 20, message = "Max failed attempts cannot exceed 20")
        private int maxFailedAttempts = 5;

        @Min(value = 1, message = "Lockout duration must be at least 1 minute")
        @Max(value = 1440, message = "Lockout duration cannot exceed 24 hours (1440 minutes)")
        private int durationMinutes = 15;

        public int getMaxFailedAttempts() {
            return maxFailedAttempts;
        }

        public void setMaxFailedAttempts(int maxFailedAttempts) {
            this.maxFailedAttempts = maxFailedAttempts;
        }

        public int getDurationMinutes() {
            return durationMinutes;
        }

        public void setDurationMinutes(int durationMinutes) {
            this.durationMinutes = durationMinutes;
        }
    }

    /**
     * Data encryption at rest properties.
     */
    public static class EncryptionProperties {

        @NotBlank(message = "Encryption key cannot be blank")
        @Size(min = 32, message = "Encryption key must provide at least 256 bits (32 bytes) of key material")
        private String key;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }

    /**
     * Idempotency filtering and scheduled cleanup configuration.
     */
    public static class IdempotencyProperties {

        @Min(value = 1024, message = "Max body size must be at least 1 KB")
        private long maxBodySizeBytes = 1048576L; // 1 MB default

        @Min(value = 10000, message = "Cleanup rate must be at least 10 seconds")
        private long cleanupRateMs = 3600000L; // 1 hour default

        private long cleanupInitialDelayMs = 60000L; // 1 minute default

        public long getMaxBodySizeBytes() {
            return maxBodySizeBytes;
        }

        public void setMaxBodySizeBytes(long maxBodySizeBytes) {
            this.maxBodySizeBytes = maxBodySizeBytes;
        }

        public long getCleanupRateMs() {
            return cleanupRateMs;
        }

        public void setCleanupRateMs(long cleanupRateMs) {
            this.cleanupRateMs = cleanupRateMs;
        }

        public long getCleanupInitialDelayMs() {
            return cleanupInitialDelayMs;
        }

        public void setCleanupInitialDelayMs(long cleanupInitialDelayMs) {
            this.cleanupInitialDelayMs = cleanupInitialDelayMs;
        }
    }
}

