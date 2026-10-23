package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leave_management_system.leave_management_system.dto.SecurityEventResponseDTO;
import com.leave_management_system.leave_management_system.dto.UpdateSecurityEventStatusDTO;
import com.leave_management_system.leave_management_system.entity.Permission;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.SecurityEvent;
import com.leave_management_system.leave_management_system.entity.SecurityEventSeverity;
import com.leave_management_system.leave_management_system.entity.SecurityEventStatus;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import com.leave_management_system.leave_management_system.security.SecurityEventType;
import com.leave_management_system.leave_management_system.service.SecurityEventManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
public class SecurityEventManagementIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private SecurityEventRepository securityEventRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private AuthorizationAuditLogRepository authorizationAuditLogRepository;

    @Autowired
    private TransactionReviewRepository transactionReviewRepository;

    @Autowired
    private SecurityEventManagementService securityEventManagementService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private User adminUser;
    private String adminToken;
    private User employeeUser;
    private String employeeToken;
    private User auditorUser;
    private String auditorToken;
    private String uniqueSuffix;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        cleanup();

        uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // Setup Admin
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
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
        Role empRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));
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

        // Setup Auditor (Read-only viewer with AUDIT_LOG_VIEW and ADMIN_VIEW permissions)
        Role auditorRole = roleRepository.findByName("AUDITOR").orElseGet(() -> roleRepository.save(new Role("AUDITOR")));
        Permission auditViewPerm = permissionRepository.findByName("AUDIT_LOG_VIEW").orElseGet(() -> permissionRepository.save(new Permission("AUDIT_LOG_VIEW", "View audit logs")));
        Permission adminViewPerm = permissionRepository.findByName("ADMIN_VIEW").orElseGet(() -> permissionRepository.save(new Permission("ADMIN_VIEW", "View admin dashboard")));
        auditorRole.getPermissions().addAll(List.of(auditViewPerm, adminViewPerm));
        roleRepository.save(auditorRole);

        auditorUser = new User();
        auditorUser.setEmail("sec-auditor-" + uniqueSuffix + "@test.com");
        auditorUser.setPassword(passwordEncoder.encode("AuditorPass123!"));
        auditorUser.setActive(true);
        auditorUser.getRoles().add(auditorRole);
        auditorUser = userRepository.save(auditorUser);

        UserDetails auditorDetails = new org.springframework.security.core.userdetails.User(
                auditorUser.getEmail(),
                auditorUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_AUDITOR"), new SimpleGrantedAuthority("AUDIT_LOG_VIEW"), new SimpleGrantedAuthority("ADMIN_VIEW"))
        );
        auditorToken = jwtUtils.generateJwtToken(
                new UsernamePasswordAuthenticationToken(auditorDetails, null, auditorDetails.getAuthorities())
        );
    }

    @AfterEach
    public void cleanup() {
        transactionReviewRepository.deleteAll();
        securityEventRepository.deleteAll();
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginHistoryRepository.deleteAll();
        authorizationAuditLogRepository.deleteAll();

        if (employeeUser != null && employeeUser.getId() != null) {
            employeeRepository.findByUserEmail(employeeUser.getEmail()).ifPresent(employeeRepository::delete);
            userRepository.deleteById(employeeUser.getId());
            employeeUser = null;
        }
        if (adminUser != null && adminUser.getId() != null) {
            employeeRepository.findByUserEmail(adminUser.getEmail()).ifPresent(employeeRepository::delete);
            userRepository.deleteById(adminUser.getId());
            adminUser = null;
        }
        if (auditorUser != null && auditorUser.getId() != null) {
            employeeRepository.findByUserEmail(auditorUser.getEmail()).ifPresent(employeeRepository::delete);
            userRepository.deleteById(auditorUser.getId());
            auditorUser = null;
        }
    }

    // 1. Routine Login creates LOW severity, RESOLVED event
    @Test
    public void testRecordRoutineLogin_CreatesLowSeverityResolvedEvent() throws Exception {
        SecurityEvent event = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.SUCCESSFUL_LOGIN,
                "192.168.1.10",
                employeeUser.getEmail(),
                "/api/auth/login",
                "User authenticated successfully",
                "Mozilla/5.0"
        );

        assertNotNull(event.getId());
        assertEquals(SecurityEventType.SUCCESSFUL_LOGIN, event.getEventType());
        assertEquals(SecurityEventSeverity.LOW, event.getSeverity());
        assertEquals(SecurityEventStatus.RESOLVED, event.getStatus());
        assertEquals(0, event.getThreatScore());
    }

    // 2. Failed Login creates MEDIUM severity, OPEN event
    @Test
    public void testFailedLogin_CreatesMediumSeverityOpenEvent() throws Exception {
        SecurityEvent event = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.FAILED_LOGIN,
                "10.0.0.5",
                employeeUser.getEmail(),
                "/api/auth/login",
                "Bad credentials provided",
                "Mozilla/5.0"
        );

        assertNotNull(event.getId());
        assertEquals(SecurityEventType.FAILED_LOGIN, event.getEventType());
        assertEquals(SecurityEventSeverity.MEDIUM, event.getSeverity());
        assertEquals(SecurityEventStatus.OPEN, event.getStatus());
        assertEquals(40, event.getThreatScore());
        assertTrue(event.getThreatReason().contains("Authentication failure"));
    }

    // 3. Automated Brute Force Threat Detection: 5 failed logins triggers CRITICAL alert
    @Test
    public void testBruteForceDetection_TriggersCriticalBruteForceAlert_AfterFiveFailedLogins() throws Exception {
        String targetEmail = "victim-" + uniqueSuffix + "@target.com";
        String attackerIp = "203.0.113.42";

        // Generate 5 failed login attempts within window
        for (int i = 1; i <= 5; i++) {
            securityEventManagementService.recordSecurityEvent(
                    SecurityEventType.FAILED_LOGIN,
                    attackerIp,
                    targetEmail,
                    "/api/auth/login",
                    "Attempt " + i + ": invalid password",
                    "Python-requests/2.28"
            );
        }

        List<SecurityEvent> events = securityEventRepository.findAll();
        // 5 FAILED_LOGIN events + 1 BRUTE_FORCE_ATTEMPT alert = 6 events
        assertTrue(events.size() >= 6);

        SecurityEvent bruteForceAlert = events.stream()
                .filter(e -> SecurityEventType.BRUTE_FORCE_ATTEMPT.equals(e.getEventType()))
                .findFirst()
                .orElse(null);

        assertNotNull(bruteForceAlert, "Brute force alert should have been automatically triggered");
        assertEquals(SecurityEventSeverity.CRITICAL, bruteForceAlert.getSeverity());
        assertEquals(SecurityEventStatus.OPEN, bruteForceAlert.getStatus());
        assertEquals(95, bruteForceAlert.getThreatScore());
        assertEquals(targetEmail, bruteForceAlert.getEmail());
        assertEquals(attackerIp, bruteForceAlert.getIpAddress());
        assertTrue(bruteForceAlert.getThreatReason().contains("consecutive failed login attempts"));
    }

    // 4. Repeated Unauthorized Access Detection: 3 violations triggers HIGH threat alert
    @Test
    public void testRepeatedUnauthorizedAccess_TriggersHighThreatAlert_AfterThreeUnauthorizedAttempts() throws Exception {
        String attackerIp = "198.51.100.99";

        for (int i = 1; i <= 3; i++) {
            securityEventManagementService.recordSecurityEvent(
                    SecurityEventType.UNAUTHORIZED_ACCESS,
                    attackerIp,
                    null,
                    "/api/admin/system-config",
                    "Access denied to resource",
                    "Curl/7.88"
            );
        }

        List<SecurityEvent> events = securityEventRepository.findAll();
        SecurityEvent repeatAlert = events.stream()
                .filter(e -> SecurityEventType.REPEATED_UNAUTHORIZED_ACCESS.equals(e.getEventType()))
                .findFirst()
                .orElse(null);

        assertNotNull(repeatAlert, "Repeated unauthorized access alert should be triggered");
        assertEquals(SecurityEventSeverity.HIGH, repeatAlert.getSeverity());
        assertEquals(85, repeatAlert.getThreatScore());
        assertEquals(attackerIp, repeatAlert.getIpAddress());
    }

    // 5. Suspicious Token Activity Detection: 4 invalid token attempts triggers alert
    @Test
    public void testSuspiciousTokenActivity_TriggersHighThreatAlert_AfterRepeatedTokenErrors() throws Exception {
        String attackerIp = "192.0.2.77";

        for (int i = 1; i <= 4; i++) {
            securityEventManagementService.recordSecurityEvent(
                    SecurityEventType.INVALID_TOKEN,
                    attackerIp,
                    null,
                    "/api/leave-balances/me",
                    "Corrupted JWT token signature",
                    "PostmanRuntime/7.32"
            );
        }

        List<SecurityEvent> events = securityEventRepository.findAll();
        SecurityEvent tokenAlert = events.stream()
                .filter(e -> SecurityEventType.SUSPICIOUS_TOKEN_ACTIVITY.equals(e.getEventType()))
                .findFirst()
                .orElse(null);

        assertNotNull(tokenAlert, "Suspicious token activity alert should be triggered");
        assertEquals(SecurityEventSeverity.HIGH, tokenAlert.getSeverity());
        assertEquals(80, tokenAlert.getThreatScore());
    }

    // 6. Multi-criteria Search & Pagination
    @Test
    public void testSearchAndFilterSecurityEvents_ByTypeSeverityAndMinThreatScore() throws Exception {
        securityEventManagementService.recordSecurityEvent(
                SecurityEventType.FAILED_LOGIN, "1.1.1.1", "alpha@test.com", "/api/auth/login", "Wrong pass", null);
        securityEventManagementService.recordSecurityEvent(
                SecurityEventType.UNAUTHORIZED_ACCESS, "2.2.2.2", "beta@test.com", "/api/admin", "Forbidden", null);

        mockMvc.perform(get("/api/security-events")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("eventType", SecurityEventType.UNAUTHORIZED_ACCESS)
                        .param("severity", "HIGH")
                        .param("minThreatScore", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].eventType").value(SecurityEventType.UNAUTHORIZED_ACCESS))
                .andExpect(jsonPath("$.content[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.content[0].threatScore", greaterThanOrEqualTo(60)));
    }

    // 7. Get Security Event By ID (Success and 404)
    @Test
    public void testGetSecurityEventById_SuccessAndNotFound() throws Exception {
        SecurityEvent event = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.PASSWORD_CHANGED, "127.0.0.1", adminUser.getEmail(), "/api/auth/change-password", "Self service", null);

        mockMvc.perform(get("/api/security-events/" + event.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(event.getId()))
                .andExpect(jsonPath("$.eventType").value(SecurityEventType.PASSWORD_CHANGED));

        mockMvc.perform(get("/api/security-events/999999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // 8. Lifecycle Transitions: Status update via PATCH
    @Test
    public void testUpdateSecurityEventStatus_ViaPatch() throws Exception {
        SecurityEvent event = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.FAILED_LOGIN, "10.10.10.10", "suspicious@test.com", "/api/auth/login", "Details", null);

        UpdateSecurityEventStatusDTO updateDTO = new UpdateSecurityEventStatusDTO();
        updateDTO.setStatus(SecurityEventStatus.INVESTIGATING);
        updateDTO.setResolutionNotes("Security analyst reviewing IP logs");

        mockMvc.perform(patch("/api/security-events/" + event.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVESTIGATING"))
                .andExpect(jsonPath("$.resolutionNotes").value("Security analyst reviewing IP logs"));
    }

    // 9. Lifecycle Transitions: Investigate, Resolve, False Positive
    @Test
    public void testConvenienceEndpoints_InvestigateResolveFalsePositive() throws Exception {
        SecurityEvent event = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.INVALID_TOKEN, "10.20.30.40", "analyst@test.com", "/api/leaves", "Bad token", null);

        // Transition to INVESTIGATING
        mockMvc.perform(post("/api/security-events/" + event.getId() + "/investigate")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("notes", "Investigating source subnet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVESTIGATING"))
                .andExpect(jsonPath("$.resolutionNotes").value("Investigating source subnet"));

        // Transition to RESOLVED
        mockMvc.perform(post("/api/security-events/" + event.getId() + "/resolve")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("notes", "Firewall blocked attacker IP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionNotes").value("Firewall blocked attacker IP"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.resolvedByEmail").value(adminUser.getEmail()));

        // Transition to FALSE_POSITIVE
        mockMvc.perform(post("/api/security-events/" + event.getId() + "/false-positive")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("notes", "Developer testing staging tokens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FALSE_POSITIVE"))
                .andExpect(jsonPath("$.resolutionNotes").value("Developer testing staging tokens"));
    }

    // 10. Summary Posture Metrics Calculation
    @Test
    public void testGetSecurityPostureSummary_CalculatesAccurateKPIs() throws Exception {
        securityEventManagementService.recordSecurityEvent(SecurityEventType.SUCCESSFUL_LOGIN, "1.1.1.1", "a@test.com", null, null, null);
        securityEventManagementService.recordSecurityEvent(SecurityEventType.FAILED_LOGIN, "2.2.2.2", "b@test.com", null, null, null);
        securityEventManagementService.recordSecurityEvent(SecurityEventType.UNAUTHORIZED_ACCESS, "3.3.3.3", "c@test.com", null, null, null);

        mockMvc.perform(get("/api/security-events/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.lowSeverityCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.mediumSeverityCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.highSeverityCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.resolutionRatePercentage").isNumber());
    }

    // 11. Credential Sanitization: Sensitive passwords and secrets redacted
    @Test
    public void testCredentialSanitization_RedactsPasswordsAndTokensInDetails() throws Exception {
        String sensitiveDetails = "{\"password\":\"MySecretSuperP@ss123!\",\"token\":\"eyJhbGciOiJIUzI1NiIsIn...\",\"user\":\"hacker\"}";

        SecurityEvent event = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.FAILED_LOGIN,
                "192.168.1.50",
                "attacker@test.com",
                "/api/auth/login",
                sensitiveDetails,
                "Mozilla/5.0"
        );

        assertNotNull(event.getDetails());
        assertFalse(event.getDetails().contains("MySecretSuperP@ss123!"));
        assertFalse(event.getDetails().contains("eyJhbGciOiJIUzI1NiIsIn..."));
        assertTrue(event.getDetails().contains("[REDACTED]"));
    }

    // 12. RBAC Enforcement: Employee forbidden (403), Unauthenticated rejected (401)
    @Test
    public void testRbac_EmployeeForbidden_AndUnauthenticatedUnauthorized() throws Exception {
        // Employee attempting to access security events endpoint
        mockMvc.perform(get("/api/security-events")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        // Employee attempting to access summary
        mockMvc.perform(get("/api/security-events/summary")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        // Unauthenticated access
        mockMvc.perform(get("/api/security-events"))
                .andExpect(status().isUnauthorized());
    }

    // 13. Negative RBAC: Read-only viewers (ADMIN_VIEW / AUDIT_LOG_VIEW) cannot modify incident status
    @Test
    public void testRbac_ReadOnlyViewers_CannotMutateSecurityIncidents_NegativeRbac() throws Exception {
        SecurityEvent event = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.FAILED_LOGIN, "10.0.0.1", "target@test.com", "/api/auth/login", "Failure", null);

        // 1. Read-only viewers CAN search and view incidents
        mockMvc.perform(get("/api/security-events")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/security-events/" + event.getId())
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/security-events/summary")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk());

        // 2. Read-only viewers CANNOT mutate incident via PATCH /status
        UpdateSecurityEventStatusDTO dto = new UpdateSecurityEventStatusDTO(SecurityEventStatus.INVESTIGATING, "Auditor trying to change");
        mockMvc.perform(patch("/api/security-events/" + event.getId() + "/status")
                        .header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());

        // 3. Read-only viewers CANNOT investigate
        mockMvc.perform(post("/api/security-events/" + event.getId() + "/investigate")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());

        // 4. Read-only viewers CANNOT resolve
        mockMvc.perform(post("/api/security-events/" + event.getId() + "/resolve")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());

        // 5. Read-only viewers CANNOT mark false-positive
        mockMvc.perform(post("/api/security-events/" + event.getId() + "/false-positive")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());
    }

    // 14. Brute Force Detection: Single IP attacking multiple distinct accounts (credential stuffing / spray attack)
    @Test
    public void testBruteForceDetection_AcrossMultipleAccounts_FromSingleIp() throws Exception {
        String attackerIp = "203.0.113.88";

        // 5 failed login attempts from same IP against 5 distinct target accounts
        for (int i = 1; i <= 5; i++) {
            String targetAccount = "spray-user-" + i + "-" + uniqueSuffix + "@target.com";
            securityEventManagementService.recordSecurityEvent(
                    SecurityEventType.FAILED_LOGIN,
                    attackerIp,
                    targetAccount,
                    "/api/auth/login",
                    "Credential spray attempt " + i,
                    "Hydra/9.5"
            );
        }

        List<SecurityEvent> events = securityEventRepository.findAll();
        SecurityEvent bruteForceAlert = events.stream()
                .filter(e -> SecurityEventType.BRUTE_FORCE_ATTEMPT.equals(e.getEventType()) && attackerIp.equals(e.getIpAddress()))
                .findFirst()
                .orElse(null);

        assertNotNull(bruteForceAlert, "Brute force alert must be triggered for single IP targeting multiple accounts");
        assertEquals(SecurityEventSeverity.CRITICAL, bruteForceAlert.getSeverity());
        assertEquals(SecurityEventStatus.OPEN, bruteForceAlert.getStatus());
        assertEquals(95, bruteForceAlert.getThreatScore());
        assertEquals(attackerIp, bruteForceAlert.getIpAddress());
        assertTrue(bruteForceAlert.getThreatReason().contains("failed login attempts detected from IP 203.0.113.88 across accounts"));
    }

    // 15. Incident Lifecycle: Allowed status transitions and timestamp/reviewer consistency
    @Test
    public void testIncidentLifecycle_AllowedTransitions_AndTimestampReviewerConsistency() throws Exception {
        SecurityEvent event = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.FAILED_LOGIN, "10.0.0.99", "lifecycle@test.com", "/api/auth/login", "Invalid pass", null);

        Long eventId = event.getId();
        assertEquals(SecurityEventStatus.OPEN, event.getStatus());
        assertNull(event.getResolvedAt());
        assertNull(event.getResolvedBy());

        // Step 1: Transition OPEN -> INVESTIGATING
        mockMvc.perform(post("/api/security-events/" + eventId + "/investigate")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("notes", "Assigned to SOC team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVESTIGATING"))
                .andExpect(jsonPath("$.resolvedAt").isEmpty())
                .andExpect(jsonPath("$.resolvedByEmail").isEmpty());

        // Step 2: Transition INVESTIGATING -> RESOLVED (sets resolvedAt and resolvedBy)
        mockMvc.perform(post("/api/security-events/" + eventId + "/resolve")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("notes", "Threat mitigated via firewall rule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.resolvedByEmail").value(adminUser.getEmail()));

        // Step 3: Reopen RESOLVED -> INVESTIGATING (clears resolvedAt and resolvedBy to null)
        UpdateSecurityEventStatusDTO reopenDTO = new UpdateSecurityEventStatusDTO(SecurityEventStatus.INVESTIGATING, "Reopened after secondary alarm");
        mockMvc.perform(patch("/api/security-events/" + eventId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reopenDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVESTIGATING"))
                .andExpect(jsonPath("$.resolvedAt").isEmpty())
                .andExpect(jsonPath("$.resolvedByEmail").isEmpty());

        // Step 4: Transition INVESTIGATING -> FALSE_POSITIVE (sets resolvedAt and resolvedBy)
        mockMvc.perform(post("/api/security-events/" + eventId + "/false-positive")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("notes", "Verified as authorized penetration testing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FALSE_POSITIVE"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.resolvedByEmail").value(adminUser.getEmail()));

        // Step 5: Reopen FALSE_POSITIVE -> OPEN (clears resolvedAt and resolvedBy)
        UpdateSecurityEventStatusDTO reopenToOpenDTO = new UpdateSecurityEventStatusDTO(SecurityEventStatus.OPEN, "Reopening incident for review");
        mockMvc.perform(patch("/api/security-events/" + eventId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reopenToOpenDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.resolvedAt").isEmpty())
                .andExpect(jsonPath("$.resolvedByEmail").isEmpty());

        // Step 6: Disallow transition to identical status (e.g. OPEN -> OPEN throws 400 Bad Request)
        UpdateSecurityEventStatusDTO duplicateDTO = new UpdateSecurityEventStatusDTO(SecurityEventStatus.OPEN, "Duplicate");
        mockMvc.perform(patch("/api/security-events/" + eventId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateDTO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already in status: OPEN")));
    }

    // 16. Comprehensive Sanitization: Authorization headers, nested sensitive fields, PEM keys, and common secrets
    @Test
    public void testSanitization_AuthorizationHeaders_NestedFields_PemKeys_AndSecrets() {
        // Case 1: Authorization Header with Bearer Token
        String authHeaderBearer = "Header Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig in request";
        String sanitized1 = securityEventManagementService.sanitizeDetails(authHeaderBearer);
        assertFalse(sanitized1.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig"));
        assertTrue(sanitized1.contains("Authorization: Bearer [REDACTED]"));

        // Case 2: Authorization Header with Basic Auth
        String authHeaderBasic = "Authorization: Basic dXNlcjpzdXBlcnNlY3JldA== credentials passed";
        String sanitized2 = securityEventManagementService.sanitizeDetails(authHeaderBasic);
        assertFalse(sanitized2.contains("dXNlcjpzdXBlcnNlY3JldA=="));
        assertTrue(sanitized2.contains("Authorization: Basic [REDACTED]"));

        // Case 3: Standalone Bearer token
        String bearerOnly = "Authorization rejected for Bearer abcdef1234567890_token! ";
        String sanitized3 = securityEventManagementService.sanitizeDetails(bearerOnly);
        assertFalse(sanitized3.contains("abcdef1234567890_token"));
        assertTrue(sanitized3.contains("Bearer [REDACTED]"));

        // Case 4: Deeply nested JSON sensitive fields (client_secret, access_token, api_key, pin, cvv)
        String nestedJson = "{\"meta\":{\"request_id\":\"req-101\"},\"auth\":{\"credentials\":{\"client_secret\":\"super-sec-456\",\"access_token\":\"ya29.secret\",\"api_key\":\"AIzaSySecretApiKey\",\"rawRefreshToken\":\"raw-ref-token-xyz\",\"pin\":\"1234\",\"cvv\":\"999\"}}}";
        String sanitized4 = securityEventManagementService.sanitizeDetails(nestedJson);
        assertFalse(sanitized4.contains("super-sec-456"));
        assertFalse(sanitized4.contains("ya29.secret"));
        assertFalse(sanitized4.contains("AIzaSySecretApiKey"));
        assertFalse(sanitized4.contains("raw-ref-token-xyz"));
        assertFalse(sanitized4.contains("1234"));
        assertFalse(sanitized4.contains("999"));
        assertTrue(sanitized4.contains("\"client_secret\":\"[REDACTED]\""));
        assertTrue(sanitized4.contains("\"access_token\":\"[REDACTED]\""));
        assertTrue(sanitized4.contains("\"api_key\":\"[REDACTED]\""));
        assertTrue(sanitized4.contains("\"pin\":\"[REDACTED]\""));
        assertTrue(sanitized4.contains("\"cvv\":\"[REDACTED]\""));

        // Case 5: PEM Private Key block
        String pemKey = "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA0rXyz1234567890abcdef==\n-----END RSA PRIVATE KEY-----";
        String sanitized5 = securityEventManagementService.sanitizeDetails(pemKey);
        assertFalse(sanitized5.contains("MIIEowIBAAKCAQEA0rXyz1234567890abcdef=="));
        assertTrue(sanitized5.contains("[REDACTED_PRIVATE_KEY]"));

        // Case 6: Key-Value pairs with snake_case and camelCase
        String kvPairs = "client_secret=secret123, secret_key=secKey456, auth_token=auth999, status=active";
        String sanitized6 = securityEventManagementService.sanitizeDetails(kvPairs);
        assertFalse(sanitized6.contains("secret123"));
        assertFalse(sanitized6.contains("secKey456"));
        assertFalse(sanitized6.contains("auth999"));
        assertTrue(sanitized6.contains("client_secret=[REDACTED]"));
        assertTrue(sanitized6.contains("secret_key=[REDACTED]"));
        assertTrue(sanitized6.contains("auth_token=[REDACTED]"));
        assertTrue(sanitized6.contains("status=active"));
    }
}
