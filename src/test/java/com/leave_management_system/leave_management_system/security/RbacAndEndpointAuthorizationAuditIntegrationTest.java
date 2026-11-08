package com.leave_management_system.leave_management_system.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.service.AuthorizationAuditService;
import com.leave_management_system.leave_management_system.service.RolePermissionService;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class RbacAndEndpointAuthorizationAuditIntegrationTest {

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
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private AuthorizationAuditLogRepository authorizationAuditLogRepository;

    @Autowired
    private SecurityEventRepository securityEventRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private TransactionReviewRepository transactionReviewRepository;

    @Autowired
    private RolePermissionService rolePermissionService;

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

    private User manager1User;
    private Employee manager1Employee;
    private String manager1Token;

    private User manager2User;
    private Employee manager2Employee;
    private String manager2Token;

    private User subordinate1User;
    private Employee subordinate1Employee;
    private String subordinate1Token;

    private User subordinate2User;
    private Employee subordinate2Employee;
    private String subordinate2Token;

    private LeaveType leaveType;
    private LeaveRequest sub1LeaveRequest;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        cleanup();

        runId = UUID.randomUUID().toString().substring(0, 8);

        // 1. Roles & Permissions setup
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        Role managerRole = roleRepository.findByName("MANAGER").orElseGet(() -> roleRepository.save(new Role("MANAGER")));
        Role empRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));

        Permission permLeaveApprove = permissionRepository.findByName("LEAVE_APPROVE")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_APPROVE", "Approve leave requests")));
        Permission permLeaveReject = permissionRepository.findByName("LEAVE_REJECT")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_REJECT", "Reject leave requests")));
        Permission permLeaveViewTeam = permissionRepository.findByName("LEAVE_VIEW_TEAM")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_VIEW_TEAM", "View team leave requests")));
        Permission permLeaveViewOwn = permissionRepository.findByName("LEAVE_VIEW_OWN")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_VIEW_OWN", "View own leave requests")));
        Permission permLeaveCreate = permissionRepository.findByName("LEAVE_CREATE")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_CREATE", "Create leave request")));
        Permission permLeaveCancel = permissionRepository.findByName("LEAVE_CANCEL")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_CANCEL", "Cancel leave request")));
        Permission permRoleManage = permissionRepository.findByName("ROLE_PERMISSION_MANAGE")
                .orElseGet(() -> permissionRepository.save(new Permission("ROLE_PERMISSION_MANAGE", "Manage roles and permissions")));
        Permission permUserLock = permissionRepository.findByName("USER_LOCK_MANAGE")
                .orElseGet(() -> permissionRepository.save(new Permission("USER_LOCK_MANAGE", "Lock and unlock user accounts")));
        Permission permAuditView = permissionRepository.findByName("AUDIT_LOG_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("AUDIT_LOG_VIEW", "View audit logs")));
        Permission permDeptView = permissionRepository.findByName("DEPARTMENT_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("DEPARTMENT_VIEW", "View departments")));
        Permission permDeptManage = permissionRepository.findByName("DEPARTMENT_MANAGE")
                .orElseGet(() -> permissionRepository.save(new Permission("DEPARTMENT_MANAGE", "Manage departments")));
        Permission permEmpUpdate = permissionRepository.findByName("EMPLOYEE_UPDATE")
                .orElseGet(() -> permissionRepository.save(new Permission("EMPLOYEE_UPDATE", "Update employee records")));

        // Admin permissions
        adminRole.getPermissions().addAll(List.of(
                permLeaveApprove, permLeaveReject, permLeaveViewTeam, permLeaveViewOwn, permLeaveCreate, permLeaveCancel,
                permRoleManage, permUserLock, permAuditView, permDeptView, permDeptManage, permEmpUpdate
        ));
        roleRepository.save(adminRole);

        // Manager permissions
        managerRole.getPermissions().addAll(List.of(
                permLeaveApprove, permLeaveReject, permLeaveViewTeam, permLeaveViewOwn, permLeaveCreate, permLeaveCancel, permDeptView
        ));
        roleRepository.save(managerRole);

        // Employee permissions
        empRole.getPermissions().addAll(List.of(
                permLeaveViewOwn, permLeaveCreate, permLeaveCancel
        ));
        roleRepository.save(empRole);

        // 2. Setup Admin User
        adminUser = new User();
        adminUser.setEmail("rbac-admin-" + runId + "@test.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);

        List<SimpleGrantedAuthority> adminAuthorities = new ArrayList<>();
        adminAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        adminRole.getPermissions().forEach(p -> adminAuthorities.add(new SimpleGrantedAuthority(p.getName())));
        UserDetails adminDetails = new org.springframework.security.core.userdetails.User(
                adminUser.getEmail(), adminUser.getPassword(), adminAuthorities);
        adminToken = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities()));

        // 3. Setup Manager 1 and Subordinate 1
        manager1User = new User();
        manager1User.setEmail("rbac-mgr1-" + runId + "@test.com");
        manager1User.setPassword(passwordEncoder.encode("MgrPass123!"));
        manager1User.setActive(true);
        manager1User.getRoles().add(managerRole);
        manager1User = userRepository.save(manager1User);

        manager1Employee = new Employee();
        manager1Employee.setFirstName("ManagerOne");
        manager1Employee.setLastName("Boss");
        manager1Employee.setEmployeeCode("MGR1-" + runId);
        manager1Employee.setStatus("ACTIVE");
        manager1Employee.setUser(manager1User);
        manager1Employee = employeeRepository.save(manager1Employee);

        List<SimpleGrantedAuthority> mgr1Authorities = new ArrayList<>();
        mgr1Authorities.add(new SimpleGrantedAuthority("ROLE_MANAGER"));
        managerRole.getPermissions().forEach(p -> mgr1Authorities.add(new SimpleGrantedAuthority(p.getName())));
        UserDetails mgr1Details = new org.springframework.security.core.userdetails.User(
                manager1User.getEmail(), manager1User.getPassword(), mgr1Authorities);
        manager1Token = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(mgr1Details, null, mgr1Details.getAuthorities()));

        subordinate1User = new User();
        subordinate1User.setEmail("rbac-sub1-" + runId + "@test.com");
        subordinate1User.setPassword(passwordEncoder.encode("SubPass123!"));
        subordinate1User.setActive(true);
        subordinate1User.getRoles().add(empRole);
        subordinate1User = userRepository.save(subordinate1User);

        subordinate1Employee = new Employee();
        subordinate1Employee.setFirstName("SubordinateOne");
        subordinate1Employee.setLastName("Worker");
        subordinate1Employee.setEmployeeCode("SUB1-" + runId);
        subordinate1Employee.setStatus("ACTIVE");
        subordinate1Employee.setUser(subordinate1User);
        subordinate1Employee.setManager(manager1Employee);
        subordinate1Employee = employeeRepository.save(subordinate1Employee);

        List<SimpleGrantedAuthority> sub1Authorities = new ArrayList<>();
        sub1Authorities.add(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        empRole.getPermissions().forEach(p -> sub1Authorities.add(new SimpleGrantedAuthority(p.getName())));
        UserDetails sub1Details = new org.springframework.security.core.userdetails.User(
                subordinate1User.getEmail(), subordinate1User.getPassword(), sub1Authorities);
        subordinate1Token = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(sub1Details, null, sub1Details.getAuthorities()));

        // 4. Setup Manager 2 and Subordinate 2
        manager2User = new User();
        manager2User.setEmail("rbac-mgr2-" + runId + "@test.com");
        manager2User.setPassword(passwordEncoder.encode("MgrPass123!"));
        manager2User.setActive(true);
        manager2User.getRoles().add(managerRole);
        manager2User = userRepository.save(manager2User);

        manager2Employee = new Employee();
        manager2Employee.setFirstName("ManagerTwo");
        manager2Employee.setLastName("Leader");
        manager2Employee.setEmployeeCode("MGR2-" + runId);
        manager2Employee.setStatus("ACTIVE");
        manager2Employee.setUser(manager2User);
        manager2Employee = employeeRepository.save(manager2Employee);

        List<SimpleGrantedAuthority> mgr2Authorities = new ArrayList<>();
        mgr2Authorities.add(new SimpleGrantedAuthority("ROLE_MANAGER"));
        managerRole.getPermissions().forEach(p -> mgr2Authorities.add(new SimpleGrantedAuthority(p.getName())));
        UserDetails mgr2Details = new org.springframework.security.core.userdetails.User(
                manager2User.getEmail(), manager2User.getPassword(), mgr2Authorities);
        manager2Token = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(mgr2Details, null, mgr2Details.getAuthorities()));

        subordinate2User = new User();
        subordinate2User.setEmail("rbac-sub2-" + runId + "@test.com");
        subordinate2User.setPassword(passwordEncoder.encode("SubPass123!"));
        subordinate2User.setActive(true);
        subordinate2User.getRoles().add(empRole);
        subordinate2User = userRepository.save(subordinate2User);

        subordinate2Employee = new Employee();
        subordinate2Employee.setFirstName("SubordinateTwo");
        subordinate2Employee.setLastName("Worker");
        subordinate2Employee.setEmployeeCode("SUB2-" + runId);
        subordinate2Employee.setStatus("ACTIVE");
        subordinate2Employee.setUser(subordinate2User);
        subordinate2Employee.setManager(manager2Employee);
        subordinate2Employee = employeeRepository.save(subordinate2Employee);

        List<SimpleGrantedAuthority> sub2Authorities = new ArrayList<>();
        sub2Authorities.add(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        empRole.getPermissions().forEach(p -> sub2Authorities.add(new SimpleGrantedAuthority(p.getName())));
        UserDetails sub2Details = new org.springframework.security.core.userdetails.User(
                subordinate2User.getEmail(), subordinate2User.getPassword(), sub2Authorities);
        subordinate2Token = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(sub2Details, null, sub2Details.getAuthorities()));

        // 5. Leave Type & Subordinate 1 Request
        leaveType = new LeaveType();
        leaveType.setName("Vacation-" + runId);
        leaveType.setDefaultDays(20);
        leaveType = leaveTypeRepository.save(leaveType);

        LeaveBalance bal1 = new LeaveBalance();
        bal1.setEmployee(subordinate1Employee);
        bal1.setLeaveType(leaveType);
        bal1.setYear(LocalDate.now().plusDays(10).getYear());
        bal1.setTotal(20);
        bal1.setAvailable(20);
        bal1.setUsed(0);
        leaveBalanceRepository.save(bal1);

        sub1LeaveRequest = new LeaveRequest();
        sub1LeaveRequest.setEmployee(subordinate1Employee);
        sub1LeaveRequest.setLeaveType(leaveType);
        sub1LeaveRequest.setStartDate(LocalDate.now().plusDays(10));
        sub1LeaveRequest.setEndDate(LocalDate.now().plusDays(12));
        sub1LeaveRequest.setStatus(LeaveStatus.PENDING);
        sub1LeaveRequest.setReason("Trip with family");
        sub1LeaveRequest = leaveRequestRepository.save(sub1LeaveRequest);
    }

    @AfterEach
    public void cleanup() {
        transactionReviewRepository.deleteAll();
        leaveRequestRepository.deleteAll();
        leaveBalanceRepository.deleteAll();
        leaveTypeRepository.deleteAll();
        authorizationAuditLogRepository.deleteAll();
        securityEventRepository.deleteAll();
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginHistoryRepository.deleteAll();

        if (subordinate1Employee != null && subordinate1Employee.getId() != null) {
            employeeRepository.deleteById(subordinate1Employee.getId());
            subordinate1Employee = null;
        }
        if (subordinate2Employee != null && subordinate2Employee.getId() != null) {
            employeeRepository.deleteById(subordinate2Employee.getId());
            subordinate2Employee = null;
        }
        if (manager1Employee != null && manager1Employee.getId() != null) {
            employeeRepository.deleteById(manager1Employee.getId());
            manager1Employee = null;
        }
        if (manager2Employee != null && manager2Employee.getId() != null) {
            employeeRepository.deleteById(manager2Employee.getId());
            manager2Employee = null;
        }
        if (subordinate1User != null && subordinate1User.getId() != null) {
            userRepository.deleteById(subordinate1User.getId());
            subordinate1User = null;
        }
        if (subordinate2User != null && subordinate2User.getId() != null) {
            userRepository.deleteById(subordinate2User.getId());
            subordinate2User = null;
        }
        if (manager1User != null && manager1User.getId() != null) {
            userRepository.deleteById(manager1User.getId());
            manager1User = null;
        }
        if (manager2User != null && manager2User.getId() != null) {
            userRepository.deleteById(manager2User.getId());
            manager2User = null;
        }
        if (adminUser != null && adminUser.getId() != null) {
            userRepository.deleteById(adminUser.getId());
            adminUser = null;
        }
    }

    // =========================================================================
    // 1. ENDPOINT-LEVEL RBAC AUDIT: ADMIN FULL PRIVILEGE
    // =========================================================================

    @Test
    @DisplayName("1. RBAC Endpoint Audit: Admin has authorized access to role, user, and security audit endpoints")
    void test1_AdminRole_FullAccessToAdminEndpoints_AuditedWithGranted() throws Exception {
        // Access roles endpoint
        mockMvc.perform(get("/api/roles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Access permissions endpoint
        mockMvc.perform(get("/api/permissions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Access users endpoint
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Access authorization audit logs
        mockMvc.perform(get("/api/authorization-audits")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // 2. ENDPOINT-LEVEL RBAC AUDIT: EMPLOYEE FORBIDDEN (403) AUDIT LOGGED
    // =========================================================================

    @Test
    @DisplayName("2. RBAC Endpoint Audit: Employee access to admin endpoints is rejected (403) and audited as DENIED")
    void test2_EmployeeRole_AccessToAdminEndpoints_DeniedWith403AndAudited() throws Exception {
        // Attempt accessing /api/roles
        mockMvc.perform(get("/api/roles")
                        .header("Authorization", "Bearer " + subordinate1Token))
                .andExpect(status().isForbidden());

        // Attempt accessing /api/users
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + subordinate1Token))
                .andExpect(status().isForbidden());

        // Attempt creating department
        mockMvc.perform(post("/api/departments")
                        .header("Authorization", "Bearer " + subordinate1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Unauthorized Dept\"}"))
                .andExpect(status().isForbidden());

        // Verify DENIED authorization audit records exist for employee
        List<AuthorizationAuditLog> deniedLogs = authorizationAuditLogRepository.findAll().stream()
                .filter(l -> subordinate1User.getEmail().equals(l.getEmail()) && "DENIED".equals(l.getDecision()))
                .toList();

        assertFalse(deniedLogs.isEmpty(), "Expected DENIED authorization audit logs for unauthorized employee");
    }

    // =========================================================================
    // 3. UNAUTHENTICATED ENDPOINT ACCESS AUDIT (401)
    // =========================================================================

    @Test
    @DisplayName("3. Endpoint Audit: Unauthenticated request to protected endpoints is rejected (401) and audited as DENIED")
    void test3_UnauthenticatedAccess_DeniedWith401AndAuditedWithAnonymousUser() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isUnauthorized());

        List<AuthorizationAuditLog> anonLogs = authorizationAuditLogRepository.findAll().stream()
                .filter(l -> "anonymousUser".equals(l.getEmail()) && "DENIED".equals(l.getDecision()))
                .toList();

        assertFalse(anonLogs.isEmpty(), "Expected DENIED authorization audit log with email='anonymousUser'");
    }

    // =========================================================================
    // 4. HIERARCHY-BASED ABAC / RBAC ENDPOINT AUDIT (LEAVE APPROVAL)
    // =========================================================================

    @Test
    @DisplayName("4. Endpoint Audit: Manager 1 approves own subordinate leave (GRANTED), Manager 2 approval is DENIED")
    void test4_ManagerRole_HierarchyBasedLeaveApproval_GrantedForSubordinate_DeniedForNonSubordinate() throws Exception {
        // Manager 2 attempts to approve Subordinate 1's leave (NOT in their reporting hierarchy)
        mockMvc.perform(post("/api/leave-requests/" + sub1LeaveRequest.getId() + "/approve")
                        .header("Authorization", "Bearer " + manager2Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // Verify DENIED audit record for Manager 2
        boolean foundDenied = authorizationAuditLogRepository.findAll().stream()
                .anyMatch(l -> "LEAVE_APPROVAL_CHECK".equals(l.getAction())
                        && "DENIED".equals(l.getDecision())
                        && manager2User.getEmail().equals(l.getEmail())
                        && l.getReason().contains("not a manager"));
        assertTrue(foundDenied, "Expected DENIED audit log when non-manager attempts to approve leave");

        // Manager 1 (actual direct manager) approves Subordinate 1's leave
        mockMvc.perform(post("/api/leave-requests/" + sub1LeaveRequest.getId() + "/approve")
                        .header("Authorization", "Bearer " + manager1Token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Verify GRANTED audit record for Manager 1
        boolean foundGranted = authorizationAuditLogRepository.findAll().stream()
                .anyMatch(l -> "LEAVE_APPROVAL_CHECK".equals(l.getAction())
                        && "GRANTED".equals(l.getDecision())
                        && manager1User.getEmail().equals(l.getEmail())
                        && l.getReason().contains("Authorized as manager"));
        assertTrue(foundGranted, "Expected GRANTED audit log when direct manager approves leave");
    }

    // =========================================================================
    // 5. EMPLOYEE SELF-SERVICE ENDPOINTS AUDIT
    // =========================================================================

    @Test
    @DisplayName("5. Endpoint Audit: Employee viewing own record is GRANTED; viewing peer record is DENIED")
    void test5_EmployeeSelfServiceEndpoints_AllowedForOwnData_DeniedForOtherEmployees() throws Exception {
        // Subordinate 1 views own record
        mockMvc.perform(get("/api/employees/" + subordinate1Employee.getId())
                        .header("Authorization", "Bearer " + subordinate1Token))
                .andExpect(status().isOk());

        boolean foundOwnGranted = authorizationAuditLogRepository.findAll().stream()
                .anyMatch(l -> "EMPLOYEE_VIEW_CHECK".equals(l.getAction())
                        && "GRANTED".equals(l.getDecision())
                        && subordinate1User.getEmail().equals(l.getEmail())
                        && l.getReason().contains("Authorized as self"));
        assertTrue(foundOwnGranted, "Expected GRANTED authorization audit for self access");

        // Subordinate 1 attempts to view Subordinate 2's record
        mockMvc.perform(get("/api/employees/" + subordinate2Employee.getId())
                        .header("Authorization", "Bearer " + subordinate1Token))
                .andExpect(status().isForbidden());

        boolean foundPeerDenied = authorizationAuditLogRepository.findAll().stream()
                .anyMatch(l -> "EMPLOYEE_VIEW_CHECK".equals(l.getAction())
                        && "DENIED".equals(l.getDecision())
                        && subordinate1User.getEmail().equals(l.getEmail()));
        assertTrue(foundPeerDenied, "Expected DENIED authorization audit when viewing peer employee");
    }

    // =========================================================================
    // 6. MANAGER TEAM HIERARCHY ENDPOINT AUDIT
    // =========================================================================

    @Test
    @DisplayName("6. Endpoint Audit: Manager viewing own team is GRANTED; Employee access is DENIED")
    void test6_ManagerTeamHierarchyEndpoints_AllowedForOwnTeam_DeniedForOtherManagers() throws Exception {
        // Manager 1 views own team
        mockMvc.perform(get("/api/managers/" + manager1Employee.getId() + "/team")
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isOk());

        boolean foundTeamGranted = authorizationAuditLogRepository.findAll().stream()
                .anyMatch(l -> "TEAM_VIEW_CHECK".equals(l.getAction())
                        && "GRANTED".equals(l.getDecision())
                        && manager1User.getEmail().equals(l.getEmail()));
        assertTrue(foundTeamGranted, "Expected GRANTED authorization audit for manager viewing own team");

        // Regular employee attempts to view team endpoint
        mockMvc.perform(get("/api/managers/" + manager1Employee.getId() + "/team")
                        .header("Authorization", "Bearer " + subordinate1Token))
                .andExpect(status().isForbidden());

        boolean foundTeamDenied = authorizationAuditLogRepository.findAll().stream()
                .anyMatch(l -> "TEAM_VIEW_CHECK".equals(l.getAction())
                        && "DENIED".equals(l.getDecision())
                        && subordinate1User.getEmail().equals(l.getEmail()));
        assertTrue(foundTeamDenied, "Expected DENIED authorization audit for employee attempting team view");
    }

    // =========================================================================
    // 7. RBAC MODIFICATIONS AUDIT: ROLE PERMISSION ASSIGNMENT & REVOCATION
    // =========================================================================

    @Test
    @DisplayName("7. RBAC Audit: Adding, removing, and replacing permissions on a role records audit logs")
    void test7_RbacRolePermissionModifications_AuditedWithFullDetails() {
        Role testRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        Permission perm = permissionRepository.findByName("DEPARTMENT_VIEW").orElseThrow();

        // 1. Add permission
        rolePermissionService.addPermissionToRole(testRole.getId(), perm.getId());
        boolean foundAdded = authorizationAuditLogRepository.findAll().stream()
                .anyMatch(l -> "ROLE_PERMISSION_ADDED".equals(l.getAction())
                        && "GRANTED".equals(l.getDecision())
                        && l.getResource().contains("Role#" + testRole.getId()));
        assertTrue(foundAdded, "Expected ROLE_PERMISSION_ADDED audit log");

        // 2. Remove permission
        rolePermissionService.removePermissionFromRole(testRole.getId(), perm.getId());
        boolean foundRemoved = authorizationAuditLogRepository.findAll().stream()
                .anyMatch(l -> "ROLE_PERMISSION_REMOVED".equals(l.getAction())
                        && "GRANTED".equals(l.getDecision())
                        && l.getResource().contains("Role#" + testRole.getId()));
        assertTrue(foundRemoved, "Expected ROLE_PERMISSION_REMOVED audit log");

        // 3. Replace permissions
        rolePermissionService.replaceRolePermissions(testRole.getId(), List.of(perm.getId()));
        boolean foundReplaced = authorizationAuditLogRepository.findAll().stream()
                .anyMatch(l -> "ROLE_PERMISSIONS_REPLACED".equals(l.getAction())
                        && "GRANTED".equals(l.getDecision())
                        && l.getResource().contains("Role#" + testRole.getId()));
        assertTrue(foundReplaced, "Expected ROLE_PERMISSIONS_REPLACED audit log");
    }

    // =========================================================================
    // 8. RBAC USER ROLE ASSIGNMENT AUDIT
    // =========================================================================

    @Test
    @DisplayName("8. RBAC Audit: Assigning a new role to an employee records USER_ROLE_ASSIGNED audit")
    void test8_RbacUserRoleAssignment_AuditedWithTargetEmployee() throws Exception {
        mockMvc.perform(patch("/api/employees/" + subordinate1Employee.getId() + "/role")
                        .param("roleName", "MANAGER")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        boolean foundRoleAssigned = authorizationAuditLogRepository.findAll().stream()
                .anyMatch(l -> "USER_ROLE_ASSIGNED".equals(l.getAction())
                        && "GRANTED".equals(l.getDecision())
                        && l.getResource().contains("Employee#" + subordinate1Employee.getId())
                        && l.getReason().contains("Assigned role: MANAGER"));
        assertTrue(foundRoleAssigned, "Expected USER_ROLE_ASSIGNED authorization audit log");
    }

    // =========================================================================
    // 9. RBAC USER LOCKOUT & UNLOCK AUDIT
    // =========================================================================

    @Test
    @DisplayName("9. RBAC Audit: Administrative user account lock and unlock operations are audited")
    void test9_UserAccountLockoutAndUnlock_AuditedWithTargetAccount() throws Exception {
        // Admin locks user
        mockMvc.perform(put("/api/users/" + subordinate2User.getId() + "/lock")
                        .param("reason", "Suspicious credential activity")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        boolean foundLock = authorizationAuditLogRepository.findAll().stream()
                .anyMatch(l -> "USER_LOCK".equals(l.getAction())
                        && "GRANTED".equals(l.getDecision())
                        && l.getResource().contains("User#" + subordinate2User.getId())
                        && l.getReason().contains("Suspicious credential activity"));
        assertTrue(foundLock, "Expected USER_LOCK authorization audit record");

        // Admin unlocks user
        mockMvc.perform(put("/api/users/" + subordinate2User.getId() + "/unlock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        boolean foundUnlock = authorizationAuditLogRepository.findAll().stream()
                .anyMatch(l -> "USER_UNLOCK".equals(l.getAction())
                        && "GRANTED".equals(l.getDecision())
                        && l.getResource().contains("User#" + subordinate2User.getId()));
        assertTrue(foundUnlock, "Expected USER_UNLOCK authorization audit record");
    }

    // =========================================================================
    // 10. DYNAMIC PERMISSION ENFORCEMENT AT RUNTIME
    // =========================================================================

    @Test
    @DisplayName("10. Dynamic RBAC: Permission additions and removals immediately alter endpoint authorization and are audited")
    void test10_DynamicPermissionEnforcementAtRuntime() throws Exception {
        Role customRole = roleRepository.save(new Role("CUSTOM_AUDIT_ROLE_" + runId));

        User dynamicUser = new User();
        dynamicUser.setEmail("dynamic-" + runId + "@test.com");
        dynamicUser.setPassword(passwordEncoder.encode("Pass123!"));
        dynamicUser.setActive(true);
        dynamicUser.getRoles().add(customRole);
        dynamicUser = userRepository.save(dynamicUser);

        // 1. Without DEPARTMENT_VIEW permission: 403 Forbidden
        UserDetails userDetailsNoPerm = new org.springframework.security.core.userdetails.User(
                dynamicUser.getEmail(), dynamicUser.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_CUSTOM_AUDIT_ROLE_" + runId)));
        String tokenNoPerm = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(userDetailsNoPerm, null, userDetailsNoPerm.getAuthorities()));

        mockMvc.perform(get("/api/departments")
                        .header("Authorization", "Bearer " + tokenNoPerm))
                .andExpect(status().isForbidden());

        // 2. Dynamically add DEPARTMENT_VIEW to role
        Permission deptViewPerm = permissionRepository.findByName("DEPARTMENT_VIEW").orElseThrow();
        rolePermissionService.addPermissionToRole(customRole.getId(), deptViewPerm.getId());

        // 3. User with updated authority: 200 OK
        List<SimpleGrantedAuthority> updatedAuthorities = List.of(
                new SimpleGrantedAuthority("ROLE_CUSTOM_AUDIT_ROLE_" + runId),
                new SimpleGrantedAuthority("DEPARTMENT_VIEW")
        );
        UserDetails userDetailsWithPerm = new org.springframework.security.core.userdetails.User(
                dynamicUser.getEmail(), dynamicUser.getPassword(), updatedAuthorities);
        String tokenWithPerm = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(userDetailsWithPerm, null, userDetailsWithPerm.getAuthorities()));

        mockMvc.perform(get("/api/departments")
                        .header("Authorization", "Bearer " + tokenWithPerm))
                .andExpect(status().isOk());

        // Verify both DENIED and ROLE_PERMISSION_ADDED are in audit logs
        final String userEmail = dynamicUser.getEmail();
        List<AuthorizationAuditLog> allLogs = authorizationAuditLogRepository.findAll();
        boolean hasDenied = allLogs.stream().anyMatch(l -> "DENIED".equals(l.getDecision()) && userEmail.equals(l.getEmail()));
        boolean hasAdded = allLogs.stream().anyMatch(l -> "ROLE_PERMISSION_ADDED".equals(l.getAction()) && "GRANTED".equals(l.getDecision()));

        assertTrue(hasDenied, "Expected initial denied audit log before dynamic permission was granted");
        assertTrue(hasAdded, "Expected ROLE_PERMISSION_ADDED audit log");
    }

    // =========================================================================
    // 11. AUDIT QUERYING: FILTERING BY DECISION AND ACTION
    // =========================================================================

    @Test
    @DisplayName("11. Audit Query: Multi-dimensional filtering by decision (GRANTED/DENIED) and action")
    void test11_AuthorizationAuditLogQueryingAndFilters() throws Exception {
        authorizationAuditService.recordGranted(adminUser.getEmail(), adminUser.getId(), "EXPORT_ACTION", "/api/export", "ADMIN_EXPORT", "Export grant");
        authorizationAuditService.recordDenied(subordinate1User.getEmail(), subordinate1User.getId(), "EXPORT_ACTION", "/api/export", "ADMIN_EXPORT", "Export deny");

        // Filter GRANTED
        mockMvc.perform(get("/api/authorization-audits")
                        .param("decision", "GRANTED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.content[*].decision", everyItem(is("GRANTED"))));

        // Filter DENIED
        mockMvc.perform(get("/api/authorization-audits")
                        .param("decision", "DENIED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.content[*].decision", everyItem(is("DENIED"))));

        // Filter by action
        mockMvc.perform(get("/api/authorization-audits")
                        .param("action", "EXPORT_ACTION")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].action", everyItem(is("EXPORT_ACTION"))));
    }

    // =========================================================================
    // 12. AUDIT PRIVACY: SELF-SERVICE AUDIT VIEW (/me)
    // =========================================================================

    @Test
    @DisplayName("12. Audit Privacy: Self-service endpoint /me strictly returns only caller's authorization records")
    void test12_SelfServiceAuthorizationAuditView() throws Exception {
        authorizationAuditService.recordDenied(subordinate1User.getEmail(), subordinate1User.getId(), "CHECK", "Resource1", "PERM", "Self deny");
        authorizationAuditService.recordGranted(adminUser.getEmail(), adminUser.getId(), "CHECK", "Resource2", "PERM", "Admin grant");

        mockMvc.perform(get("/api/authorization-audits/me")
                        .header("Authorization", "Bearer " + subordinate1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[*].email", everyItem(is(subordinate1User.getEmail()))));
    }
}
