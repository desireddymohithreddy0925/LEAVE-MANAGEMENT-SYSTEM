package com.leave_management_system.leave_management_system.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Validates critical environment configuration upon application startup.
 * Enforces zero-trust production security invariants and aborts application
 * startup immediately if mandatory credentials, keys, or security configs are invalid.
 */
@Component
public class ProductionSecurityValidator {

    private static final Logger log = LoggerFactory.getLogger(ProductionSecurityValidator.class);

    private static final Set<String> INSECURE_PASSWORDS = Set.of(
            "postgres", "password", "admin", "root", "123456", "secret"
    );

    private static final Set<String> INSECURE_SECRETS = Set.of(
            "defaultSecretKeyThatNeedsToBeVeryLongAndSecureEnoughForHS256AlgorithmToBeUsedSafelyAndSecurely",
            "defaultSecretKeyThatIsAtLeast32CharactersLongAndSecure!",
            "leave-mgmt-system-master-encryption-key-256-bit",
            "secret", "password", "12345678901234567890123456789012"
    );

    private final Environment environment;

    public ProductionSecurityValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateProductionSecurity() {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!isProd) {
            return;
        }

        log.info("Executing comprehensive Production Security & Zero-Trust credential audit...");

        // 1. Mandatory Database Password
        String dbPassword = environment.getProperty("spring.datasource.password");
        if (dbPassword == null || dbPassword.isBlank()) {
            throw new IllegalStateException("Production startup failed: Mandatory DB_PASSWORD is missing.");
        }
        String trimmedDbPassword = dbPassword.trim();
        if (INSECURE_PASSWORDS.contains(trimmedDbPassword.toLowerCase()) || trimmedDbPassword.startsWith("REPLACE_WITH_") || trimmedDbPassword.equalsIgnoreCase("CHANGE_ME")) {
            throw new IllegalStateException("Production startup failed: An insecure default or placeholder DB_PASSWORD is configured.");
        }

        // 2. Mandatory JWT Secret
        String jwtSecret = environment.getProperty("app.jwt.secret");
        if (jwtSecret == null || jwtSecret.isBlank()) {
            jwtSecret = environment.getProperty("jwt.secret");
        }
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("Production startup failed: Mandatory JWT_SECRET is missing.");
        }
        String trimmedJwtSecret = jwtSecret.trim();
        if (INSECURE_SECRETS.contains(trimmedJwtSecret) || trimmedJwtSecret.startsWith("REPLACE_WITH_") || trimmedJwtSecret.equalsIgnoreCase("CHANGE_ME")) {
            throw new IllegalStateException("Production startup failed: An insecure default or placeholder JWT_SECRET is configured. A unique 256-bit secret is required.");
        }

        // 3. Mandatory Encryption Key
        String encryptionKey = environment.getProperty("app.security.encryption.key");
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException("Production startup failed: Mandatory ENCRYPTION_KEY is missing in production profile.");
        }
        String trimmedEncKey = encryptionKey.trim();
        if (INSECURE_SECRETS.contains(trimmedEncKey) || trimmedEncKey.startsWith("REPLACE_WITH_") || trimmedEncKey.equalsIgnoreCase("CHANGE_ME")) {
            throw new IllegalStateException("Production startup failed: An insecure default or placeholder ENCRYPTION_KEY is configured. A unique 256-bit key is required in production.");
        }

        log.info("Production Security Audit PASSED: All mandatory production credentials and encryption keys verified.");
    }
}

