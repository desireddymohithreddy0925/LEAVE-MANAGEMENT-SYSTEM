package com.leave_management_system.leave_management_system.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveTypeResponseDTO;
import com.leave_management_system.leave_management_system.dto.UpdateSecurityEventStatusDTO;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.service.AuditService;
import com.leave_management_system.leave_management_system.service.AuthorizationAuditService;
import com.leave_management_system.leave_management_system.service.LeaveTypeService;
import com.leave_management_system.leave_management_system.service.SecurityEventManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Enterprise Security Audit: Comprehensive Security Event & Audit Log Verification Suite.
 *
 * This test suite verifies:
 * 1. Audit Log Persistence, Immutability & Append-Only Guarantees (Tamper-Resistance).
 * 2. Transactional Isolation & Independence (Propagation.REQUIRES_NEW) under failure/rollback.
 * 3. Automated Threat Detection Rules (Rule 1: Brute Force, Rule 2: Repeated Unauthorized, Rule 3: Suspicious Token) & Cooldown Mitigation.
 * 4. Multi-Vector Credential, Key, and PII Sanitization across JSON, KV, Headers, and PEM Certificates.
 * 5. Security Incident Lifecycle, State Machine Transitions & Resolution Reviewer Metadata.
 * 6. Security Posture Summary Metrics & Authorization Decision Auditing (Self-Service Isolation).
 * 7. RBAC & Endpoint-Level Authorization (Admin, Read-Only Auditor, Manager, Employee, Anonymous).
 * 8. Client IP Resolution & Anti-Spoofing (Untrusted Proxy Header Rejection vs Trusted Proxy Extraction).
 */
@SpringBootTest
@ActiveProfiles("test")
public class SecurityEventAndAuditLogVerificationIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SecurityEventRepository securityEventRepository;

    @Autowired
    private AuthorizationAuditLogRepository authorizationAuditLogRepository;

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
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private TransactionReviewRepository transactionReviewRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private SecurityEventManagementService securityEventManagementService;

    @Autowired
    private AuthorizationAuditService authorizationAuditService;

    @Autowired
    private LeaveTypeService leaveTypeService;

    @Autowired
    private ClientIpResolver clientIpResolver;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private String runId;

    // Test actors
    private User adminUser;
    private String adminToken;

    private User auditorUser;
    private String auditorToken;

    private User securityOfficerUser;
    private String securityOfficerToken;

    private User managerUser;
    private Employee managerEmployee;
    private String managerToken;

    private User employeeUser;
    private Employee employeeEmployee;
    private String employeeToken;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        cleanup();

        runId = UUID.randomUUID().toString().substring(0, 8);

        // 1. Establish Core Roles
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        Role auditorRole = roleRepository.findByName("AUDITOR").orElseGet(() -> roleRepository.save(new Role("AUDITOR")));
        Role secOfficerRole = roleRepository.findByName("SECURITY_OFFICER").orElseGet(() -> roleRepository.save(new Role("SECURITY_OFFICER")));
        Role managerRole = roleRepository.findByName("MANAGER").orElseGet(() -> roleRepository.save(new Role("MANAGER")));
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));

        // 2. Establish Permissions
        Permission permAuditView = getOrCreatePermission("AUDIT_LOG_VIEW", "View audit and security logs");
        Permission permAdminView = getOrCreatePermission("ADMIN_VIEW", "Administrative read access");
        Permission permSecManage = getOrCreatePermission("SECURITY_EVENT_MANAGE", "Manage and triage security incidents");
        Permission permLeaveTypeManage = getOrCreatePermission("LEAVE_TYPE_MANAGE", "Manage leave types");
        Permission permUserLock = getOrCreatePermission("USER_LOCK_MANAGE", "Lock and unlock user accounts");
        Permission permEmpView = getOrCreatePermission("EMPLOYEE_VIEW", "View employee profiles");
        Permission permLeaveViewOwn = getOrCreatePermission("LEAVE_VIEW_OWN", "View own leave requests");

        // Map Permissions to Roles
        adminRole.getPermissions().addAll(List.of(
                permAuditView, permAdminView, permSecManage, permLeaveTypeManage, permUserLock, permEmpView, permLeaveViewOwn
        ));
        roleRepository.save(adminRole);

        auditorRole.getPermissions().addAll(List.of(
                permAuditView, permAdminView
        ));
        roleRepository.save(auditorRole);

        secOfficerRole.getPermissions().addAll(List.of(
                permAuditView, permSecManage
        ));
        roleRepository.save(secOfficerRole);

        managerRole.getPermissions().addAll(List.of(
                permEmpView
        ));
        roleRepository.save(managerRole);

        employeeRole.getPermissions().addAll(List.of(
                permLeaveViewOwn
        ));
        roleRepository.save(employeeRole);

        // 3. Provision Admin User
        adminUser = new User();
        adminUser.setEmail("audit-admin-" + runId + "@company.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);
        adminToken = generateToken(adminUser, "ROLE_ADMIN", adminRole.getPermissions());

        // 4. Provision Read-Only Auditor User
        auditorUser = new User();
        auditorUser.setEmail("auditor-" + runId + "@company.com");
        auditorUser.setPassword(passwordEncoder.encode("AuditorPass123!"));
        auditorUser.setActive(true);
        auditorUser.getRoles().add(auditorRole);
        auditorUser = userRepository.save(auditorUser);
        auditorToken = generateToken(auditorUser, "ROLE_AUDITOR", auditorRole.getPermissions());

        // 5. Provision Security Officer User
        securityOfficerUser = new User();
        securityOfficerUser.setEmail("secofficer-" + runId + "@company.com");
        securityOfficerUser.setPassword(passwordEncoder.encode("SecOfficerPass123!"));
        securityOfficerUser.setActive(true);
        securityOfficerUser.getRoles().add(secOfficerRole);
        securityOfficerUser = userRepository.save(securityOfficerUser);
        securityOfficerToken = generateToken(securityOfficerUser, "ROLE_SECURITY_OFFICER", secOfficerRole.getPermissions());

        // 6. Provision Manager User & Employee
        managerUser = new User();
        managerUser.setEmail("manager-" + runId + "@company.com");
        managerUser.setPassword(passwordEncoder.encode("MgrPass123!"));
        managerUser.setActive(true);
        managerUser.getRoles().add(managerRole);
        managerUser = userRepository.save(managerUser);

        managerEmployee = new Employee();
        managerEmployee.setFirstName("Manager");
        managerEmployee.setLastName("Security");
        managerEmployee.setEmployeeCode("MGR-SEC-" + runId);
        managerEmployee.setStatus("ACTIVE");
        managerEmployee.setUser(managerUser);
        managerEmployee = employeeRepository.save(managerEmployee);
        managerToken = generateToken(managerUser, "ROLE_MANAGER", managerRole.getPermissions());

        // 7. Provision Standard Employee User & Employee
        employeeUser = new User();
        employeeUser.setEmail("employee-" + runId + "@company.com");
        employeeUser.setPassword(passwordEncoder.encode("EmpPass123!"));
        employeeUser.setActive(true);
        employeeUser.getRoles().add(employeeRole);
        employeeUser = userRepository.save(employeeUser);

        employeeEmployee = new Employee();
        employeeEmployee.setFirstName("Regular");
        employeeEmployee.setLastName("Employee");
        employeeEmployee.setEmployeeCode("EMP-SEC-" + runId);
        employeeEmployee.setStatus("ACTIVE");
        employeeEmployee.setUser(employeeUser);
        employeeEmployee.setManager(managerEmployee);
        employeeEmployee = employeeRepository.save(employeeEmployee);
        employeeToken = generateToken(employeeUser, "ROLE_EMPLOYEE", employeeRole.getPermissions());
    }

    @AfterEach
    public void cleanup() {
        SecurityContextHolder.clearContext();
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

        // Break relations before deleting employee records
        employeeRepository.findAll().forEach(e -> {
            e.setManager(null);
            e.setDepartment(null);
            employeeRepository.save(e);
        });
        departmentRepository.findAll().forEach(d -> {
            d.setManager(null);
            departmentRepository.save(d);
        });

        if (employeeEmployee != null && employeeEmployee.getId() != null) {
            employeeRepository.deleteById(employeeEmployee.getId());
            employeeEmployee = null;
        }
        if (managerEmployee != null && managerEmployee.getId() != null) {
            employeeRepository.deleteById(managerEmployee.getId());
            managerEmployee = null;
        }

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

    // =========================================================================================
    // Category 1: Audit Log Persistence, Immutability & Append-Only Guarantees
    // =========================================================================================

    @Test
    @DisplayName("Audit 1: Business mutations via @AuditAction automatically generate structured immutable audit records")
    public void test1_AuditLog_AutomaticCaptureOnBusinessMutation() throws Exception {
        LeaveTypeRequestDTO dto = new LeaveTypeRequestDTO("Audit-Leave-" + runId, "Audit Verification Leave Type", 15);

        mockMvc.perform(post("/api/leave-types")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Audit-Leave-" + runId));

        // Verify audit trail captured by AOP AuditAspect
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        AuditLog capturedLog = auditLogs.stream()
                .filter(l -> "LEAVE_TYPE_CREATED".equals(l.getAction()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected LEAVE_TYPE_CREATED audit log to be captured"));

        assertEquals("LeaveType", capturedLog.getEntityType());
        assertNotNull(capturedLog.getEntityId());
        assertNotNull(capturedLog.getTimestamp());
        assertNotNull(capturedLog.getNewValue());
        assertTrue(capturedLog.getNewValue().contains("Audit-Leave-" + runId));

        // Verify readable via Audit Log API
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("action", "LEAVE_TYPE_CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].action").value("LEAVE_TYPE_CREATED"))
                .andExpect(jsonPath("$.content[0].entityType").value("LeaveType"));
    }

    @Test
    @DisplayName("Audit 2: Audit logs are strictly append-only; update and deletion REST endpoints do not exist")
    public void test2_AuditLog_StrictAppendOnly_NoMutationOrDeletionEndpoints() throws Exception {
        // Create an audit entry directly
        auditService.logSecurityEvent(SecurityEventType.SUCCESSFUL_LOGIN, "127.0.0.1", adminUser.getEmail(), "Routine login");
        AuditLog savedLog = auditLogRepository.findAll().get(0);
        Long logId = savedLog.getId();

        // 1. Verify PUT /api/audit-logs/{id} is rejected (405 Method Not Allowed)
        mockMvc.perform(put("/api/audit-logs/" + logId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"TAMPERED\"}"))
                .andExpect(status().isMethodNotAllowed());

        // 2. Verify PATCH /api/audit-logs/{id} is rejected (405 Method Not Allowed)
        mockMvc.perform(patch("/api/audit-logs/" + logId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"TAMPERED\"}"))
                .andExpect(status().isMethodNotAllowed());

        // 3. Verify DELETE /api/audit-logs/{id} is rejected (405 Method Not Allowed)
        mockMvc.perform(delete("/api/audit-logs/" + logId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isMethodNotAllowed());

        // 4. Verify POST /api/audit-logs is rejected (405 Method Not Allowed)
        mockMvc.perform(post("/api/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"FORGED\"}"))
                .andExpect(status().isMethodNotAllowed());

        // Confirm database record was not altered
        AuditLog verifiedLog = auditLogRepository.findById(logId).orElseThrow();
        assertEquals(SecurityEventType.SUCCESSFUL_LOGIN, verifiedLog.getAction());
    }

    @Test
    @DisplayName("Audit 3: Transactional independence (Propagation.REQUIRES_NEW) guarantees audit persistence even on rollback")
    public void test3_AuditLog_TransactionalIndependence_RequiresNewPropagation() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        try {
            txTemplate.execute(status -> {
                // Log security event within business transaction
                auditService.logSecurityEvent(
                        SecurityEventType.FAILED_LOGIN,
                        "198.51.100.44",
                        "victim-rollback-" + runId + "@company.com",
                        "Transaction failure simulation for victim-rollback-" + runId);

                // Explicitly trigger a rollback of the outer transaction
                throw new IllegalStateException("Simulated business transaction failure requiring rollback");
            });
        } catch (IllegalStateException expected) {
            // Expected exception
        }

        // Verify that the audit log persisted because it executed in its own independent transaction
        List<AuditLog> logs = auditLogRepository.findAll();
        boolean auditPersisted = logs.stream().anyMatch(l ->
                SecurityEventType.FAILED_LOGIN.equals(l.getAction()) &&
                        "198.51.100.44".equals(l.getIpAddress()) &&
                        l.getNewValue() != null && l.getNewValue().contains("victim-rollback-" + runId));

        assertTrue(auditPersisted, "Audit log must remain committed in database despite outer transaction rollback");

        // Verify that the security event also persisted in its independent transaction
        boolean eventPersisted = securityEventRepository.findAll().stream().anyMatch(e ->
                SecurityEventType.FAILED_LOGIN.equals(e.getEventType()) &&
                        "198.51.100.44".equals(e.getIpAddress()) &&
                        ("victim-rollback-" + runId + "@company.com").equals(e.getEmail()));
        assertTrue(eventPersisted, "Security event must also remain committed in database despite outer transaction rollback");
    }

    @Test
    @DisplayName("Audit 4: Entity-specific audit trail returns chronological history for requested entity")
    public void test4_AuditLog_EntityAuditTrail_ChronologicalQuery() throws Exception {
        Long targetEntityId = 9999L;

        // Log multiple actions for the same entity
        auditService.logAction("BALANCE_INITIALIZED", "LeaveBalance", targetEntityId, null, "{\"available\": 20}");
        auditService.logAction("BALANCE_UPDATED", "LeaveBalance", targetEntityId, "{\"available\": 20}", "{\"available\": 18}");

        mockMvc.perform(get("/api/audit-logs/entity/LeaveBalance/" + targetEntityId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].entityId").value(targetEntityId))
                .andExpect(jsonPath("$.content[0].entityType").value("LeaveBalance"))
                .andExpect(jsonPath("$.content[1].entityId").value(targetEntityId));
    }

    @Test
    @DisplayName("Audit 5: Multi-criteria filtering by userId, action, entityType, and date range with pagination")
    public void test5_AuditLog_MultiCriteriaFilteringAndPagination() throws Exception {
        // Record 3 distinct audit events
        auditService.logAction("ACTION_A", "EntityA", 101L, null, "valA");
        auditService.logAction("ACTION_B", "EntityB", 102L, null, "valB");
        auditService.logAction("ACTION_A", "EntityA", 103L, null, "valA2");

        // Filter by action = ACTION_A
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("action", "ACTION_A")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].action").value("ACTION_A"))
                .andExpect(jsonPath("$.content[1].action").value("ACTION_A"));

        // Pagination test (page size 1)
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("action", "ACTION_A")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    // =========================================================================================
    // Category 2: Automated Threat Detection Rules & Security Event Generation
    // =========================================================================================

    @Test
    @DisplayName("Audit 6: Rule 1a - Account-targeted brute force detection generates CRITICAL alert after 5 failed logins")
    public void test6_ThreatDetection_Rule1a_AccountTargetedBruteForce_GeneratesCriticalAlert() {
        String targetEmail = "target-account-" + runId + "@company.com";
        String attackerIp = "192.0.2.10";

        for (int i = 1; i <= 5; i++) {
            securityEventManagementService.recordSecurityEvent(
                    SecurityEventType.FAILED_LOGIN, attackerIp, targetEmail, "/api/auth/login", "Invalid password attempt " + i, null);
        }

        List<SecurityEvent> alerts = securityEventRepository.findByEventType(SecurityEventType.BRUTE_FORCE_ATTEMPT);
        assertEquals(1, alerts.size(), "Exactly one BRUTE_FORCE_ATTEMPT alert must be generated");

        SecurityEvent alert = alerts.get(0);
        assertEquals(SecurityEventSeverity.CRITICAL, alert.getSeverity());
        assertEquals(SecurityEventStatus.OPEN, alert.getStatus());
        assertEquals(95, alert.getThreatScore());
        assertEquals(targetEmail, alert.getEmail());
        assertTrue(alert.getThreatReason().contains("failed login attempts detected against account"));
    }

    @Test
    @DisplayName("Audit 7: Rule 1b - Single IP credential stuffing across multiple accounts generates CRITICAL alert")
    public void test7_ThreatDetection_Rule1b_SingleIpCredentialStuffing_GeneratesCriticalAlert() {
        String attackerIp = "192.0.2.20";

        for (int i = 1; i <= 5; i++) {
            String victimEmail = "spray-user-" + i + "-" + runId + "@company.com";
            securityEventManagementService.recordSecurityEvent(
                    SecurityEventType.FAILED_LOGIN, attackerIp, victimEmail, "/api/auth/login", "Credential stuffing attempt " + i, null);
        }

        List<SecurityEvent> alerts = securityEventRepository.findByEventType(SecurityEventType.BRUTE_FORCE_ATTEMPT);
        assertFalse(alerts.isEmpty(), "Brute force / credential stuffing alert must be generated for attacking IP");

        SecurityEvent ipAlert = alerts.stream()
                .filter(a -> attackerIp.equals(a.getIpAddress()))
                .findFirst()
                .orElseThrow();

        assertEquals(SecurityEventSeverity.CRITICAL, ipAlert.getSeverity());
        assertEquals(95, ipAlert.getThreatScore());
        assertTrue(ipAlert.getThreatReason().contains("failed login attempts detected from IP"));
    }

    @Test
    @DisplayName("Audit 8: Rule 2 - Repeated unauthorized access attempts (>=3 in 10m) triggers HIGH threat alert")
    public void test8_ThreatDetection_Rule2_RepeatedUnauthorizedAccess_GeneratesHighAlert() {
        String attackerIp = "192.0.2.30";

        for (int i = 1; i <= 3; i++) {
            securityEventManagementService.recordSecurityEvent(
                    SecurityEventType.UNAUTHORIZED_ACCESS, attackerIp, "attacker-" + runId + "@bad.com",
                    "/api/admin/system", "Attempt " + i, null);
        }

        List<SecurityEvent> alerts = securityEventRepository.findByEventType(SecurityEventType.REPEATED_UNAUTHORIZED_ACCESS);
        assertEquals(1, alerts.size(), "Repeated unauthorized access alert must be triggered");

        SecurityEvent alert = alerts.get(0);
        assertEquals(SecurityEventSeverity.HIGH, alert.getSeverity());
        assertEquals(85, alert.getThreatScore());
        assertEquals(SecurityEventStatus.OPEN, alert.getStatus());
        assertTrue(alert.getThreatReason().contains("unauthorized access attempts within 10 minutes"));
    }

    @Test
    @DisplayName("Audit 9: Rule 3 - Suspicious token failures (>=4 in 10m) triggers HIGH threat alert")
    public void test9_ThreatDetection_Rule3_SuspiciousTokenFailures_GeneratesHighAlert() {
        String attackerIp = "192.0.2.40";

        // 2 invalid tokens + 2 expired tokens
        securityEventManagementService.recordSecurityEvent(SecurityEventType.INVALID_TOKEN, attackerIp, null, "/api/leaves", "Bad signature", null);
        securityEventManagementService.recordSecurityEvent(SecurityEventType.INVALID_TOKEN, attackerIp, null, "/api/leaves", "Corrupt payload", null);
        securityEventManagementService.recordSecurityEvent(SecurityEventType.EXPIRED_TOKEN, attackerIp, null, "/api/leaves", "Token expired", null);
        securityEventManagementService.recordSecurityEvent(SecurityEventType.EXPIRED_TOKEN, attackerIp, null, "/api/leaves", "Token expired", null);

        List<SecurityEvent> alerts = securityEventRepository.findByEventType(SecurityEventType.SUSPICIOUS_TOKEN_ACTIVITY);
        assertEquals(1, alerts.size(), "Suspicious token activity alert must be triggered");

        SecurityEvent alert = alerts.get(0);
        assertEquals(SecurityEventSeverity.HIGH, alert.getSeverity());
        assertEquals(80, alert.getThreatScore());
        assertEquals(SecurityEventStatus.OPEN, alert.getStatus());
        assertTrue(alert.getThreatReason().contains("token authentication failures within 10 minutes"));
    }

    @Test
    @DisplayName("Audit 10: Alert cooldown suppresses duplicate flood within 5-minute window")
    public void test10_ThreatDetection_CooldownSuppression_PreventsAlertFlooding() {
        String targetEmail = "cooldown-target-" + runId + "@company.com";
        String attackerIp = "192.0.2.50";

        // First 5 failed logins trigger alert #1
        for (int i = 1; i <= 5; i++) {
            securityEventManagementService.recordSecurityEvent(
                    SecurityEventType.FAILED_LOGIN, attackerIp, targetEmail, "/api/auth/login", "Attempt " + i, null);
        }
        assertEquals(1, securityEventRepository.findByEventType(SecurityEventType.BRUTE_FORCE_ATTEMPT).size());

        // Subsequent 6th and 7th failed logins immediately after must not spam duplicate alerts
        securityEventManagementService.recordSecurityEvent(SecurityEventType.FAILED_LOGIN, attackerIp, targetEmail, "/api/auth/login", "Attempt 6", null);
        securityEventManagementService.recordSecurityEvent(SecurityEventType.FAILED_LOGIN, attackerIp, targetEmail, "/api/auth/login", "Attempt 7", null);

        assertEquals(1, securityEventRepository.findByEventType(SecurityEventType.BRUTE_FORCE_ATTEMPT).size(),
                "Duplicate brute-force alert must be suppressed during the active cooldown window");
    }

    // =========================================================================================
    // Category 3: Multi-Vector Credential and PII Sanitization in Audit Trails
    // =========================================================================================

    @Test
    @DisplayName("Audit 11: Multi-vector secret redaction redacts passwords, tokens, private keys, and PII")
    public void test11_AuditSanitization_MultiVectorSecretRedaction() {
        // 1. JSON secrets redaction
        String sensitiveJson = "{\"password\":\"SuperP@ssword999!\",\"token\":\"eyJhbGciOiJIUzI1NiJ9.test\",\"salary\":125000,\"ssn\":\"999-00-1111\"}";
        String sanitizedJson = auditService.sanitizeValue(sensitiveJson);

        assertFalse(sanitizedJson.contains("SuperP@ssword999!"));
        assertFalse(sanitizedJson.contains("eyJhbGciOiJIUzI1NiJ9.test"));
        assertFalse(sanitizedJson.contains("125000"));
        assertFalse(sanitizedJson.contains("999-00-1111"));
        assertTrue(sanitizedJson.contains("\"password\":\"[REDACTED]\""));
        assertTrue(sanitizedJson.contains("\"token\":\"[REDACTED]\""));
        assertTrue(sanitizedJson.contains("\"salary\":\"[REDACTED]\""));
        assertTrue(sanitizedJson.contains("\"ssn\":\"[REDACTED]\""));

        // 2. Key-Value and Authorization Header redaction
        String kvAndHeader = "password=SuperP@ssword999!, Authorization: Bearer eyJSecretJwtToken123";
        String sanitizedKv = auditService.sanitizeValue(kvAndHeader);

        assertFalse(sanitizedKv.contains("SuperP@ssword999!"));
        assertFalse(sanitizedKv.contains("eyJSecretJwtToken123"));
        assertTrue(sanitizedKv.contains("password=[REDACTED]"));
        assertTrue(sanitizedKv.contains("Bearer [REDACTED]") || sanitizedKv.contains("bearer [REDACTED]"));

        // 3. PEM Private Key redaction
        String pemKey = "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA0vD9\n...secretKeyData...\n-----END RSA PRIVATE KEY-----";
        String sanitizedPem = auditService.sanitizeValue(pemKey);

        assertFalse(sanitizedPem.contains("secretKeyData"));
        assertTrue(sanitizedPem.contains("[REDACTED_PRIVATE_KEY]"));

        // 4. Verify same sanitization in SecurityEventManagementService
        String eventDetails = securityEventManagementService.sanitizeDetails(sensitiveJson);
        assertFalse(eventDetails.contains("SuperP@ssword999!"));
        assertTrue(eventDetails.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("Audit 12: Field length bounding prevents denial of service or truncation errors on oversized inputs")
    public void test12_AuditSanitization_EnforcesFieldLengthSafety() {
        // String of 2500 characters
        String oversized = "A".repeat(2500);
        String sanitized = auditService.sanitizeValue(oversized);

        assertNotNull(sanitized);
        assertTrue(sanitized.length() <= 1000, "Sanitized string must not exceed 1000 characters");
        assertTrue(sanitized.endsWith("..."), "Sanitized string must conclude with ellipsis truncation indicator");

        // Verify SecurityEvent recording handles oversized fields safely
        String longUserAgent = "Mozilla/5.0 ".concat("X".repeat(800));
        String longResource = "/api/v1/leaves/".concat("Y".repeat(400));

        SecurityEvent event = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.UNAUTHORIZED_ACCESS, "127.0.0.1", "test@test.com", longResource, oversized, longUserAgent);

        assertNotNull(event.getId());
        assertTrue(event.getUserAgent().length() <= 500);
        assertTrue(event.getResource().length() <= 255);
        assertTrue(event.getDetails().length() <= 4000);
    }

    // =========================================================================================
    // Category 4: Security Incident Lifecycle, State Machine & Triage Workflow
    // =========================================================================================

    @Test
    @DisplayName("Audit 13: Incident Lifecycle: OPEN -> INVESTIGATING -> RESOLVED records timestamp and reviewer audit")
    public void test13_IncidentLifecycle_OpenToInvestigatingToResolvedWorkflow() throws Exception {
        SecurityEvent incident = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.BRUTE_FORCE_ATTEMPT, "192.0.2.60", "victim@company.com", "/api/auth/login", "Attacking incident", null);
        Long incidentId = incident.getId();
        assertEquals(SecurityEventStatus.OPEN, incident.getStatus());

        // Step 1: Transition to INVESTIGATING
        UpdateSecurityEventStatusDTO invDto = new UpdateSecurityEventStatusDTO(
                SecurityEventStatus.INVESTIGATING, "Assigning to SOC Tier 2 analyst for packet inspection");

        mockMvc.perform(patch("/api/security-events/" + incidentId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVESTIGATING"))
                .andExpect(jsonPath("$.resolutionNotes").value("Assigning to SOC Tier 2 analyst for packet inspection"))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());

        // Step 2: Transition to RESOLVED
        UpdateSecurityEventStatusDTO resDto = new UpdateSecurityEventStatusDTO(
                SecurityEventStatus.RESOLVED, "Originating IP blacklisted on perimeter firewall");

        mockMvc.perform(patch("/api/security-events/" + incidentId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionNotes").value("Originating IP blacklisted on perimeter firewall"))
                .andExpect(jsonPath("$.resolvedAt").exists())
                .andExpect(jsonPath("$.resolvedByEmail").value(adminUser.getEmail()));
    }

    @Test
    @DisplayName("Audit 14: Incident Lifecycle: Reopening a RESOLVED incident cleanly clears resolution metadata")
    public void test14_IncidentLifecycle_ReopeningClearsResolutionMetadata() throws Exception {
        // Start from an incident transitioned to RESOLVED
        SecurityEvent incident = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.BRUTE_FORCE_ATTEMPT, "192.0.2.65", "target@company.com", null, null, null);
        securityEventManagementService.updateStatus(incident.getId(),
                new UpdateSecurityEventStatusDTO(SecurityEventStatus.RESOLVED, "Initial mitigation complete"),
                new UsernamePasswordAuthenticationToken(adminUser.getEmail(), null, Collections.emptyList()));

        SecurityEvent resolvedIncident = securityEventRepository.findById(incident.getId()).orElseThrow();
        assertNotNull(resolvedIncident.getResolvedAt());
        assertNotNull(resolvedIncident.getResolvedBy());

        // Reopen to OPEN
        UpdateSecurityEventStatusDTO reopenDto = new UpdateSecurityEventStatusDTO(
                SecurityEventStatus.OPEN, "Reopening incident due to renewed ingress alerts");

        mockMvc.perform(patch("/api/security-events/" + incident.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reopenDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.resolutionNotes").value("Reopening incident due to renewed ingress alerts"))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist())
                .andExpect(jsonPath("$.resolvedByEmail").doesNotExist());

        SecurityEvent reopenedInDb = securityEventRepository.findById(incident.getId()).orElseThrow();
        assertNull(reopenedInDb.getResolvedAt());
        assertNull(reopenedInDb.getResolvedBy());
    }

    @Test
    @DisplayName("Audit 15: Incident Lifecycle: Rejects invalid or redundant status transitions with 400 Bad Request")
    public void test15_IncidentLifecycle_RejectsInvalidTransitions() throws Exception {
        SecurityEvent incident = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.BRUTE_FORCE_ATTEMPT, "192.0.2.70", "victim@company.com", null, null, null);
        assertEquals(SecurityEventStatus.OPEN, incident.getStatus());

        // Redundant transition (OPEN -> OPEN)
        UpdateSecurityEventStatusDTO redundantDto = new UpdateSecurityEventStatusDTO(SecurityEventStatus.OPEN, "No-op transition");

        mockMvc.perform(patch("/api/security-events/" + incident.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(redundantDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already in status: OPEN")));

        // Null status transition
        mockMvc.perform(patch("/api/security-events/" + incident.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Audit 16: Convenience triage endpoints (investigate, resolve, false-positive) execute seamlessly")
    public void test16_IncidentLifecycle_ConvenienceTriageEndpoints() throws Exception {
        SecurityEvent inc1 = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.BRUTE_FORCE_ATTEMPT, "192.0.2.71", null, null, null, null);
        SecurityEvent inc2 = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.SUSPICIOUS_TOKEN_ACTIVITY, "192.0.2.72", null, null, null, null);

        // 1. POST /{id}/investigate
        mockMvc.perform(post("/api/security-events/" + inc1.getId() + "/investigate")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("notes", "Investigating via SIEM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVESTIGATING"))
                .andExpect(jsonPath("$.resolutionNotes").value("Investigating via SIEM"));

        // 2. POST /{id}/resolve
        mockMvc.perform(post("/api/security-events/" + inc1.getId() + "/resolve")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("notes", "Subnet quarantined"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionNotes").value("Subnet quarantined"));

        // 3. POST /{id}/false-positive
        mockMvc.perform(post("/api/security-events/" + inc2.getId() + "/false-positive")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("notes", "Authorized security regression scan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FALSE_POSITIVE"))
                .andExpect(jsonPath("$.resolutionNotes").value("Authorized security regression scan"));
    }

    // =========================================================================================
    // Category 5: Security Posture Summary & Authorization Audit Metrics
    // =========================================================================================

    @Test
    @DisplayName("Audit 17: Security Posture Summary KPIs correctly compute total, open, resolved, severity & resolution rate")
    public void test17_SecurityPostureSummary_CalculatesAccurateMetricsAndKPIs() throws Exception {
        securityEventRepository.deleteAll();

        // 2 Routine Low Severity Events (Auto-resolved)
        securityEventManagementService.recordSecurityEvent(SecurityEventType.SUCCESSFUL_LOGIN, "10.0.0.1", "u1@company.com", null, null, null);
        securityEventManagementService.recordSecurityEvent(SecurityEventType.PASSWORD_CHANGED, "10.0.0.2", "u2@company.com", null, null, null);

        // 1 Medium Severity Event (OPEN)
        securityEventManagementService.recordSecurityEvent(SecurityEventType.FAILED_LOGIN, "10.0.0.3", "u3@company.com", null, null, null);

        // 1 Critical Severity Event (OPEN)
        securityEventManagementService.recordSecurityEvent(SecurityEventType.BRUTE_FORCE_ATTEMPT, "10.0.0.4", "u4@company.com", null, null, null);

        // Call Summary Endpoint
        mockMvc.perform(get("/api/security-events/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(4))
                .andExpect(jsonPath("$.openCount").value(2))
                .andExpect(jsonPath("$.resolvedCount").value(2))
                .andExpect(jsonPath("$.lowSeverityCount").value(2))
                .andExpect(jsonPath("$.mediumSeverityCount").value(1))
                .andExpect(jsonPath("$.criticalSeverityCount").value(1))
                .andExpect(jsonPath("$.unresolvedHighRiskCount").value(1))
                .andExpect(jsonPath("$.resolutionRatePercentage").value(50.0));
    }

    @Test
    @DisplayName("Audit 18: Authorization decision auditing captures GRANTED and DENIED decisions with IP and reason")
    public void test18_AuthorizationAudit_CapturesGrantedAndDeniedDecisionsWithIp() throws Exception {
        authorizationAuditLogRepository.deleteAll();

        authorizationAuditService.recordGranted(
                adminUser.getEmail(), adminUser.getId(), "USER_LOCK", "User#12", "USER_LOCK_MANAGE", "Admin privilege verified");
        authorizationAuditService.recordDenied(
                employeeUser.getEmail(), employeeUser.getId(), "SALARY_EXPORT", "SensitiveData#All", "ROLE_ADMIN", "Insufficient role");

        mockMvc.perform(get("/api/authorization-audits")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.decision == 'GRANTED')].action").value(hasItem("USER_LOCK")))
                .andExpect(jsonPath("$.content[?(@.decision == 'DENIED')].action").value(hasItem("SALARY_EXPORT")));

        // Summary KPI endpoint
        mockMvc.perform(get("/api/authorization-audits/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDecisions").value(2))
                .andExpect(jsonPath("$.grantedCount").value(1))
                .andExpect(jsonPath("$.deniedCount").value(1))
                .andExpect(jsonPath("$.denialRatePercentage").value(50.0));
    }

    @Test
    @DisplayName("Audit 19: Self-Service Authorization Audit Isolation: /me endpoint returns only caller's decisions")
    public void test19_AuthorizationAudit_SelfServiceIsolation() throws Exception {
        authorizationAuditLogRepository.deleteAll();

        // 1 decision for employeeUser, 1 for adminUser
        authorizationAuditService.recordGranted(employeeUser.getEmail(), employeeUser.getId(), "LEAVE_VIEW_OWN", "LeaveBalance", "LEAVE_VIEW_OWN", "Self view");
        authorizationAuditService.recordGranted(adminUser.getEmail(), adminUser.getId(), "SYSTEM_CONFIG_UPDATE", "Config", "ROLE_ADMIN", "Admin config");

        // Employee requests /api/authorization-audits/me
        mockMvc.perform(get("/api/authorization-audits/me")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value(employeeUser.getEmail()))
                .andExpect(jsonPath("$.content[0].action").value("LEAVE_VIEW_OWN"));
    }

    // =========================================================================================
    // Category 6: RBAC & Endpoint-Level Authorization Enforcement
    // =========================================================================================

    @Test
    @DisplayName("Audit 20: Positive Authorization: Administrator has full access across all audit & security endpoints")
    public void test20_Rbac_AdminHasFullAccess_ViewAndMutate() throws Exception {
        mockMvc.perform(get("/api/audit-logs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/security-events").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/authorization-audits").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/security-events/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Audit 21: Negative Authorization: Read-Only Auditor can view audit logs but CANNOT triage/mutate incidents")
    public void test21_Rbac_ReadOnlyAuditorCanQuery_CannotMutateIncidents() throws Exception {
        SecurityEvent incident = securityEventManagementService.recordSecurityEvent(
                SecurityEventType.BRUTE_FORCE_ATTEMPT, "192.0.2.80", null, null, null, null);

        // Read-only queries succeed (200 OK)
        mockMvc.perform(get("/api/audit-logs").header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/security-events").header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/authorization-audits").header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk());

        // Mutation / triage attempts FAIL with 403 Forbidden
        UpdateSecurityEventStatusDTO dto = new UpdateSecurityEventStatusDTO(SecurityEventStatus.RESOLVED, "Auditor resolving");

        mockMvc.perform(patch("/api/security-events/" + incident.getId() + "/status")
                        .header("Authorization", "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/security-events/" + incident.getId() + "/resolve")
                        .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Audit 22: Negative Authorization: Regular Employee and Manager are 403 Forbidden from audit endpoints")
    public void test22_Rbac_EmployeeAndManagerAreForbidden() throws Exception {
        // Employee Forbidden
        mockMvc.perform(get("/api/audit-logs").header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/security-events").header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/authorization-audits").header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        // Manager Forbidden
        mockMvc.perform(get("/api/audit-logs").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/security-events").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/authorization-audits").header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Audit 23: Negative Authorization: Unauthenticated requests receive 401 Unauthorized")
    public void test23_Rbac_UnauthenticatedRequestsReceive401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/security-events"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/authorization-audits"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/security-events/summary"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================================
    // Category 7: Client IP Resolution and Anti-Spoofing in Audit Trails
    // =========================================================================================

    @Test
    @DisplayName("Audit 24: Untrusted proxy sending spoofed X-Forwarded-For header has header rejected (anti-spoofing)")
    public void test24_ClientIpResolution_UntrustedProxy_PreventsIpSpoofing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.15"); // Untrusted remote address
        request.addHeader("X-Forwarded-For", "198.51.100.99"); // Spoofed header

        String resolvedIp = clientIpResolver.resolveClientIp(request);
        assertEquals("203.0.113.15", resolvedIp, "Must ignore X-Forwarded-For from untrusted remote client");
    }

    @Test
    @DisplayName("Audit 25: Trusted reverse proxy sending valid X-Forwarded-For accurately propagates client IP into audit log")
    public void test25_ClientIpResolution_TrustedProxy_RecordsClientIpAccurately() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1"); // Trusted proxy (loopback)
        request.addHeader("X-Forwarded-For", "198.51.100.42");

        String resolvedIp = clientIpResolver.resolveClientIp(request);
        assertEquals("198.51.100.42", resolvedIp, "Must extract client IP from trusted reverse proxy");

        // Multi-hop chained proxy test
        MockHttpServletRequest chainedRequest = new MockHttpServletRequest();
        chainedRequest.setRemoteAddr("127.0.0.1");
        chainedRequest.addHeader("X-Forwarded-For", "198.51.100.42, 10.0.0.1, 10.0.0.2");

        String chainedResolvedIp = clientIpResolver.resolveClientIp(chainedRequest);
        assertEquals("198.51.100.42", chainedResolvedIp, "Must extract original client hop from trusted chain");

        // Malformed injection header test
        MockHttpServletRequest maliciousRequest = new MockHttpServletRequest();
        maliciousRequest.setRemoteAddr("127.0.0.1");
        maliciousRequest.addHeader("X-Forwarded-For", "malicious<script>alert(1)</script>");

        String fallbackResolvedIp = clientIpResolver.resolveClientIp(maliciousRequest);
        assertEquals("127.0.0.1", fallbackResolvedIp, "Malformed header must safely fall back to proxy address");
    }
}
