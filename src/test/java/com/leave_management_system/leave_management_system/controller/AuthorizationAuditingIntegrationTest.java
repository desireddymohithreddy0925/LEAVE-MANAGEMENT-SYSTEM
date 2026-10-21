package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import com.leave_management_system.leave_management_system.service.AuthorizationAuditService;
import com.leave_management_system.leave_management_system.service.EmployeeService;
import com.leave_management_system.leave_management_system.service.LeaveRequestService;
import com.leave_management_system.leave_management_system.service.RolePermissionService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class AuthorizationAuditingIntegrationTest {

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
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuthorizationAuditLogRepository authorizationAuditLogRepository;

    @Autowired
    private com.leave_management_system.leave_management_system.repository.TransactionReviewRepository transactionReviewRepository;

    @Autowired
    private AuthorizationAuditService authorizationAuditService;

    @Autowired
    private RolePermissionService rolePermissionService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private LeaveRequestService leaveRequestService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private User adminUser;
    private String adminToken;

    private User managerUser;
    private Employee managerEmployee;
    private String managerToken;

    private User employeeUser;
    private Employee regularEmployee;
    private String employeeToken;

    private LeaveType leaveType;
    private LeaveRequest leaveRequest;
    private String uniqueSuffix;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        cleanup();

        uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Roles & Permissions setup
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        Role managerRole = roleRepository.findByName("MANAGER").orElseGet(() -> roleRepository.save(new Role("MANAGER")));
        Role empRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));

        Permission approvePerm = permissionRepository.findByName("LEAVE_APPROVE").orElseGet(() -> permissionRepository.save(new Permission("LEAVE_APPROVE", "Approve leave")));
        Permission viewAllPerm = permissionRepository.findByName("LEAVE_VIEW_ALL").orElseGet(() -> permissionRepository.save(new Permission("LEAVE_VIEW_ALL", "View all leaves")));
        Permission empViewPerm = permissionRepository.findByName("EMPLOYEE_VIEW").orElseGet(() -> permissionRepository.save(new Permission("EMPLOYEE_VIEW", "View employee")));
        Permission empUpdatePerm = permissionRepository.findByName("EMPLOYEE_UPDATE").orElseGet(() -> permissionRepository.save(new Permission("EMPLOYEE_UPDATE", "Update employee")));
        Permission auditViewPerm = permissionRepository.findByName("AUDIT_LOG_VIEW").orElseGet(() -> permissionRepository.save(new Permission("AUDIT_LOG_VIEW", "View audit logs")));

        managerRole.getPermissions().add(approvePerm);
        managerRole.getPermissions().add(viewAllPerm);
        roleRepository.save(managerRole);

        adminRole.getPermissions().add(approvePerm);
        adminRole.getPermissions().add(viewAllPerm);
        adminRole.getPermissions().add(empViewPerm);
        adminRole.getPermissions().add(empUpdatePerm);
        adminRole.getPermissions().add(auditViewPerm);
        roleRepository.save(adminRole);

        // 2. Setup Admin
        adminUser = new User();
        adminUser.setEmail("authz-admin-" + uniqueSuffix + "@test.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);

        List<SimpleGrantedAuthority> adminAuthorities = new ArrayList<>();
        adminAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        adminAuthorities.add(new SimpleGrantedAuthority("ADMIN_VIEW"));
        adminAuthorities.add(new SimpleGrantedAuthority("AUDIT_LOG_VIEW"));
        adminAuthorities.add(new SimpleGrantedAuthority("EMPLOYEE_UPDATE"));
        UserDetails adminDetails = new org.springframework.security.core.userdetails.User(
                adminUser.getEmail(), adminUser.getPassword(), adminAuthorities);
        adminToken = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities()));

        // 3. Setup Manager
        managerUser = new User();
        managerUser.setEmail("authz-mgr-" + uniqueSuffix + "@test.com");
        managerUser.setPassword(passwordEncoder.encode("MgrPass123!"));
        managerUser.setActive(true);
        managerUser.getRoles().add(managerRole);
        managerUser = userRepository.save(managerUser);

        managerEmployee = new Employee();
        managerEmployee.setFirstName("Manager");
        managerEmployee.setLastName("Boss");
        managerEmployee.setEmployeeCode("MGR-" + uniqueSuffix);
        managerEmployee.setPhone("555111" + uniqueSuffix.substring(0, 4));
        managerEmployee.setSalary(BigDecimal.valueOf(80000));
        managerEmployee.setStatus("ACTIVE");
        managerEmployee.setUser(managerUser);
        managerEmployee = employeeRepository.save(managerEmployee);

        List<SimpleGrantedAuthority> mgrAuthorities = new ArrayList<>();
        mgrAuthorities.add(new SimpleGrantedAuthority("ROLE_MANAGER"));
        mgrAuthorities.add(new SimpleGrantedAuthority("LEAVE_APPROVE"));
        mgrAuthorities.add(new SimpleGrantedAuthority("LEAVE_VIEW_ALL"));
        UserDetails mgrDetails = new org.springframework.security.core.userdetails.User(
                managerUser.getEmail(), managerUser.getPassword(), mgrAuthorities);
        managerToken = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(mgrDetails, null, mgrDetails.getAuthorities()));

        // 4. Setup Employee (reporting to Manager)
        employeeUser = new User();
        employeeUser.setEmail("authz-emp-" + uniqueSuffix + "@test.com");
        employeeUser.setPassword(passwordEncoder.encode("EmpPass123!"));
        employeeUser.setActive(true);
        employeeUser.getRoles().add(empRole);
        employeeUser = userRepository.save(employeeUser);

        regularEmployee = new Employee();
        regularEmployee.setFirstName("Regular");
        regularEmployee.setLastName("Worker");
        regularEmployee.setEmployeeCode("EMP-" + uniqueSuffix);
        regularEmployee.setPhone("555222" + uniqueSuffix.substring(0, 4));
        regularEmployee.setSalary(BigDecimal.valueOf(50000));
        regularEmployee.setStatus("ACTIVE");
        regularEmployee.setUser(employeeUser);
        regularEmployee.setManager(managerEmployee);
        regularEmployee = employeeRepository.save(regularEmployee);

        List<SimpleGrantedAuthority> empAuthorities = new ArrayList<>();
        empAuthorities.add(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        UserDetails empDetails = new org.springframework.security.core.userdetails.User(
                employeeUser.getEmail(), employeeUser.getPassword(), empAuthorities);
        employeeToken = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(empDetails, null, empDetails.getAuthorities()));

        // 5. Leave Type & Request Setup
        leaveType = new LeaveType();
        leaveType.setName("Vacation-" + uniqueSuffix);
        leaveType.setDefaultDays(15);
        leaveType = leaveTypeRepository.save(leaveType);

        LeaveBalance balance = new LeaveBalance();
        balance.setEmployee(regularEmployee);
        balance.setLeaveType(leaveType);
        balance.setYear(LocalDate.now().plusDays(20).getYear());
        balance.setTotal(15);
        balance.setAvailable(15);
        balance.setUsed(0);
        leaveBalanceRepository.save(balance);

        leaveRequest = new LeaveRequest();
        leaveRequest.setEmployee(regularEmployee);
        leaveRequest.setLeaveType(leaveType);
        leaveRequest.setStartDate(LocalDate.now().plusDays(20));
        leaveRequest.setEndDate(LocalDate.now().plusDays(22));
        leaveRequest.setStatus(LeaveStatus.PENDING);
        leaveRequest.setReason("Annual family trip");
        leaveRequest = leaveRequestRepository.save(leaveRequest);
    }

    @AfterEach
    public void cleanup() {
        transactionReviewRepository.deleteAll();
        leaveRequestRepository.deleteAll();
        leaveBalanceRepository.deleteAll();
        leaveTypeRepository.deleteAll();
        authorizationAuditLogRepository.deleteAll();
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        loginHistoryRepository.deleteAll();

        if (regularEmployee != null && regularEmployee.getId() != null) {
            employeeRepository.deleteById(regularEmployee.getId());
            regularEmployee = null;
        }
        if (managerEmployee != null && managerEmployee.getId() != null) {
            employeeRepository.deleteById(managerEmployee.getId());
            managerEmployee = null;
        }
        if (employeeUser != null && employeeUser.getId() != null) {
            userRepository.deleteById(employeeUser.getId());
            employeeUser = null;
        }
        if (managerUser != null && managerUser.getId() != null) {
            userRepository.deleteById(managerUser.getId());
            managerUser = null;
        }
        if (adminUser != null && adminUser.getId() != null) {
            userRepository.deleteById(adminUser.getId());
            adminUser = null;
        }
    }

    // 1. Manager approving employee leave records GRANTED decision in authorization audits
    @Test
    void canApproveLeaveRequest_AsManager_RecordsGrantedAuthorizationAudit() throws Exception {
        mockMvc.perform(post("/api/leave-requests/" + leaveRequest.getId() + "/approve")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        List<AuthorizationAuditLog> logs = authorizationAuditLogRepository.findAll();
        boolean foundGranted = logs.stream()
                .anyMatch(l -> "LEAVE_APPROVAL_CHECK".equals(l.getAction())
                        && "GRANTED".equals(l.getDecision())
                        && managerUser.getEmail().equals(l.getEmail()));

        assertTrue(foundGranted, "Expected a GRANTED authorization audit log for manager approving leave");
    }

    // 2. Unauthorized employee attempting to approve leave records DENIED decision
    @Test
    void canApproveLeaveRequest_AsUnauthorizedEmployee_RecordsDeniedAuthorizationAudit() throws Exception {
        mockMvc.perform(post("/api/leave-requests/" + leaveRequest.getId() + "/approve")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        List<AuthorizationAuditLog> logs = authorizationAuditLogRepository.findAll();
        boolean foundDenied = logs.stream()
                .anyMatch(l -> "DENIED".equals(l.getDecision())
                        && employeeUser.getEmail().equals(l.getEmail()));

        assertTrue(foundDenied, "Expected a DENIED authorization audit log when employee tries to approve leave");
    }

    // 3. Unauthenticated request to protected endpoint records 401 DENIED decision
    @Test
    void unauthenticatedRequest_RecordsDeniedAuthorizationAudit() throws Exception {
        mockMvc.perform(get("/api/authorization-audits")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        List<AuthorizationAuditLog> logs = authorizationAuditLogRepository.findAll();
        boolean foundDenied = logs.stream()
                .anyMatch(l -> "DENIED".equals(l.getDecision())
                        && "anonymousUser".equals(l.getEmail())
                        && l.getResource().contains("/api/authorization-audits"));

        assertTrue(foundDenied, "Expected a DENIED authorization audit for unauthenticated access");
    }

    // 4. Employee accessing admin endpoint records 403 DENIED decision
    @Test
    void employeeAccessingAdminEndpoint_RecordsForbiddenDeniedAudit() throws Exception {
        mockMvc.perform(get("/api/authorization-audits")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        List<AuthorizationAuditLog> logs = authorizationAuditLogRepository.findAll();
        boolean foundDenied = logs.stream()
                .anyMatch(l -> "DENIED".equals(l.getDecision())
                        && employeeUser.getEmail().equals(l.getEmail())
                        && (l.getResource().contains("/api/authorization-audits") || l.getResource().contains("AuthorizationAuditController")));

        assertTrue(foundDenied, "Expected a DENIED authorization audit for forbidden access");
    }

    // 5. Role permission modification records ROLE_PERMISSION_CHANGE granted audit
    @Test
    void rolePermissionModification_RecordsAuthorizationAudit() throws Exception {
        Role role = roleRepository.findByName("MANAGER").orElseThrow();
        Permission perm = permissionRepository.findByName("EMPLOYEE_VIEW").orElseGet(() -> permissionRepository.save(new Permission("EMPLOYEE_VIEW", "View employee")));

        rolePermissionService.addPermissionToRole(role.getId(), perm.getId());

        List<AuthorizationAuditLog> logs = authorizationAuditLogRepository.findAll();
        boolean foundRoleAudit = logs.stream()
                .anyMatch(l -> "ROLE_PERMISSION_ADDED".equals(l.getAction())
                        && "GRANTED".equals(l.getDecision())
                        && l.getResource().contains("Role#" + role.getId()));

        assertTrue(foundRoleAudit, "Expected a ROLE_PERMISSION_ADDED authorization audit");
    }

    // 6. Employee role change records USER_ROLE_ASSIGNED authorization audit
    @Test
    void employeeRoleChange_RecordsAuthorizationAudit() throws Exception {
        mockMvc.perform(patch("/api/employees/" + regularEmployee.getId() + "/role")
                        .param("roleName", "MANAGER")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        List<AuthorizationAuditLog> logs = authorizationAuditLogRepository.findAll();
        boolean foundRoleChangeAudit = logs.stream()
                .anyMatch(l -> "USER_ROLE_ASSIGNED".equals(l.getAction())
                        && "GRANTED".equals(l.getDecision())
                        && l.getResource().contains("Employee#" + regularEmployee.getId()));

        assertTrue(foundRoleChangeAudit, "Expected a USER_ROLE_ASSIGNED authorization audit");
    }

    // 7. Admin searches authorization audits filtering by decision
    @Test
    void searchAuthorizationAudits_FilterByDecision_ReturnsMatchingLogs() throws Exception {
        authorizationAuditService.recordGranted(adminUser.getEmail(), adminUser.getId(), "TEST_ACTION", "/api/test", "TEST_PERM", "Test grant");
        authorizationAuditService.recordDenied(employeeUser.getEmail(), employeeUser.getId(), "TEST_ACTION", "/api/test", "TEST_PERM", "Test deny");

        mockMvc.perform(get("/api/authorization-audits")
                        .param("decision", "GRANTED")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.content[*].decision", everyItem(is("GRANTED"))));

        mockMvc.perform(get("/api/authorization-audits")
                        .param("decision", "DENIED")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.content[*].decision", everyItem(is("DENIED"))));
    }

    // 8. Admin searches authorization audits filtering by action and userId
    @Test
    void searchAuthorizationAudits_FilterByActionAndUser_ReturnsMatching() throws Exception {
        authorizationAuditService.recordGranted(adminUser.getEmail(), adminUser.getId(), "CUSTOM_ACTION", "ResourceA", "PERM_A", "Granted reason");

        mockMvc.perform(get("/api/authorization-audits")
                        .param("action", "CUSTOM_ACTION")
                        .param("userId", adminUser.getId().toString())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].action", is("CUSTOM_ACTION")))
                .andExpect(jsonPath("$.content[0].userId", is(adminUser.getId().intValue())));
    }

    // 9. Summary metrics endpoint returns total, granted, denied, and percentage
    @Test
    void getSummaryMetrics_AsAdmin_ReturnsValidMetrics() throws Exception {
        authorizationAuditService.recordGranted(adminUser.getEmail(), adminUser.getId(), "ACTION_A", "Resource1", "PERM", "Grant");
        authorizationAuditService.recordDenied(employeeUser.getEmail(), employeeUser.getId(), "ACTION_B", "Resource2", "PERM", "Deny");

        mockMvc.perform(get("/api/authorization-audits/summary")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDecisions", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.grantedCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.deniedCount", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.denialRatePercentage", greaterThanOrEqualTo(0.0)));
    }

    // 10. Self-service endpoint /api/authorization-audits/me returns current user's audits
    @Test
    void getMyAuthorizationAudits_AsEmployee_ReturnsOnlySelfLogs() throws Exception {
        authorizationAuditService.recordDenied(employeeUser.getEmail(), employeeUser.getId(), "CHECK", "Resource", "PERM", "Self deny");
        authorizationAuditService.recordGranted(adminUser.getEmail(), adminUser.getId(), "CHECK", "Resource", "PERM", "Admin grant");

        mockMvc.perform(get("/api/authorization-audits/me")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[*].email", everyItem(is(employeeUser.getEmail()))));
    }

    // 11. Admin views authorization audits for specific user
    @Test
    void getAuthorizationAuditsForUser_AsAdmin_ReturnsUserAudits() throws Exception {
        authorizationAuditService.recordDenied(employeeUser.getEmail(), employeeUser.getId(), "CHECK", "Resource", "PERM", "User deny");

        mockMvc.perform(get("/api/authorization-audits/user/" + employeeUser.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].userId", is(employeeUser.getId().intValue())));
    }

    // 12. Get authorization audit by ID
    @Test
    void getAuthorizationAuditById_AsAdmin_ReturnsRecord() throws Exception {
        AuthorizationAuditLog log = authorizationAuditService.recordGranted(
                adminUser.getEmail(), adminUser.getId(), "SINGLE_FETCH", "Res#1", "PERM", "Testing fetch by id");

        mockMvc.perform(get("/api/authorization-audits/" + log.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(log.getId().intValue())))
                .andExpect(jsonPath("$.action", is("SINGLE_FETCH")))
                .andExpect(jsonPath("$.decision", is("GRANTED")));
    }

    // 13. Unified bridge endpoint GET /api/audit-logs/authorization returns results
    @Test
    void bridgeEndpoint_AuditLogsAuthorization_ReturnsPage() throws Exception {
        authorizationAuditService.recordGranted(adminUser.getEmail(), adminUser.getId(), "BRIDGE_ACTION", "ResourceBridge", "PERM", "Via bridge");

        mockMvc.perform(get("/api/audit-logs/authorization")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", notNullValue()))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));
    }
}
