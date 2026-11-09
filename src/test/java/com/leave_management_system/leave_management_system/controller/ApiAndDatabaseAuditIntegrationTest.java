package com.leave_management_system.leave_management_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.*;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("API and Database Audit - Comprehensive Endpoint Validation & Schema Integrity Test Suite")
public class ApiAndDatabaseAuditIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private String runId;

    private User adminUser;
    private User managerUser;
    private User employeeUser;
    private User peerUser;

    private Employee adminEmployee;
    private Employee managerEmployee;
    private Employee standardEmployee;
    private Employee peerEmployee;

    private Department engineeringDept;
    private Department operationsDept;

    private LeaveType annualLeave;
    private LeaveRequest standardLeave;
    private LeaveRequest peerLeave;

    private String adminToken;
    private String managerToken;
    private String employeeToken;
    private String peerToken;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        cleanupDatabase();

        runId = UUID.randomUUID().toString().substring(0, 8);

        // 1. Roles & Permissions Setup
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        Role managerRole = roleRepository.findByName("MANAGER").orElseGet(() -> roleRepository.save(new Role("MANAGER")));
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));

        Permission permDeptManage = permissionRepository.findByName("DEPARTMENT_MANAGE")
                .orElseGet(() -> permissionRepository.save(new Permission("DEPARTMENT_MANAGE", "Manage departments")));
        Permission permDeptView = permissionRepository.findByName("DEPARTMENT_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("DEPARTMENT_VIEW", "View departments")));
        Permission permEmpCreate = permissionRepository.findByName("EMPLOYEE_CREATE")
                .orElseGet(() -> permissionRepository.save(new Permission("EMPLOYEE_CREATE", "Create employees")));
        Permission permEmpView = permissionRepository.findByName("EMPLOYEE_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("EMPLOYEE_VIEW", "View employees")));
        Permission permEmpUpdate = permissionRepository.findByName("EMPLOYEE_UPDATE")
                .orElseGet(() -> permissionRepository.save(new Permission("EMPLOYEE_UPDATE", "Update employees")));
        Permission permLeaveTypeManage = permissionRepository.findByName("LEAVE_TYPE_MANAGE")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_TYPE_MANAGE", "Manage leave types")));
        Permission permLeaveViewAll = permissionRepository.findByName("LEAVE_VIEW_ALL")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_VIEW_ALL", "View all leaves")));
        Permission permLeaveViewTeam = permissionRepository.findByName("LEAVE_VIEW_TEAM")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_VIEW_TEAM", "View team leaves")));
        Permission permLeaveCreate = permissionRepository.findByName("LEAVE_CREATE")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_CREATE", "Create leave request")));
        Permission permLeaveApprove = permissionRepository.findByName("LEAVE_APPROVE")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_APPROVE", "Approve leave requests")));
        Permission permLeaveReject = permissionRepository.findByName("LEAVE_REJECT")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_REJECT", "Reject leave requests")));
        Permission permLeaveCancel = permissionRepository.findByName("LEAVE_CANCEL")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_CANCEL", "Cancel leave requests")));
        Permission permAuditView = permissionRepository.findByName("AUDIT_LOG_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("AUDIT_LOG_VIEW", "View audit logs")));

        adminRole.getPermissions().clear();
        adminRole.getPermissions().addAll(Set.of(permDeptManage, permDeptView, permEmpCreate, permEmpView,
                permEmpUpdate, permLeaveTypeManage, permLeaveViewAll, permLeaveViewTeam, permLeaveCreate, permLeaveApprove,
                permLeaveReject, permLeaveCancel, permAuditView));
        roleRepository.save(adminRole);

        managerRole.getPermissions().clear();
        managerRole.getPermissions().addAll(Set.of(permLeaveViewTeam, permLeaveApprove, permLeaveReject,
                permLeaveCreate, permLeaveCancel, permEmpView));
        roleRepository.save(managerRole);

        employeeRole.getPermissions().clear();
        employeeRole.getPermissions().addAll(Set.of(permLeaveCreate, permLeaveCancel));
        roleRepository.save(employeeRole);

        // 2. Departments Setup
        engineeringDept = new Department();
        engineeringDept.setName("Audit Engineering Dept " + runId);
        engineeringDept = departmentRepository.save(engineeringDept);

        operationsDept = new Department();
        operationsDept.setName("Audit Operations Dept " + runId);
        operationsDept = departmentRepository.save(operationsDept);

        // 3. Admin User & Employee
        adminUser = new User();
        adminUser.setEmail("admin-audit-" + runId + "@enterprise.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.setAccountNonLocked(true);
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);

        adminEmployee = new Employee();
        adminEmployee.setFirstName("Admin");
        adminEmployee.setLastName("Auditor");
        adminEmployee.setEmployeeCode("ADM-" + runId);
        adminEmployee.setStatus("ACTIVE");
        adminEmployee.setSalary(new BigDecimal("120000.00"));
        adminEmployee.setDepartment(engineeringDept);
        adminEmployee.setUser(adminUser);
        adminEmployee = employeeRepository.save(adminEmployee);

        adminUser.setEmployee(adminEmployee);
        adminUser = userRepository.save(adminUser);

        // 4. Manager User & Employee (Head of Engineering Dept)
        managerUser = new User();
        managerUser.setEmail("manager-audit-" + runId + "@enterprise.com");
        managerUser.setPassword(passwordEncoder.encode("MgrPass123!"));
        managerUser.setActive(true);
        managerUser.setAccountNonLocked(true);
        managerUser.getRoles().add(managerRole);
        managerUser = userRepository.save(managerUser);

        managerEmployee = new Employee();
        managerEmployee.setFirstName("Manager");
        managerEmployee.setLastName("Engineer");
        managerEmployee.setEmployeeCode("MGR-" + runId);
        managerEmployee.setStatus("ACTIVE");
        managerEmployee.setSalary(new BigDecimal("95000.00"));
        managerEmployee.setDepartment(engineeringDept);
        managerEmployee.setUser(managerUser);
        managerEmployee.setManager(adminEmployee);
        managerEmployee = employeeRepository.save(managerEmployee);

        managerUser.setEmployee(managerEmployee);
        managerUser = userRepository.save(managerUser);

        engineeringDept.setManager(managerEmployee);
        engineeringDept = departmentRepository.save(engineeringDept);

        // 5. Standard Employee (Reports to managerEmployee in engineeringDept)
        employeeUser = new User();
        employeeUser.setEmail("john-audit-" + runId + "@enterprise.com");
        employeeUser.setPassword(passwordEncoder.encode("UserPass123!"));
        employeeUser.setActive(true);
        employeeUser.setAccountNonLocked(true);
        employeeUser.getRoles().add(employeeRole);
        employeeUser = userRepository.save(employeeUser);

        standardEmployee = new Employee();
        standardEmployee.setFirstName("John");
        standardEmployee.setLastName("AuditEmp");
        standardEmployee.setEmployeeCode("EMP-" + runId);
        standardEmployee.setStatus("ACTIVE");
        standardEmployee.setSalary(new BigDecimal("75000.00"));
        standardEmployee.setDepartment(engineeringDept);
        standardEmployee.setManager(managerEmployee);
        standardEmployee.setUser(employeeUser);
        standardEmployee = employeeRepository.save(standardEmployee);

        employeeUser.setEmployee(standardEmployee);
        employeeUser = userRepository.save(employeeUser);

        // 6. Peer Employee (In operationsDept, NOT managed by managerEmployee)
        peerUser = new User();
        peerUser.setEmail("peer-audit-" + runId + "@enterprise.com");
        peerUser.setPassword(passwordEncoder.encode("PeerPass123!"));
        peerUser.setActive(true);
        peerUser.setAccountNonLocked(true);
        peerUser.getRoles().add(employeeRole);
        peerUser = userRepository.save(peerUser);

        peerEmployee = new Employee();
        peerEmployee.setFirstName("Alice");
        peerEmployee.setLastName("PeerEmp");
        peerEmployee.setEmployeeCode("PEER-" + runId);
        peerEmployee.setStatus("ACTIVE");
        peerEmployee.setSalary(new BigDecimal("72000.00"));
        peerEmployee.setDepartment(operationsDept);
        peerEmployee.setManager(adminEmployee);
        peerEmployee.setUser(peerUser);
        peerEmployee = employeeRepository.save(peerEmployee);

        peerUser.setEmployee(peerEmployee);
        peerUser = userRepository.save(peerUser);

        // 7. Leave Type Setup
        annualLeave = new LeaveType();
        annualLeave.setName("Annual Leave Audit " + runId);
        annualLeave.setDescription("Annual Paid Time Off");
        annualLeave.setDefaultDays(25);
        annualLeave.setActive(true);
        annualLeave = leaveTypeRepository.save(annualLeave);

        // 8. Leave Balances
        LeaveBalance stdBalance = new LeaveBalance();
        stdBalance.setEmployee(standardEmployee);
        stdBalance.setLeaveType(annualLeave);
        stdBalance.setTotal(25);
        stdBalance.setAvailable(20);
        stdBalance.setUsed(5);
        stdBalance.setYear(LocalDate.now().getYear());
        leaveBalanceRepository.save(stdBalance);

        LeaveBalance peerBalance = new LeaveBalance();
        peerBalance.setEmployee(peerEmployee);
        peerBalance.setLeaveType(annualLeave);
        peerBalance.setTotal(25);
        peerBalance.setAvailable(25);
        peerBalance.setUsed(0);
        peerBalance.setYear(LocalDate.now().getYear());
        leaveBalanceRepository.save(peerBalance);

        LeaveBalance mgrBalance = new LeaveBalance();
        mgrBalance.setEmployee(managerEmployee);
        mgrBalance.setLeaveType(annualLeave);
        mgrBalance.setTotal(25);
        mgrBalance.setAvailable(20);
        mgrBalance.setUsed(5);
        mgrBalance.setYear(LocalDate.now().getYear());
        leaveBalanceRepository.save(mgrBalance);

        // 9. Leave Requests
        standardLeave = new LeaveRequest();
        standardLeave.setEmployee(standardEmployee);
        standardLeave.setLeaveType(annualLeave);
        standardLeave.setStartDate(LocalDate.now().plusDays(20));
        standardLeave.setEndDate(LocalDate.now().plusDays(22));
        standardLeave.setStatus(LeaveStatus.PENDING);
        standardLeave.setReason("Standard vacation");
        standardLeave = leaveRequestRepository.saveAndFlush(standardLeave);

        peerLeave = new LeaveRequest();
        peerLeave.setEmployee(peerEmployee);
        peerLeave.setLeaveType(annualLeave);
        peerLeave.setStartDate(LocalDate.now().plusDays(25));
        peerLeave.setEndDate(LocalDate.now().plusDays(27));
        peerLeave.setStatus(LeaveStatus.PENDING);
        peerLeave.setReason("Peer vacation");
        peerLeave = leaveRequestRepository.saveAndFlush(peerLeave);

        // 10. JWT Tokens
        adminToken = generateToken(adminUser, adminRole);
        managerToken = generateToken(managerUser, managerRole);
        employeeToken = generateToken(employeeUser, employeeRole);
        peerToken = generateToken(peerUser, employeeRole);
    }

    private String generateToken(User user, Role role) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName().replace("ROLE_", "")));
        if (role.getPermissions() != null) {
            role.getPermissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.getName())));
        }
        UserDetails details = new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPassword(), authorities);
        return jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    @AfterEach
    public void cleanup() {
        cleanupDatabase();
    }

    private void cleanupDatabase() {
        try {
            transactionReviewRepository.deleteAll();
            authorizationAuditLogRepository.deleteAll();
            securityEventRepository.deleteAll();
            auditLogRepository.deleteAll();
            loginHistoryRepository.deleteAll();
            refreshTokenRepository.deleteAll();
            leaveRequestRepository.deleteAll();
            leaveBalanceRepository.deleteAll();
            leaveTypeRepository.deleteAll();

            // Safely unlink department managers before deleting
            departmentRepository.findAll().forEach(dept -> {
                if (dept.getManager() != null) {
                    dept.setManager(null);
                    departmentRepository.save(dept);
                }
            });

            // Safely unlink employee manager, department, and user associations
            employeeRepository.findAll().forEach(emp -> {
                boolean dirty = false;
                if (emp.getDepartment() != null) {
                    emp.setDepartment(null);
                    dirty = true;
                }
                if (emp.getManager() != null) {
                    emp.setManager(null);
                    dirty = true;
                }
                if (emp.getUser() != null) {
                    emp.setUser(null);
                    dirty = true;
                }
                if (dirty) {
                    employeeRepository.save(emp);
                }
            });

            userRepository.findAll().forEach(user -> {
                if (user.getEmployee() != null) {
                    user.setEmployee(null);
                    userRepository.save(user);
                }
            });

            departmentRepository.deleteAll();
            employeeRepository.deleteAll();
            userRepository.deleteAll();
        } catch (Exception ignored) {
        }
    }

    // =========================================================================
    // SECTION 1: API ERROR RESPONSE STRUCTURE AUDIT
    // =========================================================================
    @Nested
    @DisplayName("1. API Error Response Structure & Uniformity Audit")
    class ErrorResponseStructureAudit {

        @Test
        @DisplayName("Audit 400 Bad Request error payload format contains timestamp, status, error, and message")
        void testBadRequestStructure() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Validation Failed"))
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.errors").isMap())
                    .andExpect(jsonPath("$.errors.email").exists())
                    .andExpect(jsonPath("$.errors.password").exists());
        }

        @Test
        @DisplayName("Audit 401 Unauthorized error payload format")
        void testUnauthorizedStructure() throws Exception {
            mockMvc.perform(get("/api/employees/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }

        @Test
        @DisplayName("Audit 403 Forbidden error payload format")
        void testForbiddenStructure() throws Exception {
            mockMvc.perform(get("/api/audit-logs")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"))
                    .andExpect(jsonPath("$.message").value(containsString("permission")));
        }

        @Test
        @DisplayName("Audit 404 Not Found error payload format on missing entity")
        void testNotFoundStructure() throws Exception {
            mockMvc.perform(get("/api/departments/999999")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }

        @Test
        @DisplayName("Audit 404 Not Found error payload format on unmapped route")
        void testNoResourceFoundStructure() throws Exception {
            mockMvc.perform(get("/api/non-existent-endpoint-route")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Resource not found"));
        }

        @Test
        @DisplayName("Audit 405 Method Not Allowed error payload format")
        void testMethodNotAllowedStructure() throws Exception {
            mockMvc.perform(post("/api/audit-logs")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(405))
                    .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                    .andExpect(jsonPath("$.message").value(containsString("not supported")));
        }

        @Test
        @DisplayName("Audit 415 Unsupported Media Type error payload format")
        void testUnsupportedMediaTypeStructure() throws Exception {
            mockMvc.perform(post("/api/departments")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("name=Finance"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.status").value(415))
                    .andExpect(jsonPath("$.error").value("Unsupported Media Type"))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }

        @Test
        @DisplayName("Audit 400 Bad Request on malformed JSON payload")
        void testMalformedJsonStructure() throws Exception {
            mockMvc.perform(post("/api/departments")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ name: broken_unquoted_json }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Malformed JSON request"));
        }

        @Test
        @DisplayName("Audit 400 Bad Request on path variable type mismatch")
        void testPathTypeMismatchStructure() throws Exception {
            mockMvc.perform(get("/api/departments/not-a-number")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Invalid parameter type provided"));
        }
    }

    // =========================================================================
    // SECTION 2: ENDPOINT INPUT VALIDATION AUDIT
    // =========================================================================
    @Nested
    @DisplayName("2. Endpoint Input Validation & Boundary Checks Audit")
    class InputValidationAudit {

        @Test
        @DisplayName("Audit Department creation: Blank name and overlong name (>100 chars) rejected with 400")
        void testDepartmentValidation() throws Exception {
            DepartmentRequestDTO blankDto = new DepartmentRequestDTO();
            blankDto.setName("   ");

            mockMvc.perform(post("/api/departments")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(blankDto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").isNotEmpty());

            DepartmentRequestDTO overlongDto = new DepartmentRequestDTO();
            overlongDto.setName("D".repeat(101));

            mockMvc.perform(post("/api/departments")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(overlongDto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").value(containsString("100")));
        }

        @Test
        @DisplayName("Audit Leave Type creation: Negative/zero defaultDays and over 365 defaultDays rejected with 400")
        void testLeaveTypeValidation() throws Exception {
            LeaveTypeRequestDTO zeroDto = new LeaveTypeRequestDTO("Maternity", "Description", 0);

            mockMvc.perform(post("/api/leave-types")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(zeroDto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.defaultDays").value(containsString("at least 1")));

            LeaveTypeRequestDTO excessiveDto = new LeaveTypeRequestDTO("Maternity", "Description", 366);

            mockMvc.perform(post("/api/leave-types")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(excessiveDto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.defaultDays").value(containsString("365")));
        }

        @Test
        @DisplayName("Audit Leave Request application: End date before start date rejected with 400")
        void testLeaveRequestEndDateBeforeStartDate() throws Exception {
            LeaveRequestDTO dto = new LeaveRequestDTO();
            dto.setLeaveTypeId(annualLeave.getId());
            dto.setStartDate(LocalDate.now().plusDays(10));
            dto.setEndDate(LocalDate.now().plusDays(5)); // before start date

            mockMvc.perform(post("/api/leave-requests")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.endDate").value(containsString("before start date")));
        }

        @Test
        @DisplayName("Audit Leave Request application: Past start date rejected with 400")
        void testLeaveRequestPastStartDate() throws Exception {
            LeaveRequestDTO dto = new LeaveRequestDTO();
            dto.setLeaveTypeId(annualLeave.getId());
            dto.setStartDate(LocalDate.now().minusDays(2));
            dto.setEndDate(LocalDate.now().plusDays(2));

            mockMvc.perform(post("/api/leave-requests")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.startDate").value(containsString("past")));
        }

        @Test
        @DisplayName("Audit Leave Request application: Spanning multiple calendar years rejected with 400")
        void testLeaveRequestSpanningMultipleYears() throws Exception {
            int curYear = LocalDate.now().getYear();
            LeaveRequestDTO dto = new LeaveRequestDTO();
            dto.setLeaveTypeId(annualLeave.getId());
            dto.setStartDate(LocalDate.of(curYear + 1, 12, 28));
            dto.setEndDate(LocalDate.of(curYear + 2, 1, 4));

            mockMvc.perform(post("/api/leave-requests")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.endDate").value(containsString("multiple calendar years")));
        }

        @Test
        @DisplayName("Audit Password Change: Minimum length 8 enforcement")
        void testPasswordChangeValidation() throws Exception {
            PasswordChangeRequestDTO shortPass = new PasswordChangeRequestDTO("OldPass123!", "short");

            mockMvc.perform(post("/api/auth/change-password")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(shortPass)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.newPassword").value(containsString("8")));
        }

        @Test
        @DisplayName("Audit Employee Status: Disallowed status string rejected with 400")
        void testEmployeeStatusValidation() throws Exception {
            EmployeeRequestDTO dto = new EmployeeRequestDTO();
            dto.setFirstName("Bob");
            dto.setLastName("Builder");
            dto.setEmail("bob.builder." + runId + "@enterprise.com");
            dto.setEmployeeCode("EMP-BOB-" + runId);
            dto.setStatus("TERMINATED_CUSTOM_INVALID"); // Disallowed
            dto.setSalary(new BigDecimal("60000.00"));
            dto.setDepartmentId(engineeringDept.getId());

            mockMvc.perform(post("/api/employees")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.status").value(containsString("Status must be either ACTIVE or INACTIVE")));
        }
    }

    // =========================================================================
    // SECTION 3: DATABASE CONSTRAINTS & SCHEMA INTEGRITY AUDIT
    // =========================================================================
    @Nested
    @DisplayName("3. Database Schema Constraints & Entity Integrity Audit")
    class DatabaseConstraintAudit {

        @Test
        @DisplayName("Audit Unique Constraint: Duplicate Department Name returns 409 Conflict")
        void testDuplicateDepartmentNameConflict() throws Exception {
            DepartmentRequestDTO dto = new DepartmentRequestDTO();
            dto.setName(engineeringDept.getName()); // Already exists

            mockMvc.perform(post("/api/departments")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("Conflict"))
                    .andExpect(jsonPath("$.message").value(containsString("already exists")));
        }

        @Test
        @DisplayName("Audit Unique Constraint: Duplicate Leave Type Name returns 409 Conflict")
        void testDuplicateLeaveTypeNameConflict() throws Exception {
            LeaveTypeRequestDTO dto = new LeaveTypeRequestDTO(annualLeave.getName(), "Duplicate type", 20);

            mockMvc.perform(post("/api/leave-types")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value(containsString("already exists")));
        }

        @Test
        @DisplayName("Audit Unique Constraint: Duplicate Employee Code/Email in registration returns 409 Conflict")
        void testDuplicateEmployeeEmailConflict() throws Exception {
            RegisterRequestDTO dto = new RegisterRequestDTO();
            dto.setFirstName("Duplicate");
            dto.setLastName("User");
            dto.setEmail(employeeUser.getEmail()); // Existing email
            dto.setPassword("NewPassword123!");
            dto.setPhone("+12345678901");
            dto.setEmployeeCode("EMP-NEW-" + runId);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value(containsString("Email already exists")));
        }

        @Test
        @DisplayName("Audit Referential Integrity: Deleting Department with assigned employees is rejected")
        void testDeleteDepartmentWithActiveEmployees() throws Exception {
            mockMvc.perform(delete("/api/departments/" + engineeringDept.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isConflict());

            assertTrue(departmentRepository.findById(engineeringDept.getId()).isPresent(),
                    "Department with active employees must not be deleted");
        }

        @Test
        @DisplayName("Audit Referential Integrity: Deleting Leave Type with active balances is rejected")
        void testDeleteLeaveTypeWithActiveBalances() throws Exception {
            mockMvc.perform(delete("/api/leave-types/" + annualLeave.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest());

            assertTrue(leaveTypeRepository.findById(annualLeave.getId()).isPresent(),
                    "Leave type with active balances must not be deleted");
        }

        @Test
        @DisplayName("Audit Foreign Key Integrity: Assigning employee to non-existent Department returns 404")
        void testEmployeeNonExistentDepartmentFk() throws Exception {
            EmployeeRequestDTO dto = new EmployeeRequestDTO();
            dto.setFirstName("Dave");
            dto.setLastName("Orphan");
            dto.setEmail("dave.orphan." + runId + "@enterprise.com");
            dto.setEmployeeCode("EMP-ORPHAN-" + runId);
            dto.setStatus("ACTIVE");
            dto.setSalary(new BigDecimal("55000.00"));
            dto.setDepartmentId(999999L); // Non-existent FK

            mockMvc.perform(post("/api/employees")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value(containsString("Department not found")));
        }

        @Test
        @DisplayName("Audit Optimistic Locking: Version column increments and prevents lost updates")
        void testOptimisticLockingOnLeaveRequest() {
            LeaveRequest request = new LeaveRequest();
            request.setEmployee(standardEmployee);
            request.setLeaveType(annualLeave);
            request.setStartDate(LocalDate.now().plusDays(20));
            request.setEndDate(LocalDate.now().plusDays(22));
            request.setStatus(LeaveStatus.PENDING);
            request.setReason("Annual vacation trip");
            request = leaveRequestRepository.saveAndFlush(request);

            Long initialVersion = request.getVersion();
            assertNotNull(initialVersion, "Entity must have non-null version on persist");

            request.setReason("Updated vacation trip destination");
            request = leaveRequestRepository.saveAndFlush(request);

            assertEquals(initialVersion + 1, request.getVersion(), "Entity version must increment after update");
        }
    }

    // =========================================================================
    // SECTION 4: BUSINESS INVARIANT & BALANCE AUDIT
    // =========================================================================
    @Nested
    @DisplayName("4. Business Rule Invariant & Leave Balance Audit")
    class BusinessInvariantAudit {

        @Test
        @DisplayName("Audit Insufficient Leave Balance: Requesting more days than available returns 400")
        void testInsufficientBalanceRejection() throws Exception {
            LeaveBalance balance = leaveBalanceRepository.findByEmployeeAndLeaveTypeAndYear(standardEmployee, annualLeave, LocalDate.now().getYear()).orElseThrow();
            balance.setAvailable(2);
            leaveBalanceRepository.saveAndFlush(balance);

            LeaveRequestDTO dto = new LeaveRequestDTO();
            dto.setLeaveTypeId(annualLeave.getId());
            dto.setStartDate(LocalDate.now().plusDays(30));
            dto.setEndDate(LocalDate.now().plusDays(36)); // 5 working days > 2 available
            dto.setReason("Long sabbatical attempt exceeding quota");

            mockMvc.perform(post("/api/leave-requests")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("Insufficient leave balance")));
        }

        @Test
        @DisplayName("Audit Overlapping Leave Rejection: Consecutive overlapping leaves return 400")
        void testOverlappingLeaveApplicationRejection() throws Exception {
            // First valid leave request
            LeaveRequest firstLeave = new LeaveRequest();
            firstLeave.setEmployee(standardEmployee);
            firstLeave.setLeaveType(annualLeave);
            firstLeave.setStartDate(LocalDate.now().plusDays(10));
            firstLeave.setEndDate(LocalDate.now().plusDays(14));
            firstLeave.setStatus(LeaveStatus.APPROVED);
            firstLeave.setReason("First conference trip");
            leaveRequestRepository.saveAndFlush(firstLeave);

            // Second leave overlapping with first
            LeaveRequestDTO overlappingDto = new LeaveRequestDTO();
            overlappingDto.setLeaveTypeId(annualLeave.getId());
            overlappingDto.setStartDate(LocalDate.now().plusDays(12)); // Overlaps with [10..14]
            overlappingDto.setEndDate(LocalDate.now().plusDays(16));
            overlappingDto.setReason("Conflicting workshop");

            mockMvc.perform(post("/api/leave-requests")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(overlappingDto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("overlap")));
        }

        @Test
        @DisplayName("Audit Leave Cancellation: Non-pending, non-approved leaves cannot be cancelled")
        void testCannotCancelRejectedLeave() throws Exception {
            LeaveRequest rejectedLeave = new LeaveRequest();
            rejectedLeave.setEmployee(standardEmployee);
            rejectedLeave.setLeaveType(annualLeave);
            rejectedLeave.setStartDate(LocalDate.now().plusDays(10));
            rejectedLeave.setEndDate(LocalDate.now().plusDays(12));
            rejectedLeave.setStatus(LeaveStatus.REJECTED);
            rejectedLeave.setReason("Doctor visit");
            rejectedLeave.setRejectionReason("Staffing shortage");
            rejectedLeave = leaveRequestRepository.saveAndFlush(rejectedLeave);

            mockMvc.perform(put("/api/leave-requests/" + rejectedLeave.getId() + "/cancel")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("Cannot cancel a rejected leave request")));
        }

        @Test
        @DisplayName("Audit Authorization: Standard employee cannot approve leave requests")
        void testStandardEmployeeCannotApproveLeave() throws Exception {
            LeaveRequest adminLeave = new LeaveRequest();
            adminLeave.setEmployee(adminEmployee);
            adminLeave.setLeaveType(annualLeave);
            adminLeave.setStartDate(LocalDate.now().plusDays(15));
            adminLeave.setEndDate(LocalDate.now().plusDays(17));
            adminLeave.setStatus(LeaveStatus.PENDING);
            adminLeave.setReason("Executive retreat");
            adminLeave = leaveRequestRepository.saveAndFlush(adminLeave);

            // Standard employee attempting to approve leave
            mockMvc.perform(put("/api/leave-requests/" + adminLeave.getId() + "/approve")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message").value(containsString("permission")));
        }
    }

    // =========================================================================
    // SECTION 5: API AUTHORIZATION & DATA OWNERSHIP VERIFICATION
    // =========================================================================
    @Nested
    @DisplayName("5. API Authorization & Data Ownership Verification")
    class DataOwnershipVerificationAudit {

        @Test
        @DisplayName("Horizontal IDOR: Employee cannot view another employee's profile by ID")
        void testEmployeeCannotViewPeerProfile_Returns403() throws Exception {
            mockMvc.perform(get("/api/employees/" + peerEmployee.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"));
        }

        @Test
        @DisplayName("Horizontal IDOR: Employee cannot update another employee's profile")
        void testEmployeeCannotUpdatePeerProfile_Returns403() throws Exception {
            EmployeeProfileUpdateDTO updateDto = new EmployeeProfileUpdateDTO();
            updateDto.setFirstName("Hacked");
            updateDto.setLastName("Profile");
            updateDto.setPhone("+19999999999");

            mockMvc.perform(put("/api/employees/" + peerEmployee.getId() + "/profile")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Horizontal IDOR: Employee cannot view another employee's leave request")
        void testEmployeeCannotViewPeerLeaveRequest_Returns403() throws Exception {
            mockMvc.perform(get("/api/leave-requests/" + peerLeave.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Horizontal IDOR: Employee cannot view another employee's leave balance by ID")
        void testEmployeeCannotViewPeerLeaveBalance_Returns403() throws Exception {
            mockMvc.perform(get("/api/leave-balances/employee/" + peerEmployee.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Horizontal IDOR: Employee cannot check overlap for another employee")
        void testEmployeeCannotCheckOverlapForPeer_Returns403() throws Exception {
            mockMvc.perform(get("/api/leave-requests/check-overlap")
                            .header("Authorization", "Bearer " + employeeToken)
                            .param("startDate", LocalDate.now().plusDays(25).toString())
                            .param("endDate", LocalDate.now().plusDays(27).toString())
                            .param("employeeId", peerEmployee.getId().toString()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Horizontal IDOR: Employee cannot cancel another employee's leave request")
        void testEmployeeCannotCancelPeerLeave_Returns403() throws Exception {
            mockMvc.perform(put("/api/leave-requests/" + peerLeave.getId() + "/cancel")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Horizontal IDOR: Employee cannot export another employee's sensitive GDPR personal data")
        void testEmployeeCannotExportPeerPersonalData_Returns403() throws Exception {
            mockMvc.perform(get("/api/sensitive-data/export/" + peerEmployee.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Horizontal IDOR: Employee cannot view another employee's hierarchy")
        void testEmployeeCannotViewPeerHierarchy_Returns403() throws Exception {
            mockMvc.perform(get("/api/employees/" + peerEmployee.getId() + "/hierarchy")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Data Ownership: Leave application ignores spoofed employeeId in payload and enforces caller identity")
        void testLeaveCreationEnforcesCallerOwnership_IgnoresPayloadSpoofing() throws Exception {
            LeaveRequestDTO spoofDto = new LeaveRequestDTO();
            spoofDto.setEmployeeId(peerEmployee.getId()); // Attacker supplies peer ID
            spoofDto.setLeaveTypeId(annualLeave.getId());
            spoofDto.setStartDate(LocalDate.now().plusDays(30));
            spoofDto.setEndDate(LocalDate.now().plusDays(32));
            spoofDto.setReason("Legitimate applicant request");

            mockMvc.perform(post("/api/leave-requests")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(spoofDto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.employee.id").value(standardEmployee.getId())); // Forced to caller
        }

        @Test
        @DisplayName("Data Ownership: Self-service endpoints strictly scope to the authenticated caller")
        void testSelfServiceEndpointsEnforceCallerOwnership() throws Exception {
            // /api/employees/me returns standardEmployee
            mockMvc.perform(get("/api/employees/me")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(standardEmployee.getId()))
                    .andExpect(jsonPath("$.employeeCode").value(standardEmployee.getEmployeeCode()));

            // /api/leave-requests/me returns only standardEmployee's leaves
            mockMvc.perform(get("/api/leave-requests/me")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                    .andExpect(jsonPath("$.content[0].employee.id").value(standardEmployee.getId()));

            // /api/leave-balances/me returns only standardEmployee's balances
            mockMvc.perform(get("/api/leave-balances/me")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                    .andExpect(jsonPath("$[0].employee.id").value(standardEmployee.getId()));

            // /api/sensitive-data/me/export returns only standardEmployee's data
            mockMvc.perform(get("/api/sensitive-data/me/export")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.employee.id").value(standardEmployee.getId()));
        }

        @Test
        @DisplayName("Manager Scoping: Manager CAN view own team subordinate profile, leave, and balances")
        void testManagerCanViewSubordinateData() throws Exception {
            // Manager can view managed subordinate profile
            mockMvc.perform(get("/api/employees/" + standardEmployee.getId())
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(standardEmployee.getId()));

            // Manager can view managed subordinate leave request
            mockMvc.perform(get("/api/leave-requests/" + standardLeave.getId())
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(standardLeave.getId()));

            // Manager can view managed subordinate balances
            mockMvc.perform(get("/api/leave-balances/employee/" + standardEmployee.getId())
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

            // Manager can view their own team list
            mockMvc.perform(get("/api/managers/me/team")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", hasItem(standardEmployee.getId().intValue())))
                    .andExpect(jsonPath("$[*].id", not(hasItem(peerEmployee.getId().intValue()))));
        }

        @Test
        @DisplayName("Manager Scoping: Manager CANNOT view unmanaged peer employee profile or balances")
        void testManagerCannotViewUnmanagedPeerData_Returns403() throws Exception {
            // Manager attempting to view non-subordinate peer
            mockMvc.perform(get("/api/employees/" + peerEmployee.getId())
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));

            // Manager attempting to view non-subordinate peer balances
            mockMvc.perform(get("/api/leave-balances/employee/" + peerEmployee.getId())
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Manager Approval: Manager CAN approve subordinate leave request")
        void testManagerCanApproveSubordinateLeave() throws Exception {
            mockMvc.perform(put("/api/leave-requests/" + standardLeave.getId() + "/approve")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));

            LeaveRequest updated = leaveRequestRepository.findById(standardLeave.getId()).orElseThrow();
            assertEquals(LeaveStatus.APPROVED, updated.getStatus());
        }

        @Test
        @DisplayName("Manager Approval: Manager CANNOT approve unmanaged peer leave request")
        void testManagerCannotApproveUnmanagedPeerLeave_Returns403() throws Exception {
            mockMvc.perform(put("/api/leave-requests/" + peerLeave.getId() + "/approve")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Manager Team Isolation: Manager CANNOT view another manager's team endpoint")
        void testManagerCannotViewOtherManagerTeam_Returns403() throws Exception {
            // Manager attempting to view admin's direct team
            mockMvc.perform(get("/api/managers/" + adminEmployee.getId() + "/team")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Segregation of Duties: Manager CANNOT approve their own leave request")
        void testManagerSelfApprovalDefense_Returns403() throws Exception {
            // Create pending leave for manager
            LeaveRequest mgrLeave = new LeaveRequest();
            mgrLeave.setEmployee(managerEmployee);
            mgrLeave.setLeaveType(annualLeave);
            mgrLeave.setStartDate(LocalDate.now().plusDays(40));
            mgrLeave.setEndDate(LocalDate.now().plusDays(42));
            mgrLeave.setStatus(LeaveStatus.PENDING);
            mgrLeave.setReason("Manager Personal Leave");
            mgrLeave = leaveRequestRepository.saveAndFlush(mgrLeave);

            mockMvc.perform(put("/api/leave-requests/" + mgrLeave.getId() + "/approve")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Vertical Escalation: Standard employee cannot invoke administrative management APIs")
        void testVerticalPrivilegeEscalationRejected() throws Exception {
            // Lock user
            mockMvc.perform(put("/api/users/" + peerUser.getId() + "/lock")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());

            // Unlock user
            mockMvc.perform(put("/api/users/" + peerUser.getId() + "/unlock")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());

            // Change role
            mockMvc.perform(patch("/api/employees/" + standardEmployee.getId() + "/role")
                            .param("roleName", "ROLE_ADMIN")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());

            // View audit logs
            mockMvc.perform(get("/api/audit-logs")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());

            // View security events
            mockMvc.perform(get("/api/security-events")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());

            // Create department
            DepartmentRequestDTO deptDto = new DepartmentRequestDTO();
            deptDto.setName("Rogue Department " + runId);
            mockMvc.perform(post("/api/departments")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(deptDto)))
                    .andExpect(status().isForbidden());

            // Create leave type
            LeaveTypeRequestDTO typeDto = new LeaveTypeRequestDTO("Rogue Type", "Desc", 10);
            mockMvc.perform(post("/api/leave-types")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(typeDto)))
                    .andExpect(status().isForbidden());

            // Create leave balance directly
            LeaveBalanceRequestDTO balDto = new LeaveBalanceRequestDTO(standardEmployee.getId(), annualLeave.getId(), 50);
            mockMvc.perform(post("/api/leave-balances")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(balDto)))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // SECTION 6: DATABASE DATA OWNERSHIP & QUERY SCOPING AUDIT
    // =========================================================================
    @Nested
    @DisplayName("6. Database Data Ownership & Query Scoping Audit")
    class DatabaseDataOwnershipAudit {

        @Test
        @DisplayName("Database Scoping: findTeamLeaveRequestsByManagerId strictly isolates managed subordinates")
        void testTeamLeaveRequestsQueryScoping() {
            List<LeaveRequest> teamLeaves = leaveRequestRepository.findTeamLeaveRequestsByManagerId(managerEmployee.getId());

            assertNotNull(teamLeaves, "Team leaves query must return non-null list");
            assertTrue(teamLeaves.stream().anyMatch(l -> l.getId().equals(standardLeave.getId())),
                    "Team leaves must include direct report's leave");
            assertFalse(teamLeaves.stream().anyMatch(l -> l.getId().equals(peerLeave.getId())),
                    "Team leaves must NEVER include unmanaged peer's leave");
        }

        @Test
        @DisplayName("Database Scoping: findByEmployee strictly isolates applicant leaves")
        void testApplicantLeavesQueryScoping() {
            List<LeaveRequest> empLeaves = leaveRequestRepository.findByEmployee(standardEmployee);

            assertNotNull(empLeaves);
            assertTrue(empLeaves.stream().allMatch(l -> l.getEmployee().getId().equals(standardEmployee.getId())),
                    "findByEmployee must strictly return leaves matching target employee");
            assertFalse(empLeaves.stream().anyMatch(l -> l.getId().equals(peerLeave.getId())),
                    "findByEmployee must not contain other employees' records");
        }

        @Test
        @DisplayName("Database Scoping: findByEmployee strictly isolates applicant balances")
        void testApplicantBalancesQueryScoping() {
            List<LeaveBalance> balances = leaveBalanceRepository.findByEmployee(standardEmployee);

            assertNotNull(balances);
            assertTrue(balances.stream().allMatch(b -> b.getEmployee().getId().equals(standardEmployee.getId())),
                    "findByEmployee on balances must strictly return caller's own balances");
            assertFalse(balances.stream().anyMatch(b -> b.getEmployee().getId().equals(peerEmployee.getId())),
                    "findByEmployee on balances must not contain peer balances");
        }

        @Test
        @DisplayName("Database Scoping: findByManagerId strictly isolates direct reports")
        void testManagerDirectReportsQueryScoping() {
            List<Employee> reports = employeeRepository.findByManagerId(managerEmployee.getId());

            assertNotNull(reports);
            assertTrue(reports.stream().anyMatch(e -> e.getId().equals(standardEmployee.getId())),
                    "Direct reports must include assigned subordinate");
            assertFalse(reports.stream().anyMatch(e -> e.getId().equals(peerEmployee.getId())),
                    "Direct reports must not include unmanaged peer");
        }

        @Test
        @DisplayName("Authorization Audit Trail: SecurityAuthorizationService persists decisions with full metadata")
        void testAuthorizationDecisionsAuditTrail() throws Exception {
            // Trigger a denied authorization check
            mockMvc.perform(get("/api/employees/" + peerEmployee.getId())
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());

            List<AuthorizationAuditLog> auditLogs = authorizationAuditLogRepository.findAll();
            assertFalse(auditLogs.isEmpty(), "Authorization audit logs must be persisted");

            boolean foundDenial = auditLogs.stream()
                    .anyMatch(log -> log.getEmail().equalsIgnoreCase(employeeUser.getEmail())
                            && "DENIED".equalsIgnoreCase(log.getDecision())
                            && log.getResource().contains("Employee#" + peerEmployee.getId()));

            assertTrue(foundDenial, "Audit trail must record the denied IDOR attempt with caller and target resource");
        }
    }
}
