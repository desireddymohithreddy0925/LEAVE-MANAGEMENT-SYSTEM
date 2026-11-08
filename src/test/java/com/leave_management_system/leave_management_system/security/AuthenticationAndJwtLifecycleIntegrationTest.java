package com.leave_management_system.leave_management_system.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.AuthRequestDTO;
import com.leave_management_system.leave_management_system.dto.AuthResponseDTO;
import com.leave_management_system.leave_management_system.dto.PasswordChangeRequestDTO;
import com.leave_management_system.leave_management_system.dto.RegisterRequestDTO;
import com.leave_management_system.leave_management_system.dto.TokenRefreshRequestDTO;
import com.leave_management_system.leave_management_system.entity.RefreshToken;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.SecurityEvent;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.repository.AuditLogRepository;
import com.leave_management_system.leave_management_system.repository.AuthorizationAuditLogRepository;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.LoginHistoryRepository;
import com.leave_management_system.leave_management_system.repository.RefreshTokenRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
import com.leave_management_system.leave_management_system.repository.SecurityEventRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import com.leave_management_system.leave_management_system.service.RefreshTokenService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class AuthenticationAndJwtLifecycleIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private SecurityEventRepository securityEventRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private AuthorizationAuditLogRepository authorizationAuditLogRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Value("${app.jwt.secret:defaultSecretKeyThatNeedsToBeVeryLongAndSecureEnoughForHS256AlgorithmToBeUsedSafelyAndSecurely}")
    private String jwtSecret;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private String runId;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        runId = UUID.randomUUID().toString().substring(0, 8);

        // Ensure baseline EMPLOYEE role exists
        if (roleRepository.findByName("EMPLOYEE").isEmpty()) {
            roleRepository.save(new Role("EMPLOYEE"));
        }
    }

    @AfterEach
    public void cleanup() {
        refreshTokenRepository.deleteAll();
        securityEventRepository.deleteAll();
        auditLogRepository.deleteAll();
        authorizationAuditLogRepository.deleteAll();
        loginHistoryRepository.deleteAll();

        userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().contains(runId))
                .forEach(u -> {
                    employeeRepository.findByUserEmail(u.getEmail()).ifPresent(employeeRepository::delete);
                    userRepository.delete(u);
                });
    }

    private AuthResponseDTO registerUser(String email, String password) throws Exception {
        RegisterRequestDTO register = new RegisterRequestDTO();
        register.setFirstName("John");
        register.setLastName("Doe");
        register.setEmail(email);
        register.setPassword(password);
        register.setPhone("1234567890");
        register.setEmployeeCode("EMP-" + runId + "-" + System.nanoTime() % 10000);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponseDTO.class);
    }

    private AuthResponseDTO loginUser(String email, String password) throws Exception {
        AuthRequestDTO loginRequest = new AuthRequestDTO();
        loginRequest.setEmail(email);
        loginRequest.setPassword(password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponseDTO.class);
    }

    private Key getSigningKey() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (Exception e) {
            keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String generateExpiredJwt(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis() - 120_000))
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // =========================================================================
    // 1. TOKEN ISSUANCE & CLAIMS
    // =========================================================================

    @Test
    @DisplayName("1. Issue Token: Register and Login produces valid JWT and secure Refresh Token")
    void test1_TokenIssuance_SuccessWithValidClaimsAndStructure() throws Exception {
        String email = "jwt-issue-" + runId + "@test.com";
        AuthResponseDTO auth = registerUser(email, "Password123!");

        assertNotNull(auth.getAccessToken(), "Access token must not be null");
        assertNotNull(auth.getRefreshToken(), "Refresh token must not be null");
        assertNotNull(auth.getUser(), "User payload must not be null");
        assertEquals(email, auth.getUser().getEmail());

        // Validate claims on issued JWT
        jwtUtils.validateJwtToken(auth.getAccessToken());
        String extractedUser = jwtUtils.getUserNameFromJwtToken(auth.getAccessToken());
        assertEquals(email, extractedUser);

        // Verify refresh token is stored hashed and active
        RefreshToken storedRt = refreshTokenService.findByToken(auth.getRefreshToken()).orElse(null);
        assertNotNull(storedRt, "Hashed refresh token must exist in the database");
        assertFalse(storedRt.isRevoked(), "Refresh token must not be revoked on issuance");
        assertTrue(storedRt.getExpiresAt().isAfter(LocalDateTime.now()), "Refresh token expiry must be in the future");
    }

    // =========================================================================
    // 2. TOKEN AUTHENTICATION & ACCESS CONTROL
    // =========================================================================

    @Test
    @DisplayName("2. Authenticate: Valid Bearer JWT successfully accesses protected endpoints")
    void test2_TokenAuthentication_AccessGrantedToSecuredEndpoint() throws Exception {
        String email = "jwt-access-" + runId + "@test.com";
        AuthResponseDTO auth = registerUser(email, "Password123!");

        mockMvc.perform(get("/api/employees/me")
                        .header("Authorization", "Bearer " + auth.getAccessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    @DisplayName("3. Unauthenticated: Missing Authorization header is rejected with 401")
    void test3_TokenAuthentication_MissingHeader_Returns401() throws Exception {
        mockMvc.perform(get("/api/employees/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // =========================================================================
    // 3. CRYPTOGRAPHIC TAMPERING & INTEGRITY REJECTION
    // =========================================================================

    @Test
    @DisplayName("4. Tampering: Signature tampering is rejected with 401 and logs INVALID_TOKEN")
    void test4_TokenTampering_AlteredSignature_RejectedWith401AndInvalidTokenSecurityEvent() throws Exception {
        String email = "jwt-tamper-sig-" + runId + "@test.com";
        AuthResponseDTO auth = registerUser(email, "Password123!");

        String validJwt = auth.getAccessToken();
        // Alter signature part (after the last '.')
        int lastDotIndex = validJwt.lastIndexOf('.');
        String tamperedSignature = validJwt.substring(0, lastDotIndex + 1) + "InVaLiDsIgNaTuReXyZ123456789";

        mockMvc.perform(get("/api/employees/me")
                        .header("Authorization", "Bearer " + tamperedSignature))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Invalid JWT token")));

        // Verify INVALID_TOKEN security event recorded
        List<SecurityEvent> events = securityEventRepository.findByEventType(SecurityEventType.INVALID_TOKEN);
        assertFalse(events.isEmpty(), "INVALID_TOKEN security event must be logged on signature mismatch");
        SecurityEvent event = events.get(events.size() - 1);
        assertEquals(SecurityEventType.INVALID_TOKEN, event.getEventType());
        assertEquals("HIGH", event.getSeverity().name());
    }

    @Test
    @DisplayName("5. Tampering: Payload tampering (privilege escalation) is rejected with 401")
    void test5_TokenTampering_AlteredPayload_RejectedWith401AndInvalidTokenSecurityEvent() throws Exception {
        String email = "jwt-tamper-pay-" + runId + "@test.com";
        AuthResponseDTO auth = registerUser(email, "Password123!");

        String validJwt = auth.getAccessToken();
        String[] parts = validJwt.split("\\.");
        assertEquals(3, parts.length);

        // Replace payload with altered claims (spoofing admin identity)
        String fakePayload = "{\"sub\":\"admin@enterprise.com\",\"iat\":1600000000,\"exp\":2000000000}";
        String encodedFakePayload = Base64.getUrlEncoder().withoutPadding().encodeToString(fakePayload.getBytes(StandardCharsets.UTF_8));
        String tamperedJwt = parts[0] + "." + encodedFakePayload + "." + parts[2];

        mockMvc.perform(get("/api/employees/me")
                        .header("Authorization", "Bearer " + tamperedJwt))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Invalid JWT token")));

        List<SecurityEvent> events = securityEventRepository.findByEventType(SecurityEventType.INVALID_TOKEN);
        assertFalse(events.isEmpty(), "INVALID_TOKEN security event must be logged on payload tampering");
    }

    @Test
    @DisplayName("6. Malformed: Corrupted or non-JWT garbage header is rejected with 401")
    void test6_TokenMalformed_GarbageString_RejectedWith401() throws Exception {
        mockMvc.perform(get("/api/employees/me")
                        .header("Authorization", "Bearer ThisIsNotAValidJwtFormatAtAll!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Invalid JWT token")));

        List<SecurityEvent> events = securityEventRepository.findByEventType(SecurityEventType.INVALID_TOKEN);
        assertFalse(events.isEmpty(), "INVALID_TOKEN security event must be recorded for garbage token string");
    }

    // =========================================================================
    // 4. TOKEN EXPIRATION
    // =========================================================================

    @Test
    @DisplayName("7. Expiration: Expired JWT is rejected with 401 and logs EXPIRED_TOKEN with subject")
    void test7_TokenExpiration_ExpiredJwt_RejectedWith401AndExpiredTokenSecurityEvent() throws Exception {
        String email = "jwt-expired-" + runId + "@test.com";
        registerUser(email, "Password123!");

        String expiredToken = generateExpiredJwt(email);

        mockMvc.perform(get("/api/employees/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("JWT token is expired")));

        List<SecurityEvent> events = securityEventRepository.findByEventType(SecurityEventType.EXPIRED_TOKEN);
        assertFalse(events.isEmpty(), "EXPIRED_TOKEN security event must be recorded");
        SecurityEvent event = events.get(events.size() - 1);
        assertEquals(email, event.getEmail(), "Expired token event should capture the subject email from claims");
        assertEquals("MEDIUM", event.getSeverity().name());
    }

    // =========================================================================
    // 5. REFRESH TOKEN ROTATION (RTR) & REUSE DETECTION
    // =========================================================================

    @Test
    @DisplayName("8. Refresh Token Rotation: Consuming refresh token yields new pair and invalidates old token")
    void test8_RefreshTokenRotation_Success_OldTokenInvalidated() throws Exception {
        String email = "jwt-rtr-" + runId + "@test.com";
        AuthResponseDTO initialAuth = registerUser(email, "Password123!");

        String originalRefreshToken = initialAuth.getRefreshToken();
        String originalAccessToken = initialAuth.getAccessToken();

        TokenRefreshRequestDTO refreshRequest = new TokenRefreshRequestDTO();
        refreshRequest.setRefreshToken(originalRefreshToken);

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponseDTO newAuth = objectMapper.readValue(refreshResult.getResponse().getContentAsString(), AuthResponseDTO.class);

        // 1. New refresh token is rotated
        assertNotNull(newAuth.getRefreshToken());
        assertNotEquals(originalRefreshToken, newAuth.getRefreshToken(), "Refresh token must be rotated to a new secret");

        // 2. New access token is functional
        assertNotNull(newAuth.getAccessToken());
        jwtUtils.validateJwtToken(newAuth.getAccessToken());
        assertEquals(email, jwtUtils.getUserNameFromJwtToken(newAuth.getAccessToken()));
        mockMvc.perform(get("/api/employees/me")
                        .header("Authorization", "Bearer " + newAuth.getAccessToken()))
                .andExpect(status().isOk());

        // 3. Old refresh token is consumed/deleted from database
        assertTrue(refreshTokenService.findByToken(originalRefreshToken).isEmpty(),
                "Old refresh token must be completely removed from the database after rotation");

        // 4. Audit security event logged
        List<SecurityEvent> events = securityEventRepository.findByEventType(SecurityEventType.TOKEN_REFRESHED);
        assertFalse(events.isEmpty(), "TOKEN_REFRESHED security event must be recorded");
    }

    @Test
    @DisplayName("9. Refresh Token Reuse: Replaying already-consumed token is strictly rejected with 401")
    void test9_RefreshTokenReuseDetection_ReplayOldTokenFails() throws Exception {
        String email = "jwt-reuse-" + runId + "@test.com";
        AuthResponseDTO auth = registerUser(email, "Password123!");

        TokenRefreshRequestDTO request = new TokenRefreshRequestDTO();
        request.setRefreshToken(auth.getRefreshToken());

        // First consumption: success
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Replay attempt with same consumed refresh token: MUST fail
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("10. Refresh Token Expiration: Expired refresh token in DB is rejected with 401")
    void test10_RefreshTokenExpiration_ExpiredInDbFails() throws Exception {
        String email = "jwt-rt-exp-" + runId + "@test.com";
        AuthResponseDTO auth = registerUser(email, "Password123!");

        // Artificially expire the token in database
        RefreshToken rt = refreshTokenService.findByToken(auth.getRefreshToken()).orElseThrow();
        rt.setExpiresAt(LocalDateTime.now().minusDays(2));
        refreshTokenRepository.save(rt);

        TokenRefreshRequestDTO req = new TokenRefreshRequestDTO();
        req.setRefreshToken(auth.getRefreshToken());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("11. Refresh Token Revocation: Explicitly revoked token is rejected with 401")
    void test11_RefreshTokenRevocation_RevokedTokenFails() throws Exception {
        String email = "jwt-rt-rev-" + runId + "@test.com";
        AuthResponseDTO auth = registerUser(email, "Password123!");

        // Mark token as revoked in database
        RefreshToken rt = refreshTokenService.findByToken(auth.getRefreshToken()).orElseThrow();
        rt.setRevoked(true);
        refreshTokenRepository.save(rt);

        TokenRefreshRequestDTO req = new TokenRefreshRequestDTO();
        req.setRefreshToken(auth.getRefreshToken());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 6. LOGOUT REVOCATION
    // =========================================================================

    @Test
    @DisplayName("12. Logout: Invalidates refresh token immediately, preventing further refreshes")
    void test12_Logout_RevokesRefreshTokenImmediately() throws Exception {
        String email = "jwt-logout-" + runId + "@test.com";
        AuthResponseDTO auth = registerUser(email, "Password123!");

        TokenRefreshRequestDTO logoutReq = new TokenRefreshRequestDTO();
        logoutReq.setRefreshToken(auth.getRefreshToken());

        // Perform authenticated logout
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + auth.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isOk());

        // Confirm token removed from database
        assertTrue(refreshTokenService.findByToken(auth.getRefreshToken()).isEmpty(),
                "Refresh token must be purged upon user logout");

        // Attempting to refresh with logged-out token must fail
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isUnauthorized());

        // Verify USER_LOGOUT security event recorded
        List<SecurityEvent> events = securityEventRepository.findByEventType(SecurityEventType.USER_LOGOUT);
        assertFalse(events.isEmpty(), "USER_LOGOUT security event must be recorded on logout");
    }

    // =========================================================================
    // 7. PASSWORD CHANGE MULTI-SESSION REVOCATION
    // =========================================================================

    @Test
    @DisplayName("13. Password Change: Revokes all active refresh tokens across all sessions")
    void test13_PasswordChange_RevokesAllUserSessionsAndTokens() throws Exception {
        String email = "jwt-pw-sessions-" + runId + "@test.com";
        String initialPassword = "InitialPassword123!";
        String newPassword = "NewSecurePassword456!";

        // Device 1 session
        AuthResponseDTO session1 = registerUser(email, initialPassword);
        // Device 2 session
        AuthResponseDTO session2 = loginUser(email, initialPassword);

        assertNotEquals(session1.getRefreshToken(), session2.getRefreshToken());
        assertTrue(refreshTokenService.findByToken(session1.getRefreshToken()).isPresent());
        assertTrue(refreshTokenService.findByToken(session2.getRefreshToken()).isPresent());

        // Change password from Session 1
        PasswordChangeRequestDTO changeReq = new PasswordChangeRequestDTO();
        changeReq.setCurrentPassword(initialPassword);
        changeReq.setNewPassword(newPassword);

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + session1.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isOk());

        // All existing refresh tokens for the user MUST be invalidated
        assertTrue(refreshTokenService.findByToken(session1.getRefreshToken()).isEmpty(),
                "Session 1 refresh token must be revoked on password change");
        assertTrue(refreshTokenService.findByToken(session2.getRefreshToken()).isEmpty(),
                "Session 2 refresh token must be revoked on password change");

        // Old credentials cannot login
        AuthRequestDTO oldLoginReq = new AuthRequestDTO();
        oldLoginReq.setEmail(email);
        oldLoginReq.setPassword(initialPassword);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldLoginReq)))
                .andExpect(status().isUnauthorized());

        // New credentials successfully log in and obtain active session
        AuthResponseDTO newLogin = loginUser(email, newPassword);
        assertNotNull(newLogin.getAccessToken());
        assertTrue(refreshTokenService.findByToken(newLogin.getRefreshToken()).isPresent());
    }

    // =========================================================================
    // 8. INACTIVE & DELETED USER TOKEN REJECTION
    // =========================================================================

    @Test
    @DisplayName("14. Deactivated User: Previously issued token is rejected immediately with 401")
    void test14_DeactivatedUser_TokenRejectedImmediately() throws Exception {
        String email = "jwt-deact-" + runId + "@test.com";
        AuthResponseDTO auth = registerUser(email, "Password123!");
        String activeToken = auth.getAccessToken();

        // 1. Verify token works while active
        mockMvc.perform(get("/api/employees/me")
                        .header("Authorization", "Bearer " + activeToken))
                .andExpect(status().isOk());

        // 2. Admin deactivates user
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setActive(false);
        userRepository.save(user);

        // 3. Subsequent request with same valid token is rejected immediately
        mockMvc.perform(get("/api/employees/me")
                        .header("Authorization", "Bearer " + activeToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("inactive")));

        // 4. Verify INACTIVE_USER_TOKEN security event recorded
        List<SecurityEvent> events = securityEventRepository.findByEventType(SecurityEventType.INACTIVE_USER_TOKEN);
        assertFalse(events.isEmpty(), "INACTIVE_USER_TOKEN security event must be recorded");
        SecurityEvent event = events.get(events.size() - 1);
        assertEquals(email, event.getEmail());
        assertEquals("HIGH", event.getSeverity().name());
    }

    @Test
    @DisplayName("15. Deleted User: Token presented for deleted user is rejected with 401 and UNKNOWN_USER_TOKEN")
    void test15_DeletedUser_TokenRejectedWith401() throws Exception {
        String email = "jwt-deleted-" + runId + "@test.com";
        AuthResponseDTO auth = registerUser(email, "Password123!");
        String validToken = auth.getAccessToken();

        // Delete user and associated employee (clean up references first)
        auditLogRepository.deleteAll();
        securityEventRepository.deleteAll();
        loginHistoryRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        employeeRepository.findByUserEmail(email).ifPresent(employeeRepository::delete);
        User user = userRepository.findByEmail(email).orElseThrow();
        userRepository.delete(user);

        // Present token for non-existent user
        mockMvc.perform(get("/api/employees/me")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("User not found")));

        // Verify UNKNOWN_USER_TOKEN security event recorded
        List<SecurityEvent> events = securityEventRepository.findByEventType(SecurityEventType.UNKNOWN_USER_TOKEN);
        assertFalse(events.isEmpty(), "UNKNOWN_USER_TOKEN security event must be recorded");
    }
}

