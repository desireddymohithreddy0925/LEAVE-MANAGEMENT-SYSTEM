package com.leave_management_system.leave_management_system.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.CryptoRequestDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeProfileUpdateDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.service.AuthorizationAuditService;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
public class DataAccessSecurityAuditIntegrationTest {

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
    private AuthorizationAuditService authorizationAuditService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private String runId;

    // Personas:
    // 1. Admin
    private User adminUser;
    private Employee adminEmployee;
    private String adminToken;

    // 2. Department A: Manager 1 & Subordinate 1
    private Department departmentA;
    private User manager1User;
    private Employee manager1Employee;
    private String manager1Token;

    private User sub1User;
    private Employee sub1Employee;
    private String sub1Token;

    // 3. Department B: Manager 2 & Subordinate 2
    private Department departmentB;
    private User manager2User;
    private Employee manager2Employee;
    private String manager2Token;

    private User sub2User;
    private Employee sub2Employee;
    private String sub2Token;

    // Shared resources
    private LeaveType annualLeaveType;
    private LeaveBalance sub1Balance;
    private LeaveBalance sub2Balance;
    private LeaveBalance manager1Balance;
    private LeaveRequest sub1LeaveRequest;
    private LeaveRequest sub2LeaveRequest;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        cleanup();

        runId = UUID.randomUUID().toString().substring(0, 8);

        // 1. Setup Roles & Standard Enterprise Permissions
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        Role managerRole = roleRepository.findByName("MANAGER").orElseGet(() -> roleRepository.save(new Role("MANAGER")));
        Role empRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));

        Permission permEmpView = permissionRepository.findByName("EMPLOYEE_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("EMPLOYEE_VIEW", "View employee profiles")));
        Permission permEmpUpdate = permissionRepository.findByName("EMPLOYEE_UPDATE")
                .orElseGet(() -> permissionRepository.save(new Permission("EMPLOYEE_UPDATE", "Update employee records")));
        Permission permEmpDeactivate = permissionRepository.findByName("EMPLOYEE_DEACTIVATE")
                .orElseGet(() -> permissionRepository.save(new Permission("EMPLOYEE_DEACTIVATE", "Deactivate employee records")));
        Permission permDeptView = permissionRepository.findByName("DEPARTMENT_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("DEPARTMENT_VIEW", "View departments")));
        Permission permDeptManage = permissionRepository.findByName("DEPARTMENT_MANAGE")
                .orElseGet(() -> permissionRepository.save(new Permission("DEPARTMENT_MANAGE", "Manage departments")));
        Permission permLeaveViewTeam = permissionRepository.findByName("LEAVE_VIEW_TEAM")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_VIEW_TEAM", "View team leave requests")));
        Permission permLeaveViewAll = permissionRepository.findByName("LEAVE_VIEW_ALL")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_VIEW_ALL", "View all leave requests")));
        Permission permLeaveCreate = permissionRepository.findByName("LEAVE_CREATE")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_CREATE", "Create leave request")));
        Permission permLeaveApprove = permissionRepository.findByName("LEAVE_APPROVE")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_APPROVE", "Approve leave requests")));
        Permission permLeaveReject = permissionRepository.findByName("LEAVE_REJECT")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_REJECT", "Reject leave requests")));
        Permission permLeaveCancel = permissionRepository.findByName("LEAVE_CANCEL")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_CANCEL", "Cancel leave requests")));
        Permission permBalView = permissionRepository.findByName("LEAVE_BALANCE_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_BALANCE_VIEW", "View leave balances")));
        Permission permBalManage = permissionRepository.findByName("LEAVE_BALANCE_MANAGE")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_BALANCE_MANAGE", "Manage leave balances")));
        Permission permAuditView = permissionRepository.findByName("AUDIT_LOG_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("AUDIT_LOG_VIEW", "View audit logs")));
        Permission permUserLock = permissionRepository.findByName("USER_LOCK_MANAGE")
                .orElseGet(() -> permissionRepository.save(new Permission("USER_LOCK_MANAGE", "Lock and unlock accounts")));
        Permission permSecEvent = permissionRepository.findByName("SECURITY_EVENT_MANAGE")
                .orElseGet(() -> permissionRepository.save(new Permission("SECURITY_EVENT_MANAGE", "Manage security events")));
        Permission permRoleManage = permissionRepository.findByName("ROLE_PERMISSION_MANAGE")
                .orElseGet(() -> permissionRepository.save(new Permission("ROLE_PERMISSION_MANAGE", "Manage roles & permissions")));

        adminRole.getPermissions().addAll(List.of(
                permEmpView, permEmpUpdate, permEmpDeactivate, permDeptView, permDeptManage,
                permLeaveViewTeam, permLeaveViewAll, permLeaveCreate, permLeaveApprove, permLeaveReject, permLeaveCancel,
                permBalView, permBalManage, permAuditView, permUserLock, permSecEvent, permRoleManage
        ));
        roleRepository.save(adminRole);

        managerRole.getPermissions().addAll(List.of(
                permEmpView, permDeptView, permLeaveViewTeam, permLeaveCreate, permLeaveApprove, permLeaveReject, permLeaveCancel
        ));
        roleRepository.save(managerRole);

        empRole.getPermissions().addAll(List.of(
                permLeaveCreate, permLeaveCancel, permBalView
        ));
        roleRepository.save(empRole);

        // 2. Setup Departments
        departmentA = new Department();
        departmentA.setName("Engineering-" + runId);
        departmentA = departmentRepository.save(departmentA);

        departmentB = new Department();
        departmentB.setName("Marketing-" + runId);
        departmentB = departmentRepository.save(departmentB);

        // 3. Setup Admin User & Employee (not assigned to departmentA or B to keep manager teams isolated)
        adminUser = new User();
        adminUser.setEmail("audit-admin-" + runId + "@company.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);

        adminEmployee = new Employee();
        adminEmployee.setFirstName("Super");
        adminEmployee.setLastName("Admin");
        adminEmployee.setEmployeeCode("ADM-" + runId);
        adminEmployee.setStatus("ACTIVE");
        adminEmployee.setUser(adminUser);
        adminEmployee = employeeRepository.save(adminEmployee);

        List<SimpleGrantedAuthority> adminAuthorities = new ArrayList<>();
        adminAuthorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        adminRole.getPermissions().forEach(p -> adminAuthorities.add(new SimpleGrantedAuthority(p.getName())));
        UserDetails adminDetails = new org.springframework.security.core.userdetails.User(
                adminUser.getEmail(), adminUser.getPassword(), adminAuthorities);
        adminToken = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities()));

        // 4. Setup Manager 1 (Engineering)
        manager1User = new User();
        manager1User.setEmail("audit-mgr1-" + runId + "@company.com");
        manager1User.setPassword(passwordEncoder.encode("MgrPass123!"));
        manager1User.setActive(true);
        manager1User.getRoles().add(managerRole);
        manager1User = userRepository.save(manager1User);

        manager1Employee = new Employee();
        manager1Employee.setFirstName("Alice");
        manager1Employee.setLastName("EngManager");
        manager1Employee.setEmployeeCode("MGR1-" + runId);
        manager1Employee.setStatus("ACTIVE");
        manager1Employee.setUser(manager1User);
        manager1Employee.setDepartment(departmentA);
        manager1Employee = employeeRepository.save(manager1Employee);
        departmentA.setManager(manager1Employee);
        departmentA = departmentRepository.save(departmentA);

        List<SimpleGrantedAuthority> mgr1Authorities = new ArrayList<>();
        mgr1Authorities.add(new SimpleGrantedAuthority("ROLE_MANAGER"));
        managerRole.getPermissions().forEach(p -> mgr1Authorities.add(new SimpleGrantedAuthority(p.getName())));
        UserDetails mgr1Details = new org.springframework.security.core.userdetails.User(
                manager1User.getEmail(), manager1User.getPassword(), mgr1Authorities);
        manager1Token = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(mgr1Details, null, mgr1Details.getAuthorities()));

        // 5. Setup Subordinate 1 (Engineering, reports to Manager 1)
        sub1User = new User();
        sub1User.setEmail("audit-sub1-" + runId + "@company.com");
        sub1User.setPassword(passwordEncoder.encode("SubPass123!"));
        sub1User.setActive(true);
        sub1User.getRoles().add(empRole);
        sub1User = userRepository.save(sub1User);

        sub1Employee = new Employee();
        sub1Employee.setFirstName("Bob");
        sub1Employee.setLastName("Dev");
        sub1Employee.setEmployeeCode("DEV1-" + runId);
        sub1Employee.setStatus("ACTIVE");
        sub1Employee.setUser(sub1User);
        sub1Employee.setDepartment(departmentA);
        sub1Employee.setManager(manager1Employee);
        sub1Employee = employeeRepository.save(sub1Employee);

        List<SimpleGrantedAuthority> sub1Authorities = new ArrayList<>();
        sub1Authorities.add(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        empRole.getPermissions().forEach(p -> sub1Authorities.add(new SimpleGrantedAuthority(p.getName())));
        UserDetails sub1Details = new org.springframework.security.core.userdetails.User(
                sub1User.getEmail(), sub1User.getPassword(), sub1Authorities);
        sub1Token = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(sub1Details, null, sub1Details.getAuthorities()));

        // 6. Setup Manager 2 (Marketing)
        manager2User = new User();
        manager2User.setEmail("audit-mgr2-" + runId + "@company.com");
        manager2User.setPassword(passwordEncoder.encode("MgrPass123!"));
        manager2User.setActive(true);
        manager2User.getRoles().add(managerRole);
        manager2User = userRepository.save(manager2User);

        manager2Employee = new Employee();
        manager2Employee.setFirstName("Carol");
        manager2Employee.setLastName("MktManager");
        manager2Employee.setEmployeeCode("MGR2-" + runId);
        manager2Employee.setStatus("ACTIVE");
        manager2Employee.setUser(manager2User);
        manager2Employee.setDepartment(departmentB);
        manager2Employee = employeeRepository.save(manager2Employee);
        departmentB.setManager(manager2Employee);
        departmentB = departmentRepository.save(departmentB);

        List<SimpleGrantedAuthority> mgr2Authorities = new ArrayList<>();
        mgr2Authorities.add(new SimpleGrantedAuthority("ROLE_MANAGER"));
        managerRole.getPermissions().forEach(p -> mgr2Authorities.add(new SimpleGrantedAuthority(p.getName())));
        UserDetails mgr2Details = new org.springframework.security.core.userdetails.User(
                manager2User.getEmail(), manager2User.getPassword(), mgr2Authorities);
        manager2Token = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(mgr2Details, null, mgr2Details.getAuthorities()));

        // 7. Setup Subordinate 2 (Marketing, reports to Manager 2)
        sub2User = new User();
        sub2User.setEmail("audit-sub2-" + runId + "@company.com");
        sub2User.setPassword(passwordEncoder.encode("SubPass123!"));
        sub2User.setActive(true);
        sub2User.getRoles().add(empRole);
        sub2User = userRepository.save(sub2User);

        sub2Employee = new Employee();
        sub2Employee.setFirstName("Dave");
        sub2Employee.setLastName("Marketer");
        sub2Employee.setEmployeeCode("MKT1-" + runId);
        sub2Employee.setStatus("ACTIVE");
        sub2Employee.setUser(sub2User);
        sub2Employee.setDepartment(departmentB);
        sub2Employee.setManager(manager2Employee);
        sub2Employee = employeeRepository.save(sub2Employee);

        List<SimpleGrantedAuthority> sub2Authorities = new ArrayList<>();
        sub2Authorities.add(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        empRole.getPermissions().forEach(p -> sub2Authorities.add(new SimpleGrantedAuthority(p.getName())));
        UserDetails sub2Details = new org.springframework.security.core.userdetails.User(
                sub2User.getEmail(), sub2User.getPassword(), sub2Authorities);
        sub2Token = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(sub2Details, null, sub2Details.getAuthorities()));

        // 8. Setup Shared Leave Type and Leave Balances
        annualLeaveType = new LeaveType();
        annualLeaveType.setName("Annual Vacation-" + runId);
        annualLeaveType.setDefaultDays(25);
        annualLeaveType.setActive(true);
        annualLeaveType = leaveTypeRepository.save(annualLeaveType);

        int currentYear = LocalDate.now().getYear();

        sub1Balance = new LeaveBalance();
        sub1Balance.setEmployee(sub1Employee);
        sub1Balance.setLeaveType(annualLeaveType);
        sub1Balance.setYear(currentYear);
        sub1Balance.setTotal(25);
        sub1Balance.setUsed(0);
        sub1Balance.setAvailable(25);
        sub1Balance = leaveBalanceRepository.save(sub1Balance);

        sub2Balance = new LeaveBalance();
        sub2Balance.setEmployee(sub2Employee);
        sub2Balance.setLeaveType(annualLeaveType);
        sub2Balance.setYear(currentYear);
        sub2Balance.setTotal(25);
        sub2Balance.setUsed(0);
        sub2Balance.setAvailable(25);
        sub2Balance = leaveBalanceRepository.save(sub2Balance);

        manager1Balance = new LeaveBalance();
        manager1Balance.setEmployee(manager1Employee);
        manager1Balance.setLeaveType(annualLeaveType);
        manager1Balance.setYear(currentYear);
        manager1Balance.setTotal(30);
        manager1Balance.setUsed(0);
        manager1Balance.setAvailable(30);
        manager1Balance = leaveBalanceRepository.save(manager1Balance);

        // 9. Setup Baseline Leave Requests (Mon-Thu to ensure working days within current year)
        LocalDate monday1 = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate thursday1 = monday1.plusDays(3);

        LocalDate monday2 = monday1.plusDays(7);
        LocalDate thursday2 = monday2.plusDays(3);

        sub1LeaveRequest = new LeaveRequest();
        sub1LeaveRequest.setEmployee(sub1Employee);
        sub1LeaveRequest.setLeaveType(annualLeaveType);
        sub1LeaveRequest.setStartDate(monday1);
        sub1LeaveRequest.setEndDate(thursday1);
        sub1LeaveRequest.setReason("Sub1 Scheduled Vacation");
        sub1LeaveRequest.setStatus(LeaveStatus.PENDING);
        sub1LeaveRequest = leaveRequestRepository.save(sub1LeaveRequest);

        sub2LeaveRequest = new LeaveRequest();
        sub2LeaveRequest.setEmployee(sub2Employee);
        sub2LeaveRequest.setLeaveType(annualLeaveType);
        sub2LeaveRequest.setStartDate(monday2);
        sub2LeaveRequest.setEndDate(thursday2);
        sub2LeaveRequest.setReason("Sub2 Scheduled Vacation");
        sub2LeaveRequest.setStatus(LeaveStatus.PENDING);
        sub2LeaveRequest = leaveRequestRepository.save(sub2LeaveRequest);
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

        // Safely detach all department and manager references
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
            if (dirty) {
                employeeRepository.save(emp);
            }
        });

        if (departmentA != null && departmentA.getId() != null) {
            departmentRepository.findById(departmentA.getId()).ifPresent(d -> {
                d.setManager(null);
                departmentRepository.save(d);
            });
        }
        if (departmentB != null && departmentB.getId() != null) {
            departmentRepository.findById(departmentB.getId()).ifPresent(d -> {
                d.setManager(null);
                departmentRepository.save(d);
            });
        }

        if (sub1Employee != null && sub1Employee.getId() != null) {
            employeeRepository.deleteById(sub1Employee.getId());
            sub1Employee = null;
        }
        if (sub2Employee != null && sub2Employee.getId() != null) {
            employeeRepository.deleteById(sub2Employee.getId());
            sub2Employee = null;
        }
        if (manager1Employee != null && manager1Employee.getId() != null) {
            employeeRepository.deleteById(manager1Employee.getId());
            manager1Employee = null;
        }
        if (manager2Employee != null && manager2Employee.getId() != null) {
            employeeRepository.deleteById(manager2Employee.getId());
            manager2Employee = null;
        }
        if (adminEmployee != null && adminEmployee.getId() != null) {
            employeeRepository.deleteById(adminEmployee.getId());
            adminEmployee = null;
        }

        if (departmentA != null && departmentA.getId() != null) {
            departmentRepository.deleteById(departmentA.getId());
            departmentA = null;
        }
        if (departmentB != null && departmentB.getId() != null) {
            departmentRepository.deleteById(departmentB.getId());
            departmentB = null;
        }

        if (sub1User != null && sub1User.getId() != null) {
            userRepository.deleteById(sub1User.getId());
            sub1User = null;
        }
        if (sub2User != null && sub2User.getId() != null) {
            userRepository.deleteById(sub2User.getId());
            sub2User = null;
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
    // SECTION 1: EMPLOYEE DATA ACCESS & IDOR AUDITING
    // =========================================================================

    @Test
    @DisplayName("1. Employee Access Audit: Employee can view own profile and hierarchy, but is denied accessing peer employee data")
    public void test1_Employee_CanAccessOwnProfileAndHierarchy_AndCannotAccessOtherEmployeeData() throws Exception {
        // A. Employee 1 can access own profile via /me
        mockMvc.perform(get("/api/employees/me")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(sub1Employee.getId().intValue())))
                .andExpect(jsonPath("$.firstName", is("Bob")))
                .andExpect(jsonPath("$.email", is(sub1User.getEmail())));

        // B. Employee 1 can access own employee record by explicit ID
        mockMvc.perform(get("/api/employees/" + sub1Employee.getId())
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(sub1Employee.getId().intValue())));

        // C. Employee 1 can access own user account record
        mockMvc.perform(get("/api/users/" + sub1User.getId())
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(sub1User.getId().intValue())))
                .andExpect(jsonPath("$.email", is(sub1User.getEmail())));

        // D. Employee 1 can view own reporting hierarchy
        mockMvc.perform(get("/api/employees/me/hierarchy")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.id", is(sub1Employee.getId().intValue())));

        // E. Horizontal IDOR Attack: Employee 1 attempts to view Employee 2's profile
        mockMvc.perform(get("/api/employees/" + sub2Employee.getId())
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isForbidden());

        // F. Horizontal IDOR Attack: Employee 1 attempts to view Employee 2's reporting hierarchy
        mockMvc.perform(get("/api/employees/" + sub2Employee.getId() + "/hierarchy")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isForbidden());

        // G. Verify authorization audit log captured the denial
        boolean denialCaptured = authorizationAuditLogRepository.findAll().stream()
                .anyMatch(log -> sub1User.getEmail().equalsIgnoreCase(log.getEmail())
                        && "DENIED".equals(log.getDecision())
                        && log.getResource().contains("Employee#" + sub2Employee.getId()));
        assertTrue(denialCaptured, "Audit trail must record DENIED authorization for horizontal profile access attempt");
    }

    @Test
    @DisplayName("2. Employee Mutation Audit: Employee can update own basic profile, but is denied modifying peer profiles or administrative status")
    public void test2_Employee_CanUpdateOwnProfile_AndCannotUpdateOtherEmployeeProfileOrPerformAdminActions() throws Exception {
        // A. Employee 1 updates own allowed profile fields
        EmployeeProfileUpdateDTO updateDTO = new EmployeeProfileUpdateDTO();
        updateDTO.setFirstName("Bobby");
        updateDTO.setLastName("Developer");
        updateDTO.setPhone("+1234567890");

        mockMvc.perform(put("/api/employees/" + sub1Employee.getId() + "/profile")
                        .header("Authorization", "Bearer " + sub1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Bobby")))
                .andExpect(jsonPath("$.lastName", is("Developer")));

        // B. Horizontal IDOR: Employee 1 attempts to update Employee 2's profile
        mockMvc.perform(put("/api/employees/" + sub2Employee.getId() + "/profile")
                        .header("Authorization", "Bearer " + sub1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isForbidden());

        // C. Vertical Escalation: Employee 1 attempts administrative employee update (valid DTO payload, denied by RBAC)
        EmployeeRequestDTO adminReq = new EmployeeRequestDTO();
        adminReq.setFirstName("Bobby");
        adminReq.setLastName("Developer");
        adminReq.setEmail(sub1User.getEmail());
        adminReq.setEmployeeCode("DEV1-" + runId);
        adminReq.setStatus("ACTIVE");
        adminReq.setSalary(new java.math.BigDecimal("75000.00"));
        adminReq.setDepartmentId(departmentA.getId());

        mockMvc.perform(put("/api/employees/" + sub1Employee.getId())
                        .header("Authorization", "Bearer " + sub1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(adminReq)))
                .andExpect(status().isForbidden());

        // D. Vertical Escalation: Employee 1 attempts to change employee status
        mockMvc.perform(patch("/api/employees/" + sub1Employee.getId() + "/status?status=INACTIVE")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isForbidden());

        // E. Vertical Escalation: Employee 1 attempts to delete an employee
        mockMvc.perform(delete("/api/employees/" + sub2Employee.getId())
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("3. Employee Balance Audit: Employee can view own leave balances, but is denied viewing peer balances or all company balances")
    public void test3_Employee_CanViewOwnBalances_AndCannotViewOtherEmployeeBalancesOrAllBalances() throws Exception {
        // A. Employee 1 retrieves own balances via /me
        mockMvc.perform(get("/api/leave-balances/me")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].employee.id", is(sub1Employee.getId().intValue())))
                .andExpect(jsonPath("$[0].available", is(25)));

        // B. Employee 1 retrieves own balances by employee ID
        mockMvc.perform(get("/api/leave-balances/employee/" + sub1Employee.getId())
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employee.id", is(sub1Employee.getId().intValue())));

        // C. Horizontal IDOR: Employee 1 attempts to view Employee 2's leave balances
        mockMvc.perform(get("/api/leave-balances/employee/" + sub2Employee.getId())
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isForbidden());

        // D. Vertical Escalation: Employee 1 attempts to view all company leave balances
        mockMvc.perform(get("/api/leave-balances")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isForbidden());

        // E. Vertical Escalation: Employee 1 attempts to view specific balance by internal balance ID
        mockMvc.perform(get("/api/leave-balances/" + sub2Balance.getId())
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4. Employee Leave Request Audit: Employee can create and cancel own leave, but cannot view or cancel peer leave requests")
    public void test4_Employee_CanApplyAndCancelOwnLeave_AndCannotViewOrCancelOtherEmployeeLeave() throws Exception {
        LocalDate start = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusDays(21);
        LocalDate end = start.plusDays(3);

        // A. Employee 1 applies for leave; even if passing spoofed employeeId=sub2Employee.getId(),
        // the backend forces employeeId to authenticated user
        LeaveRequestDTO newRequest = new LeaveRequestDTO();
        newRequest.setEmployeeId(sub2Employee.getId()); // Intentional spoof attempt
        newRequest.setLeaveTypeId(annualLeaveType.getId());
        newRequest.setStartDate(start);
        newRequest.setEndDate(end);
        newRequest.setReason("Family Vacation");

        String responseStr = mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", "Bearer " + sub1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employee.id", is(sub1Employee.getId().intValue()))) // Overridden to sub1!
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andReturn().getResponse().getContentAsString();

        Long createdRequestId = objectMapper.readTree(responseStr).get("id").asLong();

        // B. Employee 1 views own requests via /me
        mockMvc.perform(get("/api/leave-requests/me")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));

        // C. Employee 1 views own specific leave request
        mockMvc.perform(get("/api/leave-requests/" + createdRequestId)
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(createdRequestId.intValue())))
                .andExpect(jsonPath("$.employee.id", is(sub1Employee.getId().intValue())));

        // D. Horizontal IDOR: Employee 1 attempts to view Employee 2's leave request
        mockMvc.perform(get("/api/leave-requests/" + sub2LeaveRequest.getId())
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isForbidden());

        // E. Horizontal IDOR: Employee 1 attempts to view Employee 2's leave requests list
        mockMvc.perform(get("/api/leave-requests/employee/" + sub2Employee.getId())
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isForbidden());

        // F. Employee 1 can cancel own pending leave request
        mockMvc.perform(put("/api/leave-requests/" + createdRequestId + "/cancel")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        // G. Horizontal IDOR: Employee 1 attempts to cancel Employee 2's leave request
        mockMvc.perform(put("/api/leave-requests/" + sub2LeaveRequest.getId() + "/cancel")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("5. Employee Vertical Escalation Audit: Employee is strictly prohibited from accessing manager and admin governance endpoints")
    public void test5_Employee_VerticalPrivilegeEscalation_CannotAccessManagerOrAdminEndpoints() throws Exception {
        // A. Manager Team Endpoints
        mockMvc.perform(get("/api/managers/me/team")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/managers/me/leave-requests")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isForbidden());

        // B. Leave Approval / Rejection Endpoints
        mockMvc.perform(put("/api/leave-requests/" + sub1LeaveRequest.getId() + "/approve")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/leave-requests/" + sub1LeaveRequest.getId() + "/reject")
                        .header("Authorization", "Bearer " + sub1Token)
                        .param("reason", "Unauthorized rejection attempt"))
                .andExpect(status().isForbidden());

        // C. User Governance Endpoints
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/users/" + sub2User.getId() + "/lock")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isForbidden());

        // D. Audit & Security Event Logs
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/authorization-audits")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/security-events")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isForbidden());

        // E. Cryptographic Operations
        CryptoRequestDTO cryptoReq = new CryptoRequestDTO();
        cryptoReq.setData("topSecretData");
        mockMvc.perform(post("/api/sensitive-data/encrypt")
                        .header("Authorization", "Bearer " + sub1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cryptoReq)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // SECTION 2: MANAGER DATA ACCESS, HIERARCHY & DUAL APPROVAL AUDITING
    // =========================================================================

    @Test
    @DisplayName("6. Manager Subordinate Audit: Manager can view direct reports, team hierarchy, subordinate balances, and approve subordinate leaves")
    public void test6_Manager_SubordinateDataAccess_CanViewTeamAndDirectReports_AndCanApproveSubordinateLeave() throws Exception {
        // A. Manager 1 retrieves direct reports via /me/team
        mockMvc.perform(get("/api/managers/me/team")
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(sub1Employee.getId().intValue())))
                .andExpect(jsonPath("$[0].firstName", is("Bob")));

        // B. Manager 1 retrieves team hierarchy tree
        mockMvc.perform(get("/api/managers/me/team-hierarchy")
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.managerId", is(manager1Employee.getId().intValue())))
                .andExpect(jsonPath("$.directReports", hasSize(1)));

        // C. Manager 1 views subordinate profile by ID
        mockMvc.perform(get("/api/employees/" + sub1Employee.getId())
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(sub1Employee.getId().intValue())));

        // D. Manager 1 views subordinate leave balances
        mockMvc.perform(get("/api/leave-balances/employee/" + sub1Employee.getId())
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employee.id", is(sub1Employee.getId().intValue())))
                .andExpect(jsonPath("$[0].available", is(25)));

        // E. Manager 1 views subordinate leave request
        mockMvc.perform(get("/api/leave-requests/" + sub1LeaveRequest.getId())
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(sub1LeaveRequest.getId().intValue())))
                .andExpect(jsonPath("$.status", is("PENDING")));

        // F. Manager 1 views team leave requests
        mockMvc.perform(get("/api/managers/me/leave-requests")
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // G. Manager 1 checks leave overlap for subordinate
        LocalDate checkStart = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusDays(28);
        LocalDate checkEnd = checkStart.plusDays(3);

        mockMvc.perform(get("/api/leave-requests/check-overlap")
                        .header("Authorization", "Bearer " + manager1Token)
                        .param("startDate", checkStart.toString())
                        .param("endDate", checkEnd.toString())
                        .param("employeeId", sub1Employee.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overlapping", is(false)));

        // H. Manager 1 approves subordinate's pending leave request
        mockMvc.perform(put("/api/leave-requests/" + sub1LeaveRequest.getId() + "/approve")
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(sub1LeaveRequest.getId().intValue())))
                .andExpect(jsonPath("$.status", is("APPROVED")));

        // I. Verify audit record for approved leave
        boolean approvedCaptured = authorizationAuditLogRepository.findAll().stream()
                .anyMatch(log -> manager1User.getEmail().equalsIgnoreCase(log.getEmail())
                        && "GRANTED".equals(log.getDecision())
                        && "LEAVE_APPROVAL_CHECK".equals(log.getAction()));
        assertTrue(approvedCaptured, "Audit trail must record GRANTED authorization decision for subordinate leave approval");
    }

    @Test
    @DisplayName("7. Manager Isolation Audit: Manager is strictly forbidden from viewing or approving non-subordinate data from other departments")
    public void test7_Manager_DepartmentIsolation_CannotAccessUnrelatedManagerTeamOrUnrelatedSubordinateData() throws Exception {
        // A. Manager 1 attempts to view Manager 2's team by ID
        mockMvc.perform(get("/api/managers/" + manager2Employee.getId() + "/team")
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());

        // B. Manager 1 attempts to view Manager 2's team hierarchy
        mockMvc.perform(get("/api/managers/" + manager2Employee.getId() + "/team-hierarchy")
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());

        // C. Manager 1 attempts to view Subordinate 2's profile (Department B)
        mockMvc.perform(get("/api/employees/" + sub2Employee.getId())
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());

        // D. Manager 1 attempts to view Subordinate 2's leave balances
        mockMvc.perform(get("/api/leave-balances/employee/" + sub2Employee.getId())
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());

        // E. Manager 1 attempts to view Subordinate 2's leave request
        mockMvc.perform(get("/api/leave-requests/" + sub2LeaveRequest.getId())
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());

        // F. Manager 1 attempts to approve Subordinate 2's leave request
        mockMvc.perform(put("/api/leave-requests/" + sub2LeaveRequest.getId() + "/approve")
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());

        // G. Manager 1 attempts to reject Subordinate 2's leave request
        mockMvc.perform(put("/api/leave-requests/" + sub2LeaveRequest.getId() + "/reject")
                        .header("Authorization", "Bearer " + manager1Token)
                        .param("reason", "Cross-department interference"))
                .andExpect(status().isForbidden());

        // H. Manager 1 attempts to check overlap for Subordinate 2
        LocalDate checkStart = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusDays(28);
        LocalDate checkEnd = checkStart.plusDays(3);

        mockMvc.perform(get("/api/leave-requests/check-overlap")
                        .header("Authorization", "Bearer " + manager1Token)
                        .param("startDate", checkStart.toString())
                        .param("endDate", checkEnd.toString())
                        .param("employeeId", sub2Employee.getId().toString()))
                .andExpect(status().isForbidden());

        // I. Manager 1 attempts to export Subordinate 2's personal data
        mockMvc.perform(get("/api/sensitive-data/export/" + sub2Employee.getId())
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());

        // J. Verify audit trail records DENIED for the unauthorized cross-department attempt
        boolean denialCaptured = authorizationAuditLogRepository.findAll().stream()
                .anyMatch(log -> manager1User.getEmail().equalsIgnoreCase(log.getEmail())
                        && "DENIED".equals(log.getDecision())
                        && log.getResource().contains("LeaveRequest#" + sub2LeaveRequest.getId()));
        assertTrue(denialCaptured, "Audit trail must record DENIED for cross-department leave approval attempt");
    }

    @Test
    @DisplayName("8. Dual Approval & Anti-Self-Approval Audit: Manager cannot approve or reject their own leave requests")
    public void test8_Manager_DualApprovalEnforcement_CannotApproveOrRejectOwnLeaveRequest() throws Exception {
        LocalDate start = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusDays(35);
        LocalDate end = start.plusDays(3);

        // A. Manager 1 applies for personal leave
        LeaveRequestDTO managerLeave = new LeaveRequestDTO();
        managerLeave.setLeaveTypeId(annualLeaveType.getId());
        managerLeave.setStartDate(start);
        managerLeave.setEndDate(end);
        managerLeave.setReason("Manager Leadership Retreat");

        String responseStr = mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", "Bearer " + manager1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(managerLeave)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employee.id", is(manager1Employee.getId().intValue())))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andReturn().getResponse().getContentAsString();

        Long managerRequestId = objectMapper.readTree(responseStr).get("id").asLong();

        // B. Anti-Self-Approval Rule: Manager 1 attempts to self-approve own leave request -> 403 Forbidden
        mockMvc.perform(put("/api/leave-requests/" + managerRequestId + "/approve")
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());

        // C. Anti-Self-Approval Rule: Manager 1 attempts to self-reject own leave request -> 403 Forbidden
        mockMvc.perform(put("/api/leave-requests/" + managerRequestId + "/reject")
                        .header("Authorization", "Bearer " + manager1Token)
                        .param("reason", "Self rejection attempt"))
                .andExpect(status().isForbidden());

        // D. Verify authorization audit log recorded the self-approval denial
        boolean selfApprovalDenied = authorizationAuditLogRepository.findAll().stream()
                .anyMatch(log -> manager1User.getEmail().equalsIgnoreCase(log.getEmail())
                        && "DENIED".equals(log.getDecision())
                        && log.getResource().contains("LeaveRequest#" + managerRequestId));
        assertTrue(selfApprovalDenied, "Audit trail must record DENIED authorization for self-approval violation");

        // E. Separation of Duties: Admin can legitimately approve the manager's pending leave
        mockMvc.perform(put("/api/leave-requests/" + managerRequestId + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(managerRequestId.intValue())))
                .andExpect(jsonPath("$.status", is("APPROVED")));
    }

    @Test
    @DisplayName("9. Manager Vertical Escalation Audit: Manager is denied accessing global company data, user administration, or audit management")
    public void test9_Manager_VerticalPrivilegeEscalation_CannotAccessAdminGovernanceEndpoints() throws Exception {
        // A. Global leave requests search
        mockMvc.perform(get("/api/leave-requests")
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());

        // B. Global company leave balances
        mockMvc.perform(get("/api/leave-balances")
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());

        // C. User account administration
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/users/" + sub1User.getId() + "/lock")
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());

        // D. Security Audit & Incident Monitoring
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/authorization-audits")
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/security-events")
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());

        // E. Cryptographic Operations & Anonymization
        CryptoRequestDTO cryptoReq = new CryptoRequestDTO();
        cryptoReq.setData("unauthorizedManagerData");
        mockMvc.perform(post("/api/sensitive-data/encrypt")
                        .header("Authorization", "Bearer " + manager1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cryptoReq)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/sensitive-data/anonymize/" + sub1Employee.getId())
                        .header("Authorization", "Bearer " + manager1Token))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // SECTION 3: ADMIN DATA ACCESS, GOVERNANCE & AUDIT TRAIL VERIFICATION
    // =========================================================================

    @Test
    @DisplayName("10. Admin Global Access Audit: Admin possesses authorized global visibility and management across all departments, balances, and leaves")
    public void test10_Admin_GlobalDataAccess_CanViewAndManageAllEmployeesDepartmentsAndBalances() throws Exception {
        // A. Admin views employees across different departments
        mockMvc.perform(get("/api/employees/" + sub1Employee.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(sub1Employee.getId().intValue())));

        mockMvc.perform(get("/api/employees/" + sub2Employee.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(sub2Employee.getId().intValue())));

        // B. Admin lists all employees with pagination
        mockMvc.perform(get("/api/employees")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(5))));

        // C. Admin views teams of any manager
        mockMvc.perform(get("/api/managers/" + manager1Employee.getId() + "/team")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/managers/" + manager2Employee.getId() + "/team")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // D. Admin views balances of any employee
        mockMvc.perform(get("/api/leave-balances/employee/" + sub1Employee.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employee.id", is(sub1Employee.getId().intValue())));

        mockMvc.perform(get("/api/leave-balances/employee/" + sub2Employee.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employee.id", is(sub2Employee.getId().intValue())));

        // E. Admin lists all leave balances organization-wide
        mockMvc.perform(get("/api/leave-balances")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))));

        // F. Admin searches all leave requests organization-wide
        mockMvc.perform(get("/api/leave-requests")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(2))));

        // G. Admin can approve any leave request across any department
        mockMvc.perform(put("/api/leave-requests/" + sub2LeaveRequest.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(sub2LeaveRequest.getId().intValue())))
                .andExpect(jsonPath("$.status", is("APPROVED")));

        // H. Admin can export any employee's personal data
        mockMvc.perform(get("/api/sensitive-data/export/" + sub1Employee.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.id", is(sub1Employee.getId().intValue())));
    }

    @Test
    @DisplayName("11. Admin Security Governance Audit: Admin has full access to user management, locking, security events, and cryptographic engines")
    public void test11_Admin_GovernanceAndSecurityAuditAccess() throws Exception {
        // A. User Account Listing & Detail Inspection
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(5))));

        mockMvc.perform(get("/api/users/" + sub1User.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(sub1User.getId().intValue())))
                .andExpect(jsonPath("$.email", is(sub1User.getEmail())));

        // B. Administrative Account Lockout & Unlock Lifecycle
        mockMvc.perform(put("/api/users/" + sub1User.getId() + "/lock?reason=SecurityAuditInvestigation")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNonLocked", is(false)));

        mockMvc.perform(put("/api/users/" + sub1User.getId() + "/unlock")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNonLocked", is(true)));

        // C. Audit Logs & Authorization Audit Decision Logs
        mockMvc.perform(get("/api/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/authorization-audits")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // D. Security Event Summary KPIs
        mockMvc.perform(get("/api/security-events/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents", notNullValue()));

        // E. Cryptographic AES-256 Engine: Encryption and Decryption
        CryptoRequestDTO cryptoReq = new CryptoRequestDTO();
        cryptoReq.setData("ConfidentialSalaryInformation98000");

        String encryptResp = mockMvc.perform(post("/api/sensitive-data/encrypt")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cryptoReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encrypted", is(true)))
                .andExpect(jsonPath("$.result", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        String ciphertext = objectMapper.readTree(encryptResp).get("result").asText();

        CryptoRequestDTO decryptReq = new CryptoRequestDTO();
        decryptReq.setData(ciphertext);
        mockMvc.perform(post("/api/sensitive-data/decrypt")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decryptReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encrypted", is(false)))
                .andExpect(jsonPath("$.result", is("ConfidentialSalaryInformation98000")));
    }

    @Test
    @DisplayName("12. GDPR & Privacy Audit: Self-service personal data export and authorization audits isolate user records without cross-contamination")
    public void test12_GDPRAndSelfServiceDataAccess_UserOwnsSelfServiceDataWithoutCrossContamination() throws Exception {
        // A. Subordinate 1 exports own GDPR data
        mockMvc.perform(get("/api/sensitive-data/me/export")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.id", is(sub1Employee.getId().intValue())))
                .andExpect(jsonPath("$.user.email", is(sub1User.getEmail())))
                .andExpect(jsonPath("$.employee.firstName", is("Bob")));

        // B. Subordinate 2 exports own GDPR data
        mockMvc.perform(get("/api/sensitive-data/me/export")
                        .header("Authorization", "Bearer " + sub2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.id", is(sub2Employee.getId().intValue())))
                .andExpect(jsonPath("$.user.email", is(sub2User.getEmail())))
                .andExpect(jsonPath("$.employee.firstName", is("Dave")));

        // C. Subordinate 1 checks self-service authorization audits
        mockMvc.perform(get("/api/authorization-audits/me")
                        .header("Authorization", "Bearer " + sub1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.[*].email", everyItem(equalTo(sub1User.getEmail()))));

        // D. Subordinate 2 checks self-service authorization audits
        mockMvc.perform(get("/api/authorization-audits/me")
                        .header("Authorization", "Bearer " + sub2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.[*].email", everyItem(equalTo(sub2User.getEmail()))));
    }
}
