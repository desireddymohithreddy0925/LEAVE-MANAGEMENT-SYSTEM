package com.leave_management_system.leave_management_system.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.SecurityEventResponseDTO;
import com.leave_management_system.leave_management_system.dto.SecurityEventSummaryDTO;
import com.leave_management_system.leave_management_system.dto.UpdateSecurityEventStatusDTO;
import com.leave_management_system.leave_management_system.entity.AuditLog;
import com.leave_management_system.leave_management_system.entity.AuthorizationAuditLog;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.SecurityEvent;
import com.leave_management_system.leave_management_system.entity.SecurityEventSeverity;
import com.leave_management_system.leave_management_system.entity.SecurityEventStatus;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.repository.AuditLogRepository;
import com.leave_management_system.leave_management_system.repository.AuthorizationAuditLogRepository;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.RefreshTokenRepository;
import com.leave_management_system.leave_management_system.repository.RoleRepository;
import com.leave_management_system.leave_management_system.repository.SecurityEventRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import com.leave_management_system.leave_management_system.service.AuditService;
import com.leave_management_system.leave_management_system.service.AuthorizationAuditService;
import com.leave_management_system.leave_management_system.service.SecurityEventManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class ComprehensiveSecurityAuditIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private SecurityEventRepository securityEventRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuthorizationAuditLogRepository authorizationAuditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private SecurityEventManagementService securityEventManagementService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuthorizationAuditService authorizationAuditService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private String runId;

    private User adminUser;
    private String adminToken;

    private User securityOfficerUser;
    private String securityOfficerToken;

    private User regularUser;
    private String regularToken;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        cleanup();

        runId = UUID.randomUUID().toString().substring(0, 8);

        // 1. Roles
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        Role officerRole = roleRepository.findByName("SECURITY_OFFICER").orElseGet(() -> roleRepository.save(new Role("SECURITY_OFFICER")));
        Role empRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));

        // 2. Admin User
        adminUser = new User();
        adminUser.setEmail("audit-admin-" + runId + "@enterprise.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);

        UserDetails adminDetails = new org.springframework.security.core.userdetails.User(
                adminUser.getEmail(), adminUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ADMIN_VIEW"), new SimpleGrantedAuthority("AUDIT_LOG_VIEW"))
        );
        adminToken = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities()));

        // 3. Security Officer User
        securityOfficerUser = new User();
        securityOfficerUser.setEmail("audit-sec-" + runId + "@enterprise.com");
        securityOfficerUser.setPassword(passwordEncoder.encode("OfficerPass123!"));
        securityOfficerUser.setActive(true);
        securityOfficerUser.getRoles().add(officerRole);
        securityOfficerUser = userRepository.save(securityOfficerUser);

        UserDetails officerDetails = new org.springframework.security.core.userdetails.User(
                securityOfficerUser.getEmail(), securityOfficerUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_SECURITY_OFFICER"), new SimpleGrantedAuthority("SECURITY_EVENT_MANAGE"))
        );
        securityOfficerToken = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(officerDetails, null, officerDetails.getAuthorities()));

        // 4. Regular Employee User
        regularUser = new User();
        regularUser.setEmail("audit-emp-" + runId + "@enterprise.com");
        regularUser.setPassword(passwordEncoder.encode("EmpPass123!"));
        regularUser.setActive(true);
        regularUser.getRoles().add(empRole);
        regularUser = userRepository.save(regularUser);

        UserDetails empDetails = new org.springframework.security.core.userdetails.User(
                regularUser.getEmail(), regularUser.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
        );
        regularToken = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(empDetails, null, empDetails.getAuthorities()));
    }

    @AfterEach
    public void cleanup() {
        securityEventRepository.deleteAll();
        auditLogRepository.deleteAll();
        authorizationAuditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();

        if (regularUser != null && regularUser.getId() != null) {
            employeeRepository.findByUserEmail(regularUser.getEmail()).ifPresent(employeeRepository::delete);
            userRepository.deleteById(regularUser.getId());
            regularUser = null;
        }
        if (securityOfficerUser != null && securityOfficerUser.getId() != null) {
            employeeRepository.findByUserEmail(securityOfficerUser.getEmail()).ifPresent(employeeRepository::delete);
            userRepository.deleteById(securityOfficerUser.getId());
            securityOfficerUser = null;
        }
        if (adminUser != null && adminUser.getId() != null) {
            employeeRepository.findByUserEmail(adminUser.getEmail()).ifPresent(employeeRepository::delete);
            userRepository.deleteById(adminUser.getId());
            adminUser = null;
        }
    }

    // =========================================================================
    // 1. BASELINE SECURITY EVENT RECORDING & THREAT METRICS
    // =========================================================================

    @Test
    @DisplayName("1. Security Events: Baseline threat metrics and severities are assigned correctly")
    void test1_SecurityEventRecording_BaselineThreatMetrics() {
        // Successful login: LOW, 0, RESOLVED
        SecurityEvent loginEvent = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.SUCCESSFUL_LOGIN, "192.168.1.10", regularUser.getEmail(), "/api/auth/login", "Login success", "Chrome");
        assertEquals(SecurityEventSeverity.LOW, loginEvent.getSeverity());
        assertEquals(0, loginEvent.getThreatScore());
        assertEquals(SecurityEventStatus.RESOLVED, loginEvent.getStatus());

        // Failed login: MEDIUM, 40, OPEN
        SecurityEvent failEvent = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.FAILED_LOGIN, "192.168.1.10", regularUser.getEmail(), "/api/auth/login", "Bad credentials", "Firefox");
        assertEquals(SecurityEventSeverity.MEDIUM, failEvent.getSeverity());
        assertEquals(40, failEvent.getThreatScore());
        assertEquals(SecurityEventStatus.OPEN, failEvent.getStatus());

        // Invalid token: HIGH, 60, OPEN
        SecurityEvent tokenEvent = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.INVALID_TOKEN, "192.168.1.20", null, "/api/employees/me", "Signature mismatch", "curl");
        assertEquals(SecurityEventSeverity.HIGH, tokenEvent.getSeverity());
        assertEquals(60, tokenEvent.getThreatScore());
        assertEquals(SecurityEventStatus.OPEN, tokenEvent.getStatus());

        // Account locked: HIGH, 75, OPEN
        SecurityEvent lockEvent = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.ACCOUNT_LOCKED, "192.168.1.30", regularUser.getEmail(), "/api/users/lock", "Account locked", "Safari");
        assertEquals(SecurityEventSeverity.HIGH, lockEvent.getSeverity());
        assertEquals(75, lockEvent.getThreatScore());
        assertEquals(SecurityEventStatus.OPEN, lockEvent.getStatus());

        // Account unlocked: LOW, 10, RESOLVED
        SecurityEvent unlockEvent = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.ACCOUNT_UNLOCKED, "192.168.1.30", regularUser.getEmail(), "/api/users/unlock", "Account unlocked", "Safari");
        assertEquals(SecurityEventSeverity.LOW, unlockEvent.getSeverity());
        assertEquals(10, unlockEvent.getThreatScore());
        assertEquals(SecurityEventStatus.RESOLVED, unlockEvent.getStatus());
    }

    // =========================================================================
    // 2. AUTOMATED THREAT DETECTION RULES & THRESHOLDS
    // =========================================================================

    @Test
    @DisplayName("2. Automated Threat: 5 failed logins against single account triggers CRITICAL Brute Force alert")
    void test2_AutomatedThreatRule_BruteForceDetectionByAccount() {
        String targetEmail = "brute-target-" + runId + "@test.com";

        // Generate 4 failed login events
        for (int i = 1; i <= 4; i++) {
            securityEventManagementService.recordSecurityEvent(
                    SecurityEventType.FAILED_LOGIN, "10.0.0." + i, targetEmail, "/api/auth/login", "Bad pass attempt " + i, "Bot");
        }

        // Verify no brute force alert yet
        List<SecurityEvent> alertsBefore = securityEventRepository.findByEventType(SecurityEventType.BRUTE_FORCE_ATTEMPT);
        assertTrue(alertsBefore.isEmpty(), "Should not trigger brute force alert below threshold of 5");

        // 5th failed attempt breaches threshold
        securityEventManagementService.recordSecurityEvent(
                SecurityEventType.FAILED_LOGIN, "10.0.0.5", targetEmail, "/api/auth/login", "Bad pass attempt 5", "Bot");

        // Verify CRITICAL brute force alert was generated
        List<SecurityEvent> alertsAfter = securityEventRepository.findByEventType(SecurityEventType.BRUTE_FORCE_ATTEMPT);
        assertEquals(1, alertsAfter.size(), "Expected exactly 1 CRITICAL brute force alert");
        SecurityEvent alert = alertsAfter.get(0);
        assertEquals(SecurityEventSeverity.CRITICAL, alert.getSeverity());
        assertEquals(95, alert.getThreatScore());
        assertEquals(targetEmail, alert.getEmail());
        assertTrue(alert.getThreatReason().contains("consecutive failed login attempts"));

        // 6th attempt within cooldown window should NOT spawn a duplicate alert
        securityEventManagementService.recordSecurityEvent(
                SecurityEventType.FAILED_LOGIN, "10.0.0.6", targetEmail, "/api/auth/login", "Bad pass attempt 6", "Bot");
        List<SecurityEvent> alertsCooldown = securityEventRepository.findByEventType(SecurityEventType.BRUTE_FORCE_ATTEMPT);
        assertEquals(1, alertsCooldown.size(), "Cooldown window must prevent duplicate brute force alerts");
    }

    @Test
    @DisplayName("3. Automated Threat: 3 unauthorized access attempts triggers HIGH Repeated Unauthorized Access alert")
    void test3_AutomatedThreatRule_RepeatedUnauthorizedAccess() {
        String sourceIp = "198.51.100.42";

        for (int i = 1; i <= 2; i++) {
            securityEventManagementService.recordSecurityEvent(
                    SecurityEventType.UNAUTHORIZED_ACCESS, sourceIp, null, "/api/admin/restricted", "Denied " + i, "curl");
        }
        assertTrue(securityEventRepository.findByEventType(SecurityEventType.REPEATED_UNAUTHORIZED_ACCESS).isEmpty());

        // 3rd attempt breaches threshold
        securityEventManagementService.recordSecurityEvent(
                SecurityEventType.UNAUTHORIZED_ACCESS, sourceIp, null, "/api/admin/restricted", "Denied 3", "curl");

        List<SecurityEvent> alerts = securityEventRepository.findByEventType(SecurityEventType.REPEATED_UNAUTHORIZED_ACCESS);
        assertEquals(1, alerts.size());
        SecurityEvent alert = alerts.get(0);
        assertEquals(SecurityEventSeverity.HIGH, alert.getSeverity());
        assertEquals(85, alert.getThreatScore());
        assertEquals(sourceIp, alert.getIpAddress());
    }

    @Test
    @DisplayName("4. Automated Threat: 4 token failures triggers HIGH Suspicious Token Activity alert")
    void test4_AutomatedThreatRule_SuspiciousTokenFailures() {
        String sourceIp = "198.51.100.99";

        // Mix of INVALID_TOKEN and EXPIRED_TOKEN
        securityEventManagementService.recordSecurityEvent(SecurityEventType.INVALID_TOKEN, sourceIp, null, "/api/me", "Sig fail", "curl");
        securityEventManagementService.recordSecurityEvent(SecurityEventType.EXPIRED_TOKEN, sourceIp, null, "/api/me", "Expired", "curl");
        securityEventManagementService.recordSecurityEvent(SecurityEventType.INVALID_TOKEN, sourceIp, null, "/api/me", "Garbage", "curl");

        assertTrue(securityEventRepository.findByEventType(SecurityEventType.SUSPICIOUS_TOKEN_ACTIVITY).isEmpty());

        // 4th failure breaches threshold
        securityEventManagementService.recordSecurityEvent(SecurityEventType.INVALID_TOKEN, sourceIp, null, "/api/me", "Sig fail 2", "curl");

        List<SecurityEvent> alerts = securityEventRepository.findByEventType(SecurityEventType.SUSPICIOUS_TOKEN_ACTIVITY);
        assertEquals(1, alerts.size());
        SecurityEvent alert = alerts.get(0);
        assertEquals(SecurityEventSeverity.HIGH, alert.getSeverity());
        assertEquals(80, alert.getThreatScore());
        assertEquals(sourceIp, alert.getIpAddress());
    }

    // =========================================================================
    // 3. SENSITIVE DATA REDACTION & PII PROTECTION
    // =========================================================================

    @Test
    @DisplayName("5. Redaction: Sensitive credentials, tokens, PII, and keys are strictly redacted in audit logs")
    void test5_SensitiveDataMasking_InAuditLogsAndSecurityEvents() {
        // 1. JSON payload with sensitive fields
        String sensitiveJson = "{\"username\":\"admin\",\"password\":\"SuperSecret123!\",\"token\":\"eyJhbGciOiJI...\",\"salary\":95000}";
        String sanitizedJson = securityEventManagementService.sanitizeDetails(sensitiveJson);

        assertFalse(sanitizedJson.contains("SuperSecret123!"), "Password must be redacted");
        assertFalse(sanitizedJson.contains("eyJhbGciOiJI"), "Token must be redacted");
        assertFalse(sanitizedJson.contains("95000"), "Salary must be redacted");
        assertTrue(sanitizedJson.contains("[REDACTED]"));

        // 2. Authorization header & Bearer token
        String authHeader = "Authorization: Bearer my-secret-session-token-value";
        String sanitizedHeader = securityEventManagementService.sanitizeDetails(authHeader);
        assertFalse(sanitizedHeader.contains("my-secret-session-token-value"));
        assertTrue(sanitizedHeader.contains("[REDACTED]"));

        // 3. PEM Private Key
        String pemKey = "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA0...secret...\n-----END RSA PRIVATE KEY-----";
        String sanitizedPem = securityEventManagementService.sanitizeDetails(pemKey);
        assertFalse(sanitizedPem.contains("secret"));
        assertTrue(sanitizedPem.contains("[REDACTED_PRIVATE_KEY]"));

        // 4. Key-Value pairs in AuditService
        String kvInput = "password=PlainTextPassword123, rawRefreshToken=rt-uuid-value-12345, phone=9998887777";
        String sanitizedKv = auditService.sanitizeValue(kvInput);
        assertFalse(sanitizedKv.contains("PlainTextPassword123"));
        assertFalse(sanitizedKv.contains("rt-uuid-value-12345"));
        assertTrue(sanitizedKv.contains("[REDACTED]"));
    }

    // =========================================================================
    // 4. AUTHORIZATION AUDIT TRAILS
    // =========================================================================

    @Test
    @DisplayName("6. Authorization Audit: Records GRANTED for authorized actions and DENIED for violations")
    void test6_AuthorizationAudit_RecordsGrantedAndDeniedDecisions() {
        // Record Granted decision
        AuthorizationAuditLog granted = authorizationAuditService.recordGranted(
                adminUser.getEmail(), adminUser.getId(), "USER_UPDATE", "/api/users/10", "ROLE_ADMIN", "Admin authorized to update user");
        assertEquals("GRANTED", granted.getDecision());
        assertEquals(adminUser.getEmail(), granted.getEmail());
        assertEquals("USER_UPDATE", granted.getAction());

        // Record Denied decision
        AuthorizationAuditLog denied = authorizationAuditService.recordDenied(
                regularUser.getEmail(), regularUser.getId(), "SECURITY_EVENT_VIEW", "/api/security-events", "ROLE_ADMIN", "Insufficient privileges");
        assertEquals("DENIED", denied.getDecision());
        assertEquals(regularUser.getEmail(), denied.getEmail());
        assertEquals("Insufficient privileges", denied.getReason());

        // Verify persistence
        List<AuthorizationAuditLog> allLogs = authorizationAuditLogRepository.findAll();
        assertEquals(2, allLogs.size());
    }

    // =========================================================================
    // 5. RBAC ON AUDIT & SECURITY ENDPOINTS
    // =========================================================================

    @Test
    @DisplayName("7. RBAC: Regular employee is forbidden (403) from accessing audit & security endpoints")
    void test7_RbacProtectionOnAuditAndSecurityEndpoints_EmployeeDenied() throws Exception {
        // Security Events: Employee gets 403
        mockMvc.perform(get("/api/security-events")
                        .header("Authorization", "Bearer " + regularToken))
                .andExpect(status().isForbidden());

        // Security Events Summary: Employee gets 403
        mockMvc.perform(get("/api/security-events/summary")
                        .header("Authorization", "Bearer " + regularToken))
                .andExpect(status().isForbidden());

        // Audit Logs: Employee gets 403
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + regularToken))
                .andExpect(status().isForbidden());

        // Authorization Audits: Employee gets 403
        mockMvc.perform(get("/api/authorization-audits")
                        .header("Authorization", "Bearer " + regularToken))
                .andExpect(status().isForbidden());

        // Unauthenticated access gets 401
        mockMvc.perform(get("/api/security-events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("8. RBAC: Admin and Security Officer have authorized access to audit & incident APIs")
    void test8_RbacProtectionOnAuditAndSecurityEndpoints_PrivilegedGranted() throws Exception {
        // Admin gets 200 on all audit APIs
        mockMvc.perform(get("/api/security-events")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/authorization-audits")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Security Officer with SECURITY_EVENT_MANAGE gets 200 on security events
        mockMvc.perform(get("/api/security-events")
                        .header("Authorization", "Bearer " + securityOfficerToken))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 6. INCIDENT LIFECYCLE WORKFLOW (OPEN -> INVESTIGATING -> RESOLVED)
    // =========================================================================

    @Test
    @DisplayName("9. Incident Workflow: Documented transitions update status and record reviewer metadata")
    void test9_SecurityIncidentLifecycleWorkflow() throws Exception {
        SecurityEvent incident = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.INVALID_TOKEN, "192.168.1.1", null, "/api/me", "Sig mismatch", "curl");
        assertEquals(SecurityEventStatus.OPEN, incident.getStatus());
        assertNull(incident.getResolvedAt());
        assertNull(incident.getResolvedBy());

        // 1. Move to INVESTIGATING
        UpdateSecurityEventStatusDTO invDto = new UpdateSecurityEventStatusDTO();
        invDto.setStatus(SecurityEventStatus.INVESTIGATING);
        invDto.setResolutionNotes("Assigned to SecOps tier 2");

        mockMvc.perform(patch("/api/security-events/" + incident.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVESTIGATING"))
                .andExpect(jsonPath("$.resolutionNotes").value("Assigned to SecOps tier 2"));

        // 2. Move to RESOLVED
        UpdateSecurityEventStatusDTO resolveDto = new UpdateSecurityEventStatusDTO();
        resolveDto.setStatus(SecurityEventStatus.RESOLVED);
        resolveDto.setResolutionNotes("Confirmed test traffic from automated QA pipeline");

        MvcResult resolveResult = mockMvc.perform(patch("/api/security-events/" + incident.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.resolvedByEmail").value(adminUser.getEmail()))
                .andReturn();

        SecurityEventResponseDTO resolvedDTO = objectMapper.readValue(
                resolveResult.getResponse().getContentAsString(), SecurityEventResponseDTO.class);
        assertEquals(SecurityEventStatus.RESOLVED, resolvedDTO.getStatus());
        assertEquals(adminUser.getEmail(), resolvedDTO.getResolvedByEmail());
    }

    // =========================================================================
    // 7. SECURITY POSTURE SUMMARY KPIS
    // =========================================================================

    @Test
    @DisplayName("10. Posture Summary: Aggregates total events, open incidents, high risk, and resolution rate")
    void test10_SecurityPostureSummaryMetrics() throws Exception {
        // Create 2 resolved events
        securityEventManagementService.recordSecurityEvent(SecurityEventType.SUCCESSFUL_LOGIN, "1.1.1.1", "u1@test.com", null, null, null);
        securityEventManagementService.recordSecurityEvent(SecurityEventType.SUCCESSFUL_LOGIN, "1.1.1.2", "u2@test.com", null, null, null);

        // Create 1 open high-risk event
        securityEventManagementService.recordSecurityEvent(SecurityEventType.INVALID_TOKEN, "1.1.1.3", null, null, null, null);

        MvcResult result = mockMvc.perform(get("/api/security-events/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(3))
                .andExpect(jsonPath("$.openCount").value(1))
                .andExpect(jsonPath("$.resolvedCount").value(2))
                .andExpect(jsonPath("$.unresolvedHighRiskCount").value(1))
                .andReturn();

        SecurityEventSummaryDTO summary = objectMapper.readValue(result.getResponse().getContentAsString(), SecurityEventSummaryDTO.class);
        assertTrue(summary.getResolutionRatePercentage() > 60.0);
    }

    // =========================================================================
    // 8. CLIENT IP RESOLUTION & SPOOFING REJECTION IN AUDIT LOGS
    // =========================================================================

    @Test
    @DisplayName("11. Untrusted Proxy: Spoofed X-Forwarded-For header is ignored in audit and event records")
    void test11_ClientIpResolution_UntrustedProxySpoofingPrevented() throws Exception {
        // Direct untrusted request (remoteAddr 198.51.100.25) with spoofed X-Forwarded-For header
        mockMvc.perform(get("/api/security-events")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.25");
                            return request;
                        })
                        .header("X-Forwarded-For", "203.0.113.199") // Attacker spoofed IP
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        // Verify recorded authorization audit log does NOT record 203.0.113.199 as client IP
        List<AuthorizationAuditLog> authLogs = authorizationAuditLogRepository.findAll();
        assertFalse(authLogs.isEmpty());
        AuthorizationAuditLog latestAuthLog = authLogs.get(authLogs.size() - 1);
        assertEquals("198.51.100.25", latestAuthLog.getIpAddress(),
                "Direct untrusted peer IP (198.51.100.25) must be used; spoofed header must be rejected");

        // Verify recorded security event also records genuine direct peer IP
        List<SecurityEvent> secEvents = securityEventRepository.findByEventType(SecurityEventType.UNAUTHORIZED_ACCESS);
        assertFalse(secEvents.isEmpty());
        SecurityEvent latestSecEvent = secEvents.get(secEvents.size() - 1);
        assertEquals("198.51.100.25", latestSecEvent.getIpAddress(),
                "Security event must record genuine remote peer IP, preventing threat evasion");
    }
}
