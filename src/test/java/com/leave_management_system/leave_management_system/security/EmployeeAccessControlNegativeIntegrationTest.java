package com.leave_management_system.leave_management_system.security;

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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Priority 2 — Negative Access Control: EMPLOYEE Cannot Access ADMIN & MANAGER Operations")
public class EmployeeAccessControlNegativeIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RolePermissionIsolationTestHelper permissionHelper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private String employeeToken;
    private Employee employee;
    private User employeeUser;

    private Long managerEmployeeId;
    private Long departmentId;
    private Long leaveTypeId;
    private Long adminEmployeeId;
    private Long peerLeaveRequestId;
    private Long peerLeaveBalanceId;
    private Long adminRoleId;

    @BeforeEach
    public void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        // 1. Establish exact expected canonical role-permission set (no dependency on prior tests)
        permissionHelper.resetToCanonicalPermissions();

        String runId = UUID.randomUUID().toString().substring(0, 8);

        // 2. Setup standard Employee
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseThrow();
        employeeUser = new User();
        employeeUser.setEmail("emp.negative." + runId + "@company.com");
        employeeUser.setPassword(passwordEncoder.encode("UserSecret123!"));
        employeeUser.setActive(true);
        employeeUser.setAccountNonLocked(true);
        employeeUser.setRoles(new HashSet<>(Set.of(employeeRole)));
        employeeUser = userRepository.save(employeeUser);

        employee = new Employee();
        employee.setFirstName("Standard");
        employee.setLastName("Employee");
        employee.setEmployeeCode("EMP-NEG-" + runId);
        employee.setStatus("ACTIVE");
        employee.setSalary(new BigDecimal("65000.00"));
        employee.setUser(employeeUser);
        employee = employeeRepository.save(employee);
        employeeUser.setEmployee(employee);
        userRepository.save(employeeUser);

        employeeToken = generateToken(employeeUser, employeeRole);

        // 3. Setup Admin User & Employee
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
        adminRoleId = adminRole.getId();
        User adminUser = new User();
        adminUser.setEmail("admin.negative." + runId + "@company.com");
        adminUser.setPassword(passwordEncoder.encode("AdminSecret123!"));
        adminUser.setActive(true);
        adminUser.setAccountNonLocked(true);
        adminUser.setRoles(new HashSet<>(Set.of(adminRole)));
        adminUser = userRepository.save(adminUser);

        Employee adminEmp = new Employee();
        adminEmp.setFirstName("Admin");
        adminEmp.setLastName("Super");
        adminEmp.setEmployeeCode("ADM-NEG-" + runId);
        adminEmp.setStatus("ACTIVE");
        adminEmp.setUser(adminUser);
        adminEmp = employeeRepository.save(adminEmp);
        adminEmployeeId = adminEmp.getId();

        // 4. Setup Manager User & Department
        Role managerRole = roleRepository.findByName("MANAGER").orElseThrow();
        User managerUser = new User();
        managerUser.setEmail("mgr.negative." + runId + "@company.com");
        managerUser.setPassword(passwordEncoder.encode("MgrSecret123!"));
        managerUser.setActive(true);
        managerUser.setAccountNonLocked(true);
        managerUser.setRoles(new HashSet<>(Set.of(managerRole)));
        managerUser = userRepository.save(managerUser);

        Department dept = new Department();
        dept.setName("Engineering Neg " + runId);
        dept = departmentRepository.save(dept);
        departmentId = dept.getId();

        Employee mgrEmp = new Employee();
        mgrEmp.setFirstName("Lead");
        mgrEmp.setLastName("Manager");
        mgrEmp.setEmployeeCode("MGR-NEG-" + runId);
        mgrEmp.setStatus("ACTIVE");
        mgrEmp.setDepartment(dept);
        mgrEmp.setUser(managerUser);
        mgrEmp = employeeRepository.save(mgrEmp);
        managerEmployeeId = mgrEmp.getId();

        dept.setManager(mgrEmp);
        departmentRepository.save(dept);

        // 5. Setup Leave Type & Peer Leave Request
        LeaveType lt = new LeaveType();
        lt.setName("Annual Vacation " + runId);
        lt.setDefaultDays(20);
        lt.setActive(true);
        lt = leaveTypeRepository.save(lt);
        leaveTypeId = lt.getId();

        LeaveBalance peerBalance = new LeaveBalance();
        peerBalance.setEmployee(mgrEmp);
        peerBalance.setLeaveType(lt);
        peerBalance.setTotal(20);
        peerBalance.setAvailable(18);
        peerBalance.setUsed(2);
        peerBalance.setYear(LocalDate.now().getYear());
        peerBalance = leaveBalanceRepository.save(peerBalance);
        peerLeaveBalanceId = peerBalance.getId();

        LeaveRequest peerLeave = new LeaveRequest();
        peerLeave.setEmployee(mgrEmp);
        peerLeave.setLeaveType(lt);
        peerLeave.setStartDate(LocalDate.now().plusDays(5));
        peerLeave.setEndDate(LocalDate.now().plusDays(7));
        peerLeave.setStatus(LeaveStatus.PENDING);
        peerLeave.setReason("Manager offsite");
        peerLeave = leaveRequestRepository.save(peerLeave);
        peerLeaveRequestId = peerLeave.getId();
    }

    @AfterEach
    public void tearDown() {
        // Restore role permissions after each test to prevent cross-test leakage
        permissionHelper.resetToCanonicalPermissions();
        permissionHelper.assertEmployeeCannotAccessAdminOrManagerPermissions();
        SecurityContextHolder.clearContext();
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

    // =========================================================================
    // SECTION 1: EMPLOYEE CANNOT ACCESS ADMIN OPERATIONS (HTTP 403)
    // =========================================================================
    @Nested
    @DisplayName("1. Admin Operations Denied to EMPLOYEE")
    class AdminOperationsDenied {

        @Test
        @DisplayName("EMPLOYEE cannot create new employees (POST /api/employees)")
        void employeeCannotCreateEmployee_Returns403() throws Exception {
            EmployeeRequestDTO dto = new EmployeeRequestDTO();
            dto.setFirstName("Hacked");
            dto.setLastName("User");
            dto.setEmail("hacked.user@example.com");
            dto.setEmployeeCode("EMP-HACK-01");
            dto.setStatus("ACTIVE");
            dto.setPhone("+1-555-123456");
            dto.setDepartmentId(departmentId);
            dto.setSalary(new BigDecimal("99000.00"));

            mockMvc.perform(post("/api/employees")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot update another employee profile (PUT /api/employees/{id})")
        void employeeCannotUpdateOtherEmployee_Returns403() throws Exception {
            EmployeeRequestDTO dto = new EmployeeRequestDTO();
            dto.setFirstName("Compromised");
            dto.setLastName("Target");
            dto.setEmail("compromised.target@example.com");
            dto.setEmployeeCode("EMP-COMP-01");
            dto.setStatus("ACTIVE");
            dto.setPhone("+1-555-654321");
            dto.setDepartmentId(departmentId);
            dto.setSalary(new BigDecimal("88000.00"));

            mockMvc.perform(put("/api/employees/" + adminEmployeeId)
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot deactivate employees (DELETE /api/employees/{id})")
        void employeeCannotDeactivateEmployee_Returns403() throws Exception {
            mockMvc.perform(delete("/api/employees/" + managerEmployeeId)
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot view all employees roster (GET /api/employees)")
        void employeeCannotViewAllEmployees_Returns403() throws Exception {
            mockMvc.perform(get("/api/employees")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot create departments (POST /api/departments)")
        void employeeCannotCreateDepartment_Returns403() throws Exception {
            DepartmentRequestDTO dto = new DepartmentRequestDTO();
            dto.setName("Rogue Department");

            mockMvc.perform(post("/api/departments")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot update departments (PUT /api/departments/{id})")
        void employeeCannotUpdateDepartment_Returns403() throws Exception {
            DepartmentRequestDTO dto = new DepartmentRequestDTO();
            dto.setName("Modified Dept");

            mockMvc.perform(put("/api/departments/" + departmentId)
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot delete departments (DELETE /api/departments/{id})")
        void employeeCannotDeleteDepartment_Returns403() throws Exception {
            mockMvc.perform(delete("/api/departments/" + departmentId)
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot assign department manager (PATCH /api/departments/{id}/manager)")
        void employeeCannotAssignDepartmentManager_Returns403() throws Exception {
            mockMvc.perform(patch("/api/departments/" + departmentId + "/manager")
                            .header("Authorization", "Bearer " + employeeToken)
                            .param("managerId", employee.getId().toString()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot create leave types (POST /api/leave-types)")
        void employeeCannotCreateLeaveType_Returns403() throws Exception {
            LeaveTypeRequestDTO dto = new LeaveTypeRequestDTO();
            dto.setName("Rogue Sabbatical");
            dto.setDefaultDays(60);

            mockMvc.perform(post("/api/leave-types")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot update leave types (PUT /api/leave-types/{id})")
        void employeeCannotUpdateLeaveType_Returns403() throws Exception {
            LeaveTypeRequestDTO dto = new LeaveTypeRequestDTO();
            dto.setName("Updated Leave Type");
            dto.setDefaultDays(30);

            mockMvc.perform(put("/api/leave-types/" + leaveTypeId)
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot delete leave types (DELETE /api/leave-types/{id})")
        void employeeCannotDeleteLeaveType_Returns403() throws Exception {
            mockMvc.perform(delete("/api/leave-types/" + leaveTypeId)
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot manage leave balances for others (POST /api/leave-balances)")
        void employeeCannotCreateLeaveBalanceForOthers_Returns403() throws Exception {
            LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
            dto.setEmployeeId(managerEmployeeId);
            dto.setLeaveTypeId(leaveTypeId);
            dto.setTotal(30);
            dto.setYear(LocalDate.now().getYear());

            mockMvc.perform(post("/api/leave-balances")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot adjust leave balances (PUT /api/leave-balances/{id})")
        void employeeCannotAdjustLeaveBalances_Returns403() throws Exception {
            LeaveBalanceRequestDTO dto = new LeaveBalanceRequestDTO();
            dto.setEmployeeId(employee.getId());
            dto.setLeaveTypeId(leaveTypeId);
            dto.setTotal(30);
            dto.setAvailable(30);
            dto.setYear(LocalDate.now().getYear());

            mockMvc.perform(put("/api/leave-balances/" + peerLeaveBalanceId)
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot initialize leave balances (POST /api/leave-balances/employee/{id}/initialize)")
        void employeeCannotInitializeLeaveBalances_Returns403() throws Exception {
            mockMvc.perform(post("/api/leave-balances/employee/" + employee.getId() + "/initialize")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot access roles management (GET /api/roles)")
        void employeeCannotAccessRoles_Returns403() throws Exception {
            mockMvc.perform(get("/api/roles")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot modify role permissions (PUT /api/roles/{id}/permissions)")
        void employeeCannotModifyRolePermissions_Returns403() throws Exception {
            mockMvc.perform(put("/api/roles/" + adminRoleId + "/permissions")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(List.of(1L))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot lock user accounts (PUT /api/users/{id}/lock)")
        void employeeCannotLockUserAccounts_Returns403() throws Exception {
            mockMvc.perform(put("/api/users/" + employeeUser.getId() + "/lock")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot unlock user accounts (PUT /api/users/{id}/unlock)")
        void employeeCannotUnlockUserAccounts_Returns403() throws Exception {
            mockMvc.perform(put("/api/users/" + employeeUser.getId() + "/unlock")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot view audit logs (GET /api/audit-logs)")
        void employeeCannotViewAuditLogs_Returns403() throws Exception {
            mockMvc.perform(get("/api/audit-logs")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot view authorization audit logs (GET /api/authorization-audits)")
        void employeeCannotViewAuthorizationAudits_Returns403() throws Exception {
            mockMvc.perform(get("/api/authorization-audits")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot resolve security events (POST /api/security-events/{id}/resolve)")
        void employeeCannotResolveSecurityEvents_Returns403() throws Exception {
            mockMvc.perform(post("/api/security-events/1/resolve")
                            .header("Authorization", "Bearer " + employeeToken)
                            .param("resolution", "Dismissed"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot search all leave requests across organization (GET /api/leave-requests)")
        void employeeCannotSearchAllLeaveRequests_Returns403() throws Exception {
            mockMvc.perform(get("/api/leave-requests")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // SECTION 2: EMPLOYEE CANNOT ACCESS MANAGER OPERATIONS (HTTP 403)
    // =========================================================================
    @Nested
    @DisplayName("2. Manager Operations Denied to EMPLOYEE")
    class ManagerOperationsDenied {

        @Test
        @DisplayName("EMPLOYEE cannot approve leave requests via PUT (/api/leave-requests/{id}/approve)")
        void employeeCannotApproveLeaveRequestViaPut_Returns403() throws Exception {
            mockMvc.perform(put("/api/leave-requests/" + peerLeaveRequestId + "/approve")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot approve leave requests via PATCH (/api/leave-requests/{id}/approve)")
        void employeeCannotApproveLeaveRequestViaPatch_Returns403() throws Exception {
            mockMvc.perform(patch("/api/leave-requests/" + peerLeaveRequestId + "/approve")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot reject leave requests via PUT (/api/leave-requests/{id}/reject)")
        void employeeCannotRejectLeaveRequestViaPut_Returns403() throws Exception {
            mockMvc.perform(put("/api/leave-requests/" + peerLeaveRequestId + "/reject")
                            .header("Authorization", "Bearer " + employeeToken)
                            .param("rejectionReason", "Denied by peer"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot reject leave requests via PATCH (/api/leave-requests/{id}/reject)")
        void employeeCannotRejectLeaveRequestViaPatch_Returns403() throws Exception {
            mockMvc.perform(patch("/api/leave-requests/" + peerLeaveRequestId + "/reject")
                            .header("Authorization", "Bearer " + employeeToken)
                            .param("rejectionReason", "Denied by peer"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot view manager's team endpoint (/api/managers/{id}/team)")
        void employeeCannotViewManagerTeam_Returns403() throws Exception {
            mockMvc.perform(get("/api/managers/" + managerEmployeeId + "/team")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot view manager's team leave requests (/api/managers/me/leave-requests)")
        void employeeCannotViewManagerTeamLeaveRequests_Returns403() throws Exception {
            mockMvc.perform(get("/api/managers/me/leave-requests")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot view manager me team roster (/api/managers/me/team)")
        void employeeCannotViewManagerMeTeam_Returns403() throws Exception {
            mockMvc.perform(get("/api/managers/me/team")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("EMPLOYEE cannot view manager's team hierarchy (/api/managers/{id}/team-hierarchy)")
        void employeeCannotViewManagerTeamHierarchy_Returns403() throws Exception {
            mockMvc.perform(get("/api/managers/" + managerEmployeeId + "/team-hierarchy")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());
        }
    }
}
