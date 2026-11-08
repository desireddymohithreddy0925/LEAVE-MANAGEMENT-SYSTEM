package com.leave_management_system.leave_management_system.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.config.ProductionSecurityValidator;
import com.leave_management_system.leave_management_system.config.properties.AppProperties;
import com.leave_management_system.leave_management_system.dto.CryptoRequestDTO;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.idempotency.IdempotencyCleanupScheduler;
import com.leave_management_system.leave_management_system.security.idempotency.IdempotencyFilter;
import com.leave_management_system.leave_management_system.security.idempotency.IdempotencyService;
import com.leave_management_system.leave_management_system.security.sensitive.AesGcmEncryptionConverter;
import com.leave_management_system.leave_management_system.security.sensitive.CryptoService;
import com.leave_management_system.leave_management_system.service.SecurityEventManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Enterprise Security Audit: Secrets, Encryption & Trusted Proxy Configuration Review.
 *
 * This test suite verifies:
 * 1. Zero-Trust Production Secrets Enforcement (DB Password, JWT Secret, Encryption Key invariants).
 * 2. Cryptographic Security & Robustness (AES-256-GCM authenticated encryption, IND-CPA random IVs, AEAD integrity).
 * 3. Backward Compatibility & Key Rotation (Decryption of legacy UTF-8 derived ciphertext, unencrypted pass-through, re-encryption).
 * 4. Deterministic HMAC-SHA256 Blind Indexing for Encrypted Search without plaintext leakage.
 * 5. JPA AttributeConverter integration for transparent field encryption.
 * 6. Trusted Reverse-Proxy Boundary Enforcement & Anti-Spoofing (CIDR matching, untrusted header discarding, chained hops).
 * 7. End-to-End Threat Mitigation: Spoofed IP headers cannot evade brute force detection or IP lockout.
 * 8. REST Endpoint Cryptographic Authorization & Tamper Rejection (/api/sensitive-data/encrypt & /decrypt).
 */
@SpringBootTest
@ActiveProfiles("test")
public class SecretsEncryptionAndProxySecurityAuditIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private SecurityEventRepository securityEventRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuthorizationAuditLogRepository authorizationAuditLogRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private TransactionReviewRepository transactionReviewRepository;

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private AesGcmEncryptionConverter aesGcmConverter;

    @Autowired
    private ClientIpResolver clientIpResolver;

    @Autowired
    private SecurityEventManagementService securityEventManagementService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private String runId;
    private User adminUser;
    private String adminToken;
    private User employeeUser;
    private String employeeToken;
    private User managerUser;
    private String managerToken;

    private static final String VALID_PROD_JWT_SECRET = "c2VjdXJlUmFuZG9tSldUU2VjcmV0S2V5MjAyNkZvckxlYXZlTWFuYWdlbWVudFN5c3RlbSE=";
    private static final String VALID_PROD_ENCRYPTION_KEY = "ci-test-master-encryption-key-256-bit";
    private static final String VALID_PROD_DB_PASSWORD = "StrongProductionDbPassword2026!#";
    private static final String VALID_PROXIES = "127.0.0.1,::1,10.0.1.15/32,192.168.1.100/32";

    private final ApplicationContextRunner prodContextRunner = new ApplicationContextRunner()
            .withBean(IdempotencyService.class, () -> new IdempotencyService((com.leave_management_system.leave_management_system.repository.IdempotencyKeyRepository) null))
            .withUserConfiguration(
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

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        cleanup();

        runId = UUID.randomUUID().toString().substring(0, 8);

        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));
        Role managerRole = roleRepository.findByName("MANAGER").orElseGet(() -> roleRepository.save(new Role("MANAGER")));

        Permission permAdminSecurity = getOrCreatePermission("ADMIN_SECURITY_VIEW", "Access cryptographic encryption tools");
        Permission permEmpView = getOrCreatePermission("EMPLOYEE_VIEW", "View employee profiles");
        Permission permLeaveOwn = getOrCreatePermission("LEAVE_VIEW_OWN", "View own leave requests");

        adminRole.getPermissions().addAll(List.of(permAdminSecurity, permEmpView, permLeaveOwn));
        roleRepository.save(adminRole);

        managerRole.getPermissions().add(permEmpView);
        roleRepository.save(managerRole);

        employeeRole.getPermissions().add(permLeaveOwn);
        roleRepository.save(employeeRole);

        // Admin User
        adminUser = new User();
        adminUser.setEmail("sec-admin-" + runId + "@company.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);
        adminToken = generateToken(adminUser, "ROLE_ADMIN", adminRole.getPermissions());

        // Employee User
        employeeUser = new User();
        employeeUser.setEmail("sec-emp-" + runId + "@company.com");
        employeeUser.setPassword(passwordEncoder.encode("EmpPass123!"));
        employeeUser.setActive(true);
        employeeUser.getRoles().add(employeeRole);
        employeeUser = userRepository.save(employeeUser);
        employeeToken = generateToken(employeeUser, "ROLE_EMPLOYEE", employeeRole.getPermissions());

        // Manager User
        managerUser = new User();
        managerUser.setEmail("sec-mgr-" + runId + "@company.com");
        managerUser.setPassword(passwordEncoder.encode("MgrPass123!"));
        managerUser.setActive(true);
        managerUser.getRoles().add(managerRole);
        managerUser = userRepository.save(managerUser);
        managerToken = generateToken(managerUser, "ROLE_MANAGER", managerRole.getPermissions());
    }

    @AfterEach
    public void cleanup() {
        RequestContextHolder.resetRequestAttributes();

        transactionReviewRepository.deleteAll();
        leaveRequestRepository.deleteAll();
        leaveBalanceRepository.deleteAll();
        leaveTypeRepository.deleteAll();
        authorizationAuditLogRepository.deleteAll();
        securityEventRepository.deleteAll();
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginHistoryRepository.deleteAll();

        employeeRepository.findAll().forEach(e -> {
            e.setManager(null);
            e.setDepartment(null);
            employeeRepository.save(e);
        });
        departmentRepository.findAll().forEach(d -> {
            d.setManager(null);
            departmentRepository.save(d);
        });

        employeeRepository.deleteAll();
        userRepository.deleteAll();
    }

    private Permission getOrCreatePermission(String name, String description) {
        return permissionRepository.findByName(name)
                .orElseGet(() -> permissionRepository.save(new Permission(name, description)));
    }

    private String generateToken(User user, String rolePrefix, Set<Permission> permissions) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (rolePrefix != null) {
            authorities.add(new SimpleGrantedAuthority(rolePrefix));
        }
        if (permissions != null) {
            permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p.getName())));
        }
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPassword(), authorities);
        return jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
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

    // =========================================================================================
    // Category 1: Production Secrets Zero-Trust Validation & Environment Invariants
    // =========================================================================================

    @Test
    @DisplayName("Secrets 1: Production startup aborts when DB_PASSWORD is missing or insecure default")
    public void test1_SecretsAudit_ProductionStartup_FailsWhenDbPasswordMissingOrInsecure() {
        // Missing DB_PASSWORD
        prodContextRunner
                .withPropertyValues("spring.profiles.active=prod", "spring.datasource.password=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("DB_PASSWORD is missing");
                });

        // Insecure default ("postgres")
        prodContextRunner
                .withPropertyValues("spring.profiles.active=prod", "spring.datasource.password=postgres")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("insecure default or placeholder DB_PASSWORD");
                });

        // Placeholder password
        prodContextRunner
                .withPropertyValues("spring.profiles.active=prod", "spring.datasource.password=REPLACE_WITH_STRONG_PRODUCTION_DB_PASSWORD")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("insecure default or placeholder DB_PASSWORD");
                });
    }

    @Test
    @DisplayName("Secrets 2: Production startup aborts when JWT_SECRET is missing, default, or fewer than 256 bits")
    public void test2_SecretsAudit_ProductionStartup_FailsWhenJwtSecretMissingOrInsecureOrSub256Bit() {
        // Missing JWT_SECRET
        prodContextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.password=" + VALID_PROD_DB_PASSWORD,
                        "app.jwt.secret="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("JWT_SECRET");
                });

        // Insecure default JWT_SECRET
        prodContextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.password=" + VALID_PROD_DB_PASSWORD,
                        "app.jwt.secret=defaultSecretKeyThatNeedsToBeVeryLongAndSecureEnoughForHS256AlgorithmToBeUsedSafelyAndSecurely"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("insecure default or placeholder JWT_SECRET");
                });

        // Sub-256-bit JWT_SECRET (< 32 bytes)
        prodContextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.password=" + VALID_PROD_DB_PASSWORD,
                        "app.jwt.secret=too-short-key"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("256 bits");
                });
    }

    @Test
    @DisplayName("Secrets 3: Production startup aborts when ENCRYPTION_KEY is missing, default, or fewer than 256 bits")
    public void test3_SecretsAudit_ProductionStartup_FailsWhenEncryptionKeyMissingOrInsecureOrSub256Bit() {
        // Missing ENCRYPTION_KEY
        prodContextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.password=" + VALID_PROD_DB_PASSWORD,
                        "app.security.encryption.key="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("ENCRYPTION_KEY");
                });

        // Insecure default ENCRYPTION_KEY
        prodContextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.password=" + VALID_PROD_DB_PASSWORD,
                        "app.security.encryption.key=leave-mgmt-system-master-encryption-key-256-bit"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("insecure default or placeholder ENCRYPTION_KEY");
                });

        // Sub-256-bit ENCRYPTION_KEY (< 32 bytes)
        prodContextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.password=" + VALID_PROD_DB_PASSWORD,
                        "app.security.encryption.key=weak-16-byte-key"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(getRootCause(context.getStartupFailure()))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("256 bits");
                });
    }

    @Test
    @DisplayName("Secrets 4: Development profile allows default credentials and keys for seamless local development")
    public void test4_SecretsAudit_DevelopmentProfile_PermitsDefaultSecrets() {
        prodContextRunner
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CryptoService.class);
                    assertThat(context).hasSingleBean(JwtUtils.class);
                });
    }

    @Test
    @DisplayName("Secrets 5: Production startup succeeds when all zero-trust secrets and keys are valid")
    public void test5_SecretsAudit_ProductionStartup_SucceedsWithValidSecrets() {
        prodContextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.password=" + VALID_PROD_DB_PASSWORD,
                        "app.jwt.secret=" + VALID_PROD_JWT_SECRET,
                        "app.security.encryption.key=" + VALID_PROD_ENCRYPTION_KEY,
                        "app.security.trusted-proxies=" + VALID_PROXIES
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProductionSecurityValidator.class);
                    assertThat(context).hasSingleBean(CryptoService.class);
                    assertThat(context).hasSingleBean(JwtUtils.class);
                    assertThat(context).hasSingleBean(ClientIpResolver.class);
                });
    }

    // =========================================================================================
    // Category 2: Cryptographic Encryption Implementation & Robustness (AES-256-GCM)
    // =========================================================================================

    @Test
    @DisplayName("Encryption 6: AES-256-GCM round trip: plaintext encrypts with 'ENC:' prefix and decrypts identically")
    public void test6_EncryptionAudit_Aes256Gcm_RoundTripAndPrefix() {
        String sensitiveSalary = "$165,000.00 USD";
        String ciphertext = cryptoService.encrypt(sensitiveSalary);

        assertNotNull(ciphertext);
        assertTrue(ciphertext.startsWith(CryptoService.ENCRYPTION_PREFIX), "Ciphertext must be tagged with standard ENC: prefix");
        assertNotEquals(sensitiveSalary, ciphertext);

        String decrypted = cryptoService.decrypt(ciphertext);
        assertEquals(sensitiveSalary, decrypted, "Decrypted data must match original plaintext exactly");
    }

    @Test
    @DisplayName("Encryption 7: IND-CPA Semantic Security: Encrypting identical plaintext yields distinct ciphertexts")
    public void test7_EncryptionAudit_IndCpa_ProbabilisticEncryptionWithRandomIv() {
        String sensitiveData = "Employee-SSN-123-45-6789";

        String ciphertext1 = cryptoService.encrypt(sensitiveData);
        String ciphertext2 = cryptoService.encrypt(sensitiveData);
        String ciphertext3 = cryptoService.encrypt(sensitiveData);

        assertNotEquals(ciphertext1, ciphertext2, "AES-GCM must use a distinct random IV for every encryption call");
        assertNotEquals(ciphertext2, ciphertext3);
        assertNotEquals(ciphertext1, ciphertext3);

        // All three distinct ciphertexts must decrypt to the identical plaintext
        assertEquals(sensitiveData, cryptoService.decrypt(ciphertext1));
        assertEquals(sensitiveData, cryptoService.decrypt(ciphertext2));
        assertEquals(sensitiveData, cryptoService.decrypt(ciphertext3));
    }

    @Test
    @DisplayName("Encryption 8: AEAD Integrity: Tampering with ciphertext or authentication tag throws SecurityException")
    public void test8_EncryptionAudit_AeadIntegrity_TamperedCiphertextRejection() {
        String sensitiveData = "CONFIDENTIAL_EXECUTIVE_SALARY";
        String originalCiphertext = cryptoService.encrypt(sensitiveData);

        // Corrupt characters in the payload
        String tampered = originalCiphertext.substring(0, originalCiphertext.length() - 6) + "XXXXXX";

        SecurityException thrown = assertThrows(SecurityException.class, () -> cryptoService.decrypt(tampered),
                "Tampered ciphertext must be rejected by GCM authentication tag verification");
        assertTrue(thrown.getMessage().contains("Failed to decrypt or authenticate sensitive data"));
    }

    @Test
    @DisplayName("Encryption 9: Key Derivation: Supports 256-bit Base64, Hex, and raw byte keys with SHA-256 normalization")
    public void test9_EncryptionAudit_KeyDerivation_SupportsBase64HexAndRawBytes() {
        // 1. Base64-encoded key
        String base64Key = "c2VjdXJlUmFuZG9tSldUU2VjcmV0S2V5MjAyNkZvckxlYXZlTWFuYWdlbWVudFN5c3RlbSE=";
        CryptoService b64Service = new CryptoService(base64Key);
        String cipherB64 = b64Service.encrypt("Payload1");
        assertEquals("Payload1", b64Service.decrypt(cipherB64));

        // 2. Hex-encoded key (64 hex characters = 256 bits)
        String hexKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        CryptoService hexService = new CryptoService(hexKey);
        String cipherHex = hexService.encrypt("Payload2");
        assertEquals("Payload2", hexService.decrypt(cipherHex));

        // 3. Raw UTF-8 key (>= 32 characters)
        String rawKey = "master-raw-passphrase-with-at-least-32-chars-long";
        CryptoService rawService = new CryptoService(rawKey);
        String cipherRaw = rawService.encrypt("Payload3");
        assertEquals("Payload3", rawService.decrypt(cipherRaw));
    }

    @Test
    @DisplayName("Encryption 10: HMAC Blind Indexing: Produces deterministic HMAC-SHA256 digests for encrypted search")
    public void test10_EncryptionAudit_HmacBlindIndexing_DeterministicNormalizedSearch() {
        String inputA = "executive@company.com";
        String inputB = "   EXECUTIVE@COMPANY.COM   "; // Whitespace and uppercase variation

        String hmacA = cryptoService.generateHmacBlindIndex(inputA);
        String hmacB = cryptoService.generateHmacBlindIndex(inputB);

        assertNotNull(hmacA);
        assertEquals(64, hmacA.length(), "HMAC-SHA256 hex string must be 64 characters");
        assertEquals(hmacA, hmacB, "Normalized search terms must produce identical blind index for DB lookup");

        String hmacDifferent = cryptoService.generateHmacBlindIndex("other@company.com");
        assertNotEquals(hmacA, hmacDifferent);

        assertNull(cryptoService.generateHmacBlindIndex(null));
    }

    // =========================================================================================
    // Category 3: Legacy Compatibility & Key Rotation Migration
    // =========================================================================================

    @Test
    @DisplayName("Encryption 11: Backward Compatibility: Decrypts legacy ciphertext generated by previous derivation")
    public void test11_EncryptionAudit_BackwardCompatibility_DecryptsLegacyUtf8Ciphertext() throws Exception {
        String key = "c2VjdXJlUmFuZG9tSldUU2VjcmV0S2V5MjAyNkZvckxlYXZlTWFuYWdlbWVudFN5c3RlbSE=";
        String plaintext = "HistoricEncryptedRecord-PersonalData-1999";

        // Generate ciphertext using previous implementation's direct UTF-8 SHA-256 derivation
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] legacyKeyBytes = sha.digest(key.getBytes(StandardCharsets.UTF_8));
        SecretKey legacyKey = new SecretKeySpec(legacyKeyBytes, "AES");

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, legacyKey, new GCMParameterSpec(128, iv));
        byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        ByteBuffer buf = ByteBuffer.allocate(iv.length + encryptedBytes.length);
        buf.put(iv);
        buf.put(encryptedBytes);
        String legacyCiphertext = CryptoService.ENCRYPTION_PREFIX + Base64.getEncoder().encodeToString(buf.array());

        // Modern CryptoService must successfully decrypt this legacy record
        CryptoService modernCryptoService = new CryptoService(key);
        String decrypted = modernCryptoService.decrypt(legacyCiphertext);
        assertEquals(plaintext, decrypted, "Modern CryptoService must decrypt records produced by the previous implementation");
    }

    @Test
    @DisplayName("Encryption 12: Backward Compatibility: Passes through unencrypted legacy records without modification")
    public void test12_EncryptionAudit_BackwardCompatibility_PassesThroughUnencryptedLegacyData() {
        String legacyPlaintext = "LegacyUnencryptedDataRecordWithoutPrefix";
        String result = cryptoService.decrypt(legacyPlaintext);
        assertEquals(legacyPlaintext, result, "Legacy records without ENC: prefix must pass through untouched");

        assertNull(cryptoService.decrypt(null));
    }

    @Test
    @DisplayName("Encryption 13: Key Rotation: Decrypts records encrypted under retired keys from previous-keys list")
    public void test13_EncryptionAudit_KeyRotation_DecryptsWithPreviousKeys() {
        String oldKey = "old-master-encryption-key-for-rotation-32-chars";
        String newKey = "new-master-encryption-key-for-rotation-32-chars";
        String payload = "ConfidentialSalaryRecord-$195,000";

        CryptoService oldService = new CryptoService(oldKey);
        String ciphertextOldKey = oldService.encrypt(payload);

        // Initialize service with newKey as primary and oldKey in previousKeys
        CryptoService rotatedService = new CryptoService(newKey, oldKey);
        String decrypted = rotatedService.decrypt(ciphertextOldKey);
        assertEquals(payload, decrypted, "Rotated service must successfully decrypt records encrypted under retired key");
    }

    @Test
    @DisplayName("Encryption 14: Safe Migration: rotateCiphertext() re-encrypts old data under the current primary key")
    public void test14_EncryptionAudit_KeyRotation_SafeMigrationReEncryption() {
        String oldKey = "old-key-version-1-for-data-migration-32-chars";
        String newKey = "new-key-version-2-for-data-migration-32-chars";
        String payload = "PersonalHealthData-Patient-Record-99";

        CryptoService oldService = new CryptoService(oldKey);
        String ciphertextOld = oldService.encrypt(payload);

        // Migrate ciphertext using rotated service
        CryptoService rotatedService = new CryptoService(newKey, oldKey);
        String migratedCiphertext = rotatedService.rotateCiphertext(ciphertextOld);

        assertNotNull(migratedCiphertext);
        assertTrue(migratedCiphertext.startsWith(CryptoService.ENCRYPTION_PREFIX));
        assertNotEquals(ciphertextOld, migratedCiphertext);

        // Verify migrated ciphertext is decryptable by a service instance having ONLY the new key
        CryptoService newOnlyService = new CryptoService(newKey);
        String finalDecrypted = newOnlyService.decrypt(migratedCiphertext);
        assertEquals(payload, finalDecrypted, "Migrated record must be fully decryptable using only the new primary key");
    }

    @Test
    @DisplayName("Encryption 15: JPA Attribute Converter: Transparently encrypts on write and decrypts on read")
    public void test15_EncryptionAudit_JpaAttributeConverter_TransparentEntityFieldEncryption() {
        String rawAttribute = "SensitiveEmployeeBonus$25000";

        // Convert to database column (Write)
        String dbColumnValue = aesGcmConverter.convertToDatabaseColumn(rawAttribute);
        assertNotNull(dbColumnValue);
        assertTrue(dbColumnValue.startsWith(CryptoService.ENCRYPTION_PREFIX));
        assertNotEquals(rawAttribute, dbColumnValue);

        // Convert to entity attribute (Read)
        String entityValue = aesGcmConverter.convertToEntityAttribute(dbColumnValue);
        assertEquals(rawAttribute, entityValue, "Attribute converter must transparently restore plaintext on read");

        assertNull(aesGcmConverter.convertToDatabaseColumn(null));
        assertNull(aesGcmConverter.convertToEntityAttribute(null));
    }

    // =========================================================================================
    // Category 4: Trusted Proxy Security & Anti-Spoofing Verification
    // =========================================================================================

    @Test
    @DisplayName("Proxy 16: Untrusted Client: Directly connecting untrusted client has spoofed X-Forwarded-For discarded")
    public void test16_ProxyAudit_UntrustedClient_IgnoresSpoofedXForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.195"); // Directly-connecting external client
        request.addHeader("X-Forwarded-For", "10.0.0.1, 192.168.1.1"); // Spoofed private IP headers

        String resolvedIp = clientIpResolver.resolveClientIp(request);
        assertEquals("203.0.113.195", resolvedIp, "Must ignore X-Forwarded-For from untrusted remote client");
    }

    @Test
    @DisplayName("Proxy 17: Trusted Reverse Proxy: Accurately propagates true client IP from X-Forwarded-For")
    public void test17_ProxyAudit_TrustedReverseProxy_ExtractsTrueClientIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); // Trusted loopback proxy
        request.addHeader("X-Forwarded-For", "198.51.100.75");

        String resolvedIp = clientIpResolver.resolveClientIp(request);
        assertEquals("198.51.100.75", resolvedIp, "Must extract client IP from trusted reverse proxy");
    }

    @Test
    @DisplayName("Proxy 18: Chained Multi-Hop Proxies: Extracts earliest originating client IP")
    public void test18_ProxyAudit_TrustedProxy_ChainedHops_PicksEarliestClientIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); // Trusted proxy
        request.addHeader("X-Forwarded-For", "198.51.100.75, 10.0.1.20, 10.0.1.10");

        String resolvedIp = clientIpResolver.resolveClientIp(request);
        assertEquals("198.51.100.75", resolvedIp, "Must extract the originating client IP from chained proxy hops");
    }

    @Test
    @DisplayName("Proxy 19: CIDR Subnet Matching: Accurately matches trusted proxies configured as CIDR ranges")
    public void test19_ProxyAudit_CidrSubnetMatching_SupportsControlledSubnets() {
        // Configure trusted proxies with specific CIDR notation (e.g. 10.0.1.0/24)
        ClientIpResolver subnetResolver = new ClientIpResolver("10.0.1.0/24,192.168.50.0/24");

        // Client within 10.0.1.0/24 is trusted
        assertTrue(subnetResolver.isTrustedProxy("10.0.1.15"));
        assertTrue(subnetResolver.isTrustedProxy("10.0.1.254"));

        // Client outside 10.0.1.0/24 is NOT trusted
        assertFalse(subnetResolver.isTrustedProxy("10.0.2.15"));
        assertFalse(subnetResolver.isTrustedProxy("203.0.113.5"));

        // When proxy within CIDR forwards traffic, X-Forwarded-For is trusted
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.1.15");
        req.addHeader("X-Forwarded-For", "203.0.113.88");
        assertEquals("203.0.113.88", subnetResolver.resolveClientIp(req));

        // When proxy outside CIDR forwards traffic, X-Forwarded-For is IGNORED
        MockHttpServletRequest untrustedReq = new MockHttpServletRequest();
        untrustedReq.setRemoteAddr("10.0.2.15");
        untrustedReq.addHeader("X-Forwarded-For", "203.0.113.88");
        assertEquals("10.0.2.15", subnetResolver.resolveClientIp(untrustedReq));
    }

    @Test
    @DisplayName("Proxy 20: Header Injection & Malformed Input: Malformed IP headers safely fall back to proxy address")
    public void test20_ProxyAudit_HeaderInjection_MalformedPayloadsFallBackSafely() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); // Trusted proxy
        request.addHeader("X-Forwarded-For", "<script>alert('xss')</script>, 999.999.999.999, sql' OR 1=1--");

        String resolvedIp = clientIpResolver.resolveClientIp(request);
        assertEquals("127.0.0.1", resolvedIp, "Malformed or injected IP headers must safely fall back to proxy remoteAddr");
    }

    @Test
    @DisplayName("Proxy 21: Configuration Validation: Startup fails immediately when trusted proxy CIDR format is invalid")
    public void test21_ProxyAudit_StartupFailsOnInvalidCidrConfiguration() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
                new ClientIpResolver("127.0.0.1,not_a_valid_ip_or_cidr_mask"));
        assertTrue(thrown.getMessage().contains("Invalid trusted proxy address or CIDR notation"));
    }

    @Test
    @DisplayName("Proxy 22: Threat Integration: Attacker cannot evade brute force detection by spoofing X-Forwarded-For")
    public void test22_ProxyAudit_ThreatDetection_PreventsSpoofedIpFromBypassingLockout() {
        String attackerSocketIp = "203.0.113.80"; // Directly connecting untrusted attacker

        // Attacker attempts 5 logins, spoofing a different X-Forwarded-For IP on each request
        for (int i = 1; i <= 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            req.setRemoteAddr(attackerSocketIp);
            req.addHeader("X-Forwarded-For", "198.51.100." + i); // Spoofed IP header

            ServletRequestAttributes attributes = new ServletRequestAttributes(req);
            RequestContextHolder.setRequestAttributes(attributes);

            securityEventManagementService.recordSecurityEvent(
                    SecurityEventType.FAILED_LOGIN,
                    null, // Let service resolve IP via ClientIpResolver from request context
                    "victim-" + runId + "@company.com",
                    "/api/auth/login",
                    "Attempt " + i,
                    "AttackerBot/1.0");
        }

        // All 5 attempts must have been attributed to 203.0.113.80, NOT the spoofed IPs
        long failedCountForSocketIp = securityEventRepository.countByEventTypeAndIpAddressAndCreatedAtAfter(
                SecurityEventType.FAILED_LOGIN, attackerSocketIp, java.time.LocalDateTime.now().minusMinutes(15));
        assertEquals(5, failedCountForSocketIp, "All failed logins must be attributed to true socket IP");

        // Verify automated brute force alert was triggered against the real attacker IP
        List<SecurityEvent> alerts = securityEventRepository.findByEventType(SecurityEventType.BRUTE_FORCE_ATTEMPT);
        boolean alertForAttacker = alerts.stream().anyMatch(a -> attackerSocketIp.equals(a.getIpAddress()));
        assertTrue(alertForAttacker, "Brute force alert must be generated for the real attacker IP despite header spoofing");
    }

    // =========================================================================================
    // Category 5: REST API Encryption & Sensitive Data Endpoint Authorization
    // =========================================================================================

    @Test
    @DisplayName("API 23: Sensitive Data API: Admin can encrypt and decrypt data via REST endpoints (200 OK)")
    public void test23_RestApi_EncryptDecrypt_AdminAuthorized() throws Exception {
        CryptoRequestDTO encryptReq = new CryptoRequestDTO();
        encryptReq.setData("Salary-Package-$185,000");

        // 1. POST /api/sensitive-data/encrypt
        String encryptResponse = mockMvc.perform(post("/api/sensitive-data/encrypt")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(encryptReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encrypted").value(true))
                .andExpect(jsonPath("$.result", startsWith("ENC:")))
                .andReturn().getResponse().getContentAsString();

        String ciphertext = objectMapper.readTree(encryptResponse).get("result").asText();

        // 2. POST /api/sensitive-data/decrypt
        CryptoRequestDTO decryptReq = new CryptoRequestDTO();
        decryptReq.setData(ciphertext);

        mockMvc.perform(post("/api/sensitive-data/decrypt")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decryptReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encrypted").value(false))
                .andExpect(jsonPath("$.result").value("Salary-Package-$185,000"));
    }

    @Test
    @DisplayName("API 24: Sensitive Data API: Decrypting tampered ciphertext returns 400 Bad Request")
    public void test24_RestApi_DecryptTamperedData_Returns400BadRequest() throws Exception {
        String validCiphertext = cryptoService.encrypt("TopSecretPersonalRecord");
        String tamperedCiphertext = validCiphertext.substring(0, validCiphertext.length() - 4) + "AAAA";

        CryptoRequestDTO tamperedReq = new CryptoRequestDTO();
        tamperedReq.setData(tamperedCiphertext);

        mockMvc.perform(post("/api/sensitive-data/decrypt")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tamperedReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Decryption Failed"))
                .andExpect(jsonPath("$.message", containsString("Failed to decrypt or authenticate sensitive data")));
    }

    @Test
    @DisplayName("API 25: Sensitive Data API: Employee and Manager are 403 Forbidden from encrypt/decrypt endpoints")
    public void test25_RestApi_EncryptDecrypt_EmployeeAndManagerForbidden() throws Exception {
        CryptoRequestDTO dto = new CryptoRequestDTO();
        dto.setData("AttemptedData");

        // Employee Forbidden
        mockMvc.perform(post("/api/sensitive-data/encrypt")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/sensitive-data/decrypt")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());

        // Manager Forbidden
        mockMvc.perform(post("/api/sensitive-data/encrypt")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/sensitive-data/decrypt")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());

        // Unauthenticated Unauthorized
        mockMvc.perform(post("/api/sensitive-data/encrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }
}
