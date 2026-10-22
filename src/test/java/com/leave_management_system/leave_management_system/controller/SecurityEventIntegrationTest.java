package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.AuthRequestDTO;
import com.leave_management_system.leave_management_system.dto.AuthResponseDTO;
import com.leave_management_system.leave_management_system.dto.PasswordChangeRequestDTO;
import com.leave_management_system.leave_management_system.dto.RegisterRequestDTO;
import com.leave_management_system.leave_management_system.dto.TokenRefreshRequestDTO;
import com.leave_management_system.leave_management_system.entity.AuditLog;
import com.leave_management_system.leave_management_system.entity.RefreshToken;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.repository.AuditLogRepository;
import com.leave_management_system.leave_management_system.repository.AuthorizationAuditLogRepository;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.LoginHistoryRepository;
import com.leave_management_system.leave_management_system.repository.RefreshTokenRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import com.leave_management_system.leave_management_system.security.SecurityEventType;
import com.leave_management_system.leave_management_system.service.AuditService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class SecurityEventIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private AuthorizationAuditLogRepository authorizationAuditLogRepository;

    @Autowired
    private com.leave_management_system.leave_management_system.repository.SecurityEventRepository securityEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AuditService auditService;

    @Value("${app.jwt.secret:defaultSecretKeyThatNeedsToBeVeryLongAndSecureEnoughForHS256AlgorithmToBeUsedSafelyAndSecurely}")
    private String jwtSecret;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private User adminUser;
    private String adminToken;
    private User employeeUser;
    private String employeeToken;
    private String uniqueSuffix;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        securityEventRepository.deleteAll();
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginHistoryRepository.deleteAll();
        authorizationAuditLogRepository.deleteAll();

        uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // Setup Admin
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        adminUser = new User();
        adminUser.setEmail("sec-admin-" + uniqueSuffix + "@test.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);

        UserDetails adminDetails = new org.springframework.security.core.userdetails.User(
                adminUser.getEmail(),
                adminUser.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        adminToken = jwtUtils.generateJwtToken(
                new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities())
        );

        // Setup Employee
        Role empRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        employeeUser = new User();
        employeeUser.setEmail("sec-emp-" + uniqueSuffix + "@test.com");
        employeeUser.setPassword(passwordEncoder.encode("EmpPass123!"));
        employeeUser.setActive(true);
        employeeUser.getRoles().add(empRole);
        employeeUser = userRepository.save(employeeUser);

        UserDetails empDetails = new org.springframework.security.core.userdetails.User(
                employeeUser.getEmail(),
                employeeUser.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
        );
        employeeToken = jwtUtils.generateJwtToken(
                new UsernamePasswordAuthenticationToken(empDetails, null, empDetails.getAuthorities())
        );
    }

    @AfterEach
    public void cleanup() {
        securityEventRepository.deleteAll();
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginHistoryRepository.deleteAll();
        authorizationAuditLogRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && u.getEmail().contains(uniqueSuffix))
                .forEach(u -> {
                    employeeRepository.findByUserEmail(u.getEmail()).ifPresent(employeeRepository::delete);
                    userRepository.delete(u);
                });
    }

    private String generateExpiredJwt(String username) {
        Key key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis() - 100000))
                .setExpiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 1. SUCCESSFUL_LOGIN
    @Test
    void testSuccessfulLogin_GeneratesSecurityEvent() throws Exception {
        AuthRequestDTO loginRequest = new AuthRequestDTO();
        loginRequest.setEmail(adminUser.getEmail());
        loginRequest.setPassword("AdminPass123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());

        boolean hasSuccessLogin = auditLogRepository.findAll().stream()
                .anyMatch(l -> SecurityEventType.SUCCESSFUL_LOGIN.equals(l.getAction())
                        && SecurityEventType.ENTITY_TYPE_SECURITY_EVENT.equals(l.getEntityType())
                        && l.getUser() != null
                        && adminUser.getId().equals(l.getUser().getId()));

        assertTrue(hasSuccessLogin, "Expected SUCCESSFUL_LOGIN security event to be recorded");
    }

    // 2. FAILED_LOGIN & Password Redaction
    @Test
    void testFailedLogin_GeneratesSecurityEvent_AndDoesNotLeakPassword() throws Exception {
        String secretWrongPass = "SuperSecretBadPassword999!";
        AuthRequestDTO loginRequest = new AuthRequestDTO();
        loginRequest.setEmail(adminUser.getEmail());
        loginRequest.setPassword(secretWrongPass);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());

        AuditLog failedLog = auditLogRepository.findAll().stream()
                .filter(l -> SecurityEventType.FAILED_LOGIN.equals(l.getAction())
                        && SecurityEventType.ENTITY_TYPE_SECURITY_EVENT.equals(l.getEntityType()))
                .findFirst()
                .orElse(null);

        assertNotNull(failedLog, "Expected FAILED_LOGIN security event to be recorded");
        assertFalse(String.valueOf(failedLog.getNewValue()).contains(secretWrongPass),
                "Password must NEVER be leaked in audit log value");
        assertFalse(String.valueOf(failedLog.getOldValue()).contains(secretWrongPass),
                "Password must NEVER be leaked in audit log value");
    }

    // 3. USER_LOGOUT
    @Test
    void testUserLogout_GeneratesSecurityEvent() throws Exception {
        // Register and login a fresh user
        String email = "logout-sec-" + uniqueSuffix + "@test.com";
        RegisterRequestDTO register = new RegisterRequestDTO();
        register.setFirstName("Logout");
        register.setLastName("Tester");
        register.setEmail(email);
        register.setPassword("Password123!");
        register.setPhone("9876543210");
        register.setEmployeeCode("LOG-" + uniqueSuffix);

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponseDTO auth = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponseDTO.class);

        TokenRefreshRequestDTO logoutReq = new TokenRefreshRequestDTO();
        logoutReq.setRefreshToken(auth.getRefreshToken());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + auth.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutReq)))
                .andExpect(status().isOk());

        boolean hasLogoutEvent = auditLogRepository.findAll().stream()
                .anyMatch(l -> SecurityEventType.USER_LOGOUT.equals(l.getAction())
                        && SecurityEventType.ENTITY_TYPE_SECURITY_EVENT.equals(l.getEntityType())
                        && l.getUser() != null
                        && auth.getUser().getId().equals(l.getUser().getId()));

        assertTrue(hasLogoutEvent, "Expected USER_LOGOUT security event to be recorded");
    }

    // 4. EXPIRED_TOKEN (Expired JWT)
    @Test
    void testExpiredJwt_GeneratesSecurityEvent() throws Exception {
        String expiredJwt = generateExpiredJwt(employeeUser.getEmail());

        mockMvc.perform(get("/api/departments")
                        .header("Authorization", "Bearer " + expiredJwt))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("JWT token is expired")));

        boolean hasExpiredEvent = auditLogRepository.findAll().stream()
                .anyMatch(l -> SecurityEventType.EXPIRED_TOKEN.equals(l.getAction())
                        && SecurityEventType.ENTITY_TYPE_SECURITY_EVENT.equals(l.getEntityType()));

        assertTrue(hasExpiredEvent, "Expected EXPIRED_TOKEN security event to be recorded");
    }

    // 5. INVALID_TOKEN (Malformed JWT)
    @Test
    void testInvalidJwt_GeneratesSecurityEvent() throws Exception {
        mockMvc.perform(get("/api/departments")
                        .header("Authorization", "Bearer invalid.malformed.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Invalid JWT token")));

        boolean hasInvalidEvent = auditLogRepository.findAll().stream()
                .anyMatch(l -> SecurityEventType.INVALID_TOKEN.equals(l.getAction())
                        && SecurityEventType.ENTITY_TYPE_SECURITY_EVENT.equals(l.getEntityType()));

        assertTrue(hasInvalidEvent, "Expected INVALID_TOKEN security event to be recorded");
    }

    // 6. INVALID_TOKEN (Invalid Refresh Token)
    @Test
    void testInvalidRefreshToken_GeneratesSecurityEvent() throws Exception {
        TokenRefreshRequestDTO request = new TokenRefreshRequestDTO();
        request.setRefreshToken("non-existent-refresh-token-" + uniqueSuffix);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        boolean hasInvalidTokenEvent = auditLogRepository.findAll().stream()
                .anyMatch(l -> SecurityEventType.INVALID_TOKEN.equals(l.getAction())
                        && SecurityEventType.ENTITY_TYPE_SECURITY_EVENT.equals(l.getEntityType()));

        assertTrue(hasInvalidTokenEvent, "Expected INVALID_TOKEN security event for missing refresh token");
    }

    // 7. EXPIRED_TOKEN (Expired Refresh Token)
    @Test
    void testExpiredRefreshToken_GeneratesSecurityEvent() throws Exception {
        String rawToken = UUID.randomUUID().toString();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(employeeUser);
        refreshToken.setExpiresAt(LocalDateTime.now().minusDays(1)); // Expired yesterday
        refreshToken.setTokenHash(hashToken(rawToken));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        TokenRefreshRequestDTO request = new TokenRefreshRequestDTO();
        request.setRefreshToken(rawToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        boolean hasExpiredEvent = auditLogRepository.findAll().stream()
                .anyMatch(l -> SecurityEventType.EXPIRED_TOKEN.equals(l.getAction())
                        && SecurityEventType.ENTITY_TYPE_SECURITY_EVENT.equals(l.getEntityType()));

        assertTrue(hasExpiredEvent, "Expected EXPIRED_TOKEN security event for expired refresh token");
    }

    // 8. UNAUTHORIZED_ACCESS (401 Unauthenticated)
    @Test
    void testUnauthorizedAccess_401Unauthenticated_GeneratesSecurityEvent() throws Exception {
        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isUnauthorized());

        boolean hasUnauthorized = auditLogRepository.findAll().stream()
                .anyMatch(l -> SecurityEventType.UNAUTHORIZED_ACCESS.equals(l.getAction())
                        && SecurityEventType.ENTITY_TYPE_SECURITY_EVENT.equals(l.getEntityType()));

        assertTrue(hasUnauthorized, "Expected UNAUTHORIZED_ACCESS security event on 401 unauthenticated request");
    }

    // 9. UNAUTHORIZED_ACCESS (403 Forbidden)
    @Test
    void testUnauthorizedAccess_403Forbidden_GeneratesSecurityEvent() throws Exception {
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        boolean hasUnauthorized = auditLogRepository.findAll().stream()
                .anyMatch(l -> SecurityEventType.UNAUTHORIZED_ACCESS.equals(l.getAction())
                        && SecurityEventType.ENTITY_TYPE_SECURITY_EVENT.equals(l.getEntityType())
                        && l.getUser() != null
                        && employeeUser.getId().equals(l.getUser().getId()));

        assertTrue(hasUnauthorized, "Expected UNAUTHORIZED_ACCESS security event on 403 forbidden access");
    }

    // 10. PASSWORD_CHANGED & Password Redaction
    @Test
    void testPasswordChanged_GeneratesSecurityEvent_AndDoesNotLeakPassword() throws Exception {
        String currentPass = "EmpPass123!";
        String newPass = "NewSecurePassword456!";

        PasswordChangeRequestDTO changeReq = new PasswordChangeRequestDTO();
        changeReq.setCurrentPassword(currentPass);
        changeReq.setNewPassword(newPass);

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isOk());

        AuditLog changeLog = auditLogRepository.findAll().stream()
                .filter(l -> SecurityEventType.PASSWORD_CHANGED.equals(l.getAction())
                        && SecurityEventType.ENTITY_TYPE_SECURITY_EVENT.equals(l.getEntityType())
                        && l.getUser() != null
                        && employeeUser.getId().equals(l.getUser().getId()))
                .findFirst()
                .orElse(null);

        assertNotNull(changeLog, "Expected PASSWORD_CHANGED security event to be recorded");
        assertFalse(String.valueOf(changeLog.getNewValue()).contains(newPass),
                "New password must NEVER be logged");
        assertFalse(String.valueOf(changeLog.getNewValue()).contains(currentPass),
                "Current password must NEVER be logged");
    }

    // 11. Dedicated Security Events Endpoint: Admin 200 OK & Filtering
    @Test
    void testGetSecurityEvents_AsAdmin_Returns200AndFilters() throws Exception {
        // Pre-create two distinct security events
        auditService.logSecurityEvent(SecurityEventType.FAILED_LOGIN, "10.0.0.1", adminUser.getEmail(), "Test failed login");
        auditService.logSecurityEvent(SecurityEventType.PASSWORD_CHANGED, "10.0.0.2", employeeUser.getEmail(), "Password updated");

        // Request all security events
        mockMvc.perform(get("/api/audit-logs/security-events")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.content[*].entityType", everyItem(is("SECURITY_EVENT"))));

        // Filter by action: FAILED_LOGIN
        mockMvc.perform(get("/api/audit-logs/security-events")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("action", SecurityEventType.FAILED_LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.content[*].action", everyItem(is(SecurityEventType.FAILED_LOGIN))));

        // Filter by userId
        mockMvc.perform(get("/api/audit-logs/security-events")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("userId", adminUser.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.content[*].userEmail", everyItem(is(adminUser.getEmail()))));
    }

    // 12. Dedicated Security Events Endpoint: Employee 403 Forbidden
    @Test
    void testGetSecurityEvents_AsEmployee_Returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/audit-logs/security-events")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    // 13. Dedicated Security Events Endpoint: Unauthenticated 401 Unauthorized
    @Test
    void testGetSecurityEvents_Unauthenticated_Returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/audit-logs/security-events"))
                .andExpect(status().isUnauthorized());
    }
}
