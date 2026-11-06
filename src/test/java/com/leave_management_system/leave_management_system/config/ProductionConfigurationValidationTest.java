package com.leave_management_system.leave_management_system.config;

import com.leave_management_system.leave_management_system.config.properties.AppProperties;
import com.leave_management_system.leave_management_system.security.ClientIpResolver;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import com.leave_management_system.leave_management_system.security.idempotency.IdempotencyCleanupScheduler;
import com.leave_management_system.leave_management_system.security.idempotency.IdempotencyFilter;
import com.leave_management_system.leave_management_system.security.idempotency.IdempotencyService;
import com.leave_management_system.leave_management_system.security.sensitive.CryptoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Production Security & Configuration Validation Tests")
public class ProductionConfigurationValidationTest {

    @Configuration
    static class TestContextConfiguration {
        @Bean
        public IdempotencyService idempotencyService() {
            return new IdempotencyService((com.leave_management_system.leave_management_system.repository.IdempotencyKeyRepository) null);
        }
    }

    private static final String VALID_PROD_JWT_SECRET = "c2VjdXJlUmFuZG9tSldUU2VjcmV0S2V5MjAyNkZvckxlYXZlTWFuYWdlbWVudFN5c3RlbSE=";
    private static final String VALID_PROD_ENCRYPTION_KEY = "ci-test-master-encryption-key-256-bit";
    private static final String VALID_PROD_DB_PASSWORD = "StrongProductionDbPassword2026!#";
    private static final String VALID_PROXIES = "127.0.0.1,::1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16";

    private final ApplicationContextRunner baseRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    TestContextConfiguration.class,
                    ProductionSecurityValidator.class,
                    CryptoService.class,
                    JwtUtils.class,
                    ClientIpResolver.class,
                    IdempotencyFilter.class,
                    IdempotencyCleanupScheduler.class,
                    AppProperties.class
            )
            .withPropertyValues(
                    "app.security.encryption.key=" + VALID_PROD_ENCRYPTION_KEY,
                    "app.jwt.secret=" + VALID_PROD_JWT_SECRET
            );

    private boolean anyCauseContains(Throwable throwable, String search) {
        Throwable cur = throwable;
        while (cur != null) {
            if (cur.getMessage() != null && cur.getMessage().contains(search)) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private Throwable getRootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    @Test
    @DisplayName("Production startup fails when ENCRYPTION_KEY is missing")
    void productionStartup_Fails_WhenEncryptionKeyIsMissing() {
        baseRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.password=" + VALID_PROD_DB_PASSWORD,
                        "app.jwt.secret=" + VALID_PROD_JWT_SECRET,
                        "app.security.encryption.key="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("ENCRYPTION_KEY");
                });
    }

    @Test
    @DisplayName("Production startup fails when ENCRYPTION_KEY is an insecure default key")
    void productionStartup_Fails_WhenEncryptionKeyIsDefault() {
        baseRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.password=" + VALID_PROD_DB_PASSWORD,
                        "app.jwt.secret=" + VALID_PROD_JWT_SECRET,
                        "app.security.encryption.key=leave-mgmt-system-master-encryption-key-256-bit"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("insecure default or placeholder ENCRYPTION_KEY");
                });
    }

    @Test
    @DisplayName("Production startup fails when ENCRYPTION_KEY is a placeholder")
    void productionStartup_Fails_WhenEncryptionKeyIsPlaceholder() {
        baseRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.password=" + VALID_PROD_DB_PASSWORD,
                        "app.jwt.secret=" + VALID_PROD_JWT_SECRET,
                        "app.security.encryption.key=REPLACE_WITH_256_BIT_SECRET_FOR_SENSITIVE_DATA_ENCRYPTION"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("insecure default or placeholder ENCRYPTION_KEY");
                });
    }

    @Test
    @DisplayName("Production startup fails when ENCRYPTION_KEY has fewer than 256 bits (32 bytes)")
    void productionStartup_Fails_WhenEncryptionKeyIsTooShort() {
        baseRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.password=" + VALID_PROD_DB_PASSWORD,
                        "app.jwt.secret=" + VALID_PROD_JWT_SECRET,
                        "app.security.encryption.key=short-key-16-bytes!"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("256 bits");
                });
    }

    @Test
    @DisplayName("Production startup fails when JWT_SECRET is missing")
    void productionStartup_Fails_WhenJwtSecretIsMissing() {
        baseRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.password=" + VALID_PROD_DB_PASSWORD,
                        "app.security.encryption.key=" + VALID_PROD_ENCRYPTION_KEY,
                        "app.jwt.secret="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("JWT_SECRET");
                });
    }

    @Test
    @DisplayName("Production startup fails when JWT_SECRET is an insecure default")
    void productionStartup_Fails_WhenJwtSecretIsDefault() {
        baseRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.password=" + VALID_PROD_DB_PASSWORD,
                        "app.security.encryption.key=" + VALID_PROD_ENCRYPTION_KEY,
                        "app.jwt.secret=defaultSecretKeyThatNeedsToBeVeryLongAndSecureEnoughForHS256AlgorithmToBeUsedSafelyAndSecurely"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("insecure default or placeholder JWT_SECRET");
                });
    }

    @Test
    @DisplayName("Production startup fails when JWT_SECRET is a placeholder")
    void productionStartup_Fails_WhenJwtSecretIsPlaceholder() {
        baseRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.password=" + VALID_PROD_DB_PASSWORD,
                        "app.security.encryption.key=" + VALID_PROD_ENCRYPTION_KEY,
                        "app.jwt.secret=REPLACE_WITH_256_BIT_BASE64_SECRET_KEY_GENERATED_VIA_OPENSSL_RAND"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("insecure default or placeholder JWT_SECRET");
                });
    }

    @Test
    @DisplayName("Production startup fails when JWT_SECRET has fewer than 256 bits (32 bytes)")
    void productionStartup_Fails_WhenJwtSecretIsTooShort() {
        baseRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.password=" + VALID_PROD_DB_PASSWORD,
                        "app.security.encryption.key=" + VALID_PROD_ENCRYPTION_KEY,
                        "app.jwt.secret=short-key"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("256 bits");
                });
    }

    @Test
    @DisplayName("Production startup fails when DB_PASSWORD is an insecure default")
    void productionStartup_Fails_WhenDbPasswordIsDefault() {
        baseRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.password=postgres",
                        "app.jwt.secret=" + VALID_PROD_JWT_SECRET,
                        "app.security.encryption.key=" + VALID_PROD_ENCRYPTION_KEY
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("insecure default or placeholder DB_PASSWORD");
                });
    }

    @Test
    @DisplayName("Production startup fails when DB_PASSWORD is a placeholder")
    void productionStartup_Fails_WhenDbPasswordIsPlaceholder() {
        baseRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.password=REPLACE_WITH_STRONG_PRODUCTION_DB_PASSWORD",
                        "app.jwt.secret=" + VALID_PROD_JWT_SECRET,
                        "app.security.encryption.key=" + VALID_PROD_ENCRYPTION_KEY
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("insecure default or placeholder DB_PASSWORD");
                });
    }

    @Test
    @DisplayName("Startup fails when trusted proxy address is invalid")
    void startup_Fails_WhenTrustedProxyIsInvalid() {
        baseRunner
                .withPropertyValues(
                        "spring.profiles.active=dev",
                        "app.security.trusted-proxies=invalid_ip_address_or_mask"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalArgumentException.class);
                    assertThat(anyCauseContains(context.getStartupFailure(), "Invalid trusted proxy address or CIDR notation"))
                            .isTrue();
                });
    }

    @Test
    @DisplayName("Startup fails when idempotency max body size is zero or negative")
    void startup_Fails_WhenIdempotencyMaxBodySizeIsInvalid() {
        baseRunner
                .withPropertyValues(
                        "spring.profiles.active=dev",
                        "idempotency.max-body-size-bytes=-1"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("max-body-size-bytes must be greater than 0");
                });
    }

    @Test
    @DisplayName("Startup fails when idempotency cleanup rate is less than 1,000 ms")
    void startup_Fails_WhenIdempotencyCleanupRateIsTooLow() {
        baseRunner
                .withPropertyValues(
                        "spring.profiles.active=dev",
                        "idempotency.cleanup-rate-ms=500"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageContaining("cleanup-rate-ms must be at least 1,000 ms");
                });
    }

    @Test
    @DisplayName("Production startup succeeds when all production secrets and configurations are valid")
    void productionStartup_Succeeds_WhenAllSecretsValid() {
        baseRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.password=" + VALID_PROD_DB_PASSWORD,
                        "app.jwt.secret=" + VALID_PROD_JWT_SECRET,
                        "app.security.encryption.key=" + VALID_PROD_ENCRYPTION_KEY,
                        "app.security.trusted-proxies=" + VALID_PROXIES,
                        "idempotency.max-body-size-bytes=2097152"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProductionSecurityValidator.class);
                    assertThat(context).hasSingleBean(CryptoService.class);
                    assertThat(context).hasSingleBean(JwtUtils.class);
                    assertThat(context).hasSingleBean(ClientIpResolver.class);
                });
    }

    @Test
    @DisplayName("Development startup succeeds with default credentials and keys")
    void developmentStartup_Succeeds_WithDefaultValues() {
        baseRunner
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CryptoService.class);
                    assertThat(context).hasSingleBean(JwtUtils.class);
                });
    }
}
