package com.leave_management_system.leave_management_system.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveRequestDTO;
import com.leave_management_system.leave_management_system.dto.TransactionIntegrityAuditReportDTO;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.exception.InsufficientLeaveException;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import com.leave_management_system.leave_management_system.security.idempotency.IdempotencyService;
import com.leave_management_system.leave_management_system.service.AuditService;
import com.leave_management_system.leave_management_system.service.LeaveRequestService;
import com.leave_management_system.leave_management_system.service.TransactionAndIdempotencyAuditService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("API and Database Audit - Transaction Integrity & Idempotency Audit Test Suite")
public class TransactionIntegrityAndIdempotencyAuditIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private LeaveRequestSignoffRepository leaveRequestSignoffRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SecurityEventRepository securityEventRepository;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    @Autowired
    private AuthorizationAuditLogRepository authorizationAuditLogRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TransactionReviewRepository transactionReviewRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private LeaveRequestService leaveRequestService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private TransactionAndIdempotencyAuditService auditReportService;

    @Autowired
    private IdempotencyService idempotencyService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private String runId;
    private Department department;
    private User adminUser;
    private Employee adminEmployee;
    private String adminToken;

    private User managerUser;
    private Employee managerEmployee;
    private String managerToken;

    private User hrUser;
    private Employee hrEmployee;
    private String hrToken;

    private User employeeUser;
    private Employee regularEmployee;
    private String employeeToken;

    private LeaveType annualLeave;
    private LeaveBalance employeeBalance;

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
        Role hrRole = roleRepository.findByName("HR").orElseGet(() -> roleRepository.save(new Role("HR")));
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));

        Permission permAdminView = permissionRepository.findByName("ADMIN_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("ADMIN_VIEW", "View admin data")));
        Permission permSystemAudit = permissionRepository.findByName("SYSTEM_AUDIT")
                .orElseGet(() -> permissionRepository.save(new Permission("SYSTEM_AUDIT", "Audit system")));
        Permission permDeptManage = permissionRepository.findByName("DEPARTMENT_MANAGE")
                .orElseGet(() -> permissionRepository.save(new Permission("DEPARTMENT_MANAGE", "Manage departments")));
        Permission permDeptView = permissionRepository.findByName("DEPARTMENT_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("DEPARTMENT_VIEW", "View departments")));
        Permission permLeaveApprove = permissionRepository.findByName("LEAVE_APPROVE")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_APPROVE", "Approve leaves")));
        Permission permLeaveCreate = permissionRepository.findByName("LEAVE_CREATE")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_CREATE", "Create leave requests")));
        Permission permLeaveApply = permissionRepository.findByName("LEAVE_APPLY")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_APPLY", "Apply for leaves")));
        Permission permLeaveView = permissionRepository.findByName("LEAVE_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_VIEW", "View leaves")));

        adminRole.getPermissions().clear();
        adminRole.getPermissions().addAll(Set.of(permAdminView, permSystemAudit, permDeptManage, permDeptView, permLeaveApprove, permLeaveCreate, permLeaveApply, permLeaveView));
        roleRepository.save(adminRole);

        managerRole.getPermissions().clear();
        managerRole.getPermissions().addAll(Set.of(permLeaveApprove, permLeaveView, permDeptView));
        roleRepository.save(managerRole);

        hrRole.getPermissions().clear();
        hrRole.getPermissions().addAll(Set.of(permLeaveApprove, permLeaveView, permDeptView));
        roleRepository.save(hrRole);

        employeeRole.getPermissions().clear();
        employeeRole.getPermissions().addAll(Set.of(permLeaveCreate, permLeaveApply, permLeaveView));
        roleRepository.save(employeeRole);

        // 2. Department
        department = new Department();
        department.setName("Integrity Dept " + runId);
        department = departmentRepository.save(department);

        // 3. Manager User & Employee
        managerUser = new User();
        managerUser.setEmail("mgr-" + runId + "@enterprise.com");
        managerUser.setPassword(passwordEncoder.encode("MgrPass123!"));
        managerUser.setActive(true);
        managerUser.setAccountNonLocked(true);
        managerUser.getRoles().add(managerRole);
        managerUser = userRepository.save(managerUser);

        managerEmployee = new Employee();
        managerEmployee.setFirstName("Manager");
        managerEmployee.setLastName("Director");
        managerEmployee.setEmployeeCode("MGR-" + runId);
        managerEmployee.setStatus("ACTIVE");
        managerEmployee.setSalary(new BigDecimal("110000.00"));
        managerEmployee.setDepartment(department);
        managerEmployee.setUser(managerUser);
        managerEmployee = employeeRepository.save(managerEmployee);
        managerUser.setEmployee(managerEmployee);
        managerUser = userRepository.save(managerUser);

        department.setManager(managerEmployee);
        department = departmentRepository.save(department);

        // 4. HR User & Employee
        hrUser = new User();
        hrUser.setEmail("hr-" + runId + "@enterprise.com");
        hrUser.setPassword(passwordEncoder.encode("HrPass123!"));
        hrUser.setActive(true);
        hrUser.setAccountNonLocked(true);
        hrUser.getRoles().add(hrRole);
        hrUser = userRepository.save(hrUser);

        hrEmployee = new Employee();
        hrEmployee.setFirstName("HR");
        hrEmployee.setLastName("Officer");
        hrEmployee.setEmployeeCode("HR-" + runId);
        hrEmployee.setStatus("ACTIVE");
        hrEmployee.setSalary(new BigDecimal("95000.00"));
        hrEmployee.setDepartment(department);
        hrEmployee.setUser(hrUser);
        hrEmployee = employeeRepository.save(hrEmployee);
        hrUser.setEmployee(hrEmployee);
        hrUser = userRepository.save(hrUser);

        // 5. Admin User & Employee
        adminUser = new User();
        adminUser.setEmail("admin-" + runId + "@enterprise.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.setAccountNonLocked(true);
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);

        adminEmployee = new Employee();
        adminEmployee.setFirstName("Admin");
        adminEmployee.setLastName("Controller");
        adminEmployee.setEmployeeCode("ADM-" + runId);
        adminEmployee.setStatus("ACTIVE");
        adminEmployee.setSalary(new BigDecimal("130000.00"));
        adminEmployee.setDepartment(department);
        adminEmployee.setUser(adminUser);
        adminEmployee = employeeRepository.save(adminEmployee);
        adminUser.setEmployee(adminEmployee);
        adminUser = userRepository.save(adminUser);

        // 6. Regular Employee
        employeeUser = new User();
        employeeUser.setEmail("emp-" + runId + "@enterprise.com");
        employeeUser.setPassword(passwordEncoder.encode("EmpPass123!"));
        employeeUser.setActive(true);
        employeeUser.setAccountNonLocked(true);
        employeeUser.getRoles().add(employeeRole);
        employeeUser = userRepository.save(employeeUser);

        regularEmployee = new Employee();
        regularEmployee.setFirstName("Regular");
        regularEmployee.setLastName("Developer");
        regularEmployee.setEmployeeCode("EMP-" + runId);
        regularEmployee.setStatus("ACTIVE");
        regularEmployee.setSalary(new BigDecimal("75000.00"));
        regularEmployee.setDepartment(department);
        regularEmployee.setManager(managerEmployee);
        regularEmployee.setUser(employeeUser);
        regularEmployee = employeeRepository.save(regularEmployee);
        employeeUser.setEmployee(regularEmployee);
        employeeUser = userRepository.save(employeeUser);

        // 7. Leave Type & Initial Balance
        annualLeave = new LeaveType();
        annualLeave.setName("Annual Vacation " + runId);
        annualLeave.setDescription("Standard Annual Vacation");
        annualLeave.setDefaultDays(20);
        annualLeave.setActive(true);
        annualLeave = leaveTypeRepository.save(annualLeave);

        int currentYear = LocalDate.now().getYear();
        employeeBalance = new LeaveBalance();
        employeeBalance.setEmployee(regularEmployee);
        employeeBalance.setLeaveType(annualLeave);
        employeeBalance.setYear(currentYear);
        employeeBalance.setTotal(20);
        employeeBalance.setUsed(0);
        employeeBalance.setAvailable(20);
        employeeBalance = leaveBalanceRepository.saveAndFlush(employeeBalance);

        // 8. Generate JWT Tokens
        adminToken = generateToken(adminUser, adminRole);
        managerToken = generateToken(managerUser, managerRole);
        hrToken = generateToken(hrUser, hrRole);
        employeeToken = generateToken(employeeUser, employeeRole);
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
        SecurityContextHolder.clearContext();
    }

    private void cleanupDatabase() {
        try {
            transactionReviewRepository.deleteAll();
            authorizationAuditLogRepository.deleteAll();
            securityEventRepository.deleteAll();
            auditLogRepository.deleteAll();
            loginHistoryRepository.deleteAll();
            refreshTokenRepository.deleteAll();
            idempotencyKeyRepository.deleteAll();
            leaveRequestSignoffRepository.deleteAll();
            leaveRequestRepository.deleteAll();
            leaveBalanceRepository.deleteAll();
            leaveTypeRepository.deleteAll();

            departmentRepository.findAll().forEach(dept -> {
                if (dept.getManager() != null) {
                    dept.setManager(null);
                    departmentRepository.save(dept);
                }
            });

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
    // SECTION 1: TRANSACTION ATOMICITY & ROLLBACK GUARANTEES
    // =========================================================================
    @Nested
    @DisplayName("Section 1: Transaction Atomicity & Rollback Guarantees")
    class TransactionAtomicityAndRollbackAudit {

        @Test
        @DisplayName("Leave application rolls back completely when requested days exceed available balance")
        void leaveApplication_RollsBack_WhenInsufficientBalance() throws Exception {
            // Available balance is 20 days. Attempting to apply for 25 working days.
            LocalDate mondayStart = getNextMonday();
            LocalDate fridayEnd = mondayStart.plusWeeks(5); // 25+ working days

            LeaveRequestDTO dto = new LeaveRequestDTO();
            dto.setEmployeeId(regularEmployee.getId());
            dto.setLeaveTypeId(annualLeave.getId());
            dto.setStartDate(mondayStart);
            dto.setEndDate(fridayEnd);
            dto.setReason("Excessive leave overdraft request");

            mockMvc.perform(post("/api/leave-requests")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("Insufficient leave balance")));

            // Invariant verification: Zero leave requests saved
            List<LeaveRequest> requests = leaveRequestRepository.findByEmployee(regularEmployee);
            assertTrue(requests.isEmpty(), "LeaveRequest must not exist after transaction rollback");

            // Invariant verification: LeaveBalance remains intact (20 total = 0 used + 20 available)
            LeaveBalance currentBalance = leaveBalanceRepository.findById(employeeBalance.getId()).orElseThrow();
            assertEquals(20, currentBalance.getTotal());
            assertEquals(0, currentBalance.getUsed());
            assertEquals(20, currentBalance.getAvailable());
        }

        @Test
        @DisplayName("Leave application rolls back when requested dates overlap with an existing pending request")
        void leaveApplication_RollsBack_WhenOverlappingDatesDetected() throws Exception {
            LocalDate mondayStart = getNextMonday();
            LocalDate wednesdayEnd = mondayStart.plusDays(2);

            // 1. Submit first valid request (Monday to Wednesday)
            LeaveRequestDTO firstDto = new LeaveRequestDTO();
            firstDto.setEmployeeId(regularEmployee.getId());
            firstDto.setLeaveTypeId(annualLeave.getId());
            firstDto.setStartDate(mondayStart);
            firstDto.setEndDate(wednesdayEnd);
            firstDto.setReason("Initial vacation request");

            mockMvc.perform(post("/api/leave-requests")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(firstDto)))
                    .andExpect(status().isCreated());

            assertEquals(1, leaveRequestRepository.findByEmployee(regularEmployee).size());

            // 2. Submit second overlapping request (Tuesday to Friday)
            LeaveRequestDTO overlapDto = new LeaveRequestDTO();
            overlapDto.setEmployeeId(regularEmployee.getId());
            overlapDto.setLeaveTypeId(annualLeave.getId());
            overlapDto.setStartDate(mondayStart.plusDays(1)); // Tuesday overlap
            overlapDto.setEndDate(mondayStart.plusDays(4));   // Friday
            overlapDto.setReason("Conflicting overlapping vacation request");

            mockMvc.perform(post("/api/leave-requests")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(overlapDto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("overlaps with an existing pending or approved leave")));

            // State verification: Still exactly 1 leave request exists, the second transaction rolled back
            List<LeaveRequest> requests = leaveRequestRepository.findByEmployee(regularEmployee);
            assertEquals(1, requests.size(), "Only the initial request should exist; overlapping request must roll back");
        }

        @Test
        @DisplayName("REQUIRES_NEW audit logging survives transaction rollback of the outer business operation")
        void auditLogPreservation_RequiresNew_SurvivesTransactionRollback() {
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

            String auditAction = "TX_AUDIT_PROBE_" + runId;
            String probeEmail = "security-auditor-" + runId + "@enterprise.com";

            assertThrows(RuntimeException.class, () -> {
                txTemplate.execute(status -> {
                    // Call REQUIRES_NEW audit service
                    auditService.logSecurityEvent(auditAction, "10.0.0.99", probeEmail, "Simulated security event during transaction rollback");

                    // Deliberately fail the outer transaction
                    throw new RuntimeException("Forced outer business transaction rollback");
                });
            });

            // Verification: Security audit log must be committed and persisted despite the outer failure
            List<AuditLog> persistedLogs = auditLogRepository.findByAction(auditAction);
            assertFalse(persistedLogs.isEmpty(), "Audit log with Propagation.REQUIRES_NEW must survive outer transaction rollback");
            assertEquals(auditAction, persistedLogs.getFirst().getAction());
            assertEquals("SECURITY_EVENT", persistedLogs.getFirst().getEntityType());
        }
    }

    // =========================================================================
    // SECTION 2: DATABASE INVARIANTS & CONCURRENCY INTEGRITY
    // =========================================================================
    @Nested
    @DisplayName("Section 2: Database Invariants & Concurrency Integrity")
    class DatabaseInvariantsAndConcurrencyAudit {

        @Test
        @DisplayName("Balance equation invariant (total = used + available) holds across all database records")
        void balanceInvariant_HoldsAcrossAllBalances() {
            List<LeaveBalance> balances = leaveBalanceRepository.findAll();
            assertFalse(balances.isEmpty(), "At least one leave balance should be present");

            for (LeaveBalance b : balances) {
                int total = b.getTotal() != null ? b.getTotal() : 0;
                int used = b.getUsed() != null ? b.getUsed() : 0;
                int available = b.getAvailable() != null ? b.getAvailable() : 0;

                assertEquals(total, used + available,
                        String.format("Invariant violation on balance ID %d: total (%d) != used (%d) + available (%d)",
                                b.getId(), total, used, available));
                assertTrue(available >= 0, "Available balance cannot be negative");
                assertTrue(used >= 0, "Used balance cannot be negative");
                assertTrue(total >= 0, "Total balance cannot be negative");
            }
        }

        @Test
        @DisplayName("Optimistic locking throws ObjectOptimisticLockingFailureException on concurrent stale entity updates")
        void optimisticLocking_FailsOnStaleEntityUpdate() {
            LeaveBalance balance = leaveBalanceRepository.findById(employeeBalance.getId()).orElseThrow();
            assertNotNull(balance.getVersion(), "Entity must have @Version initialized");

            // Simulate concurrent transaction incrementing version directly in DB
            jdbcTemplate.update("UPDATE leave_balances SET version = version + 1 WHERE id = ?", balance.getId());

            // Attempting to update in memory stale entity triggers optimistic locking exception
            balance.setAvailable(balance.getAvailable() - 1);
            balance.setUsed(balance.getUsed() + 1);

            assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
                leaveBalanceRepository.saveAndFlush(balance);
            }, "Stale entity update must throw ObjectOptimisticLockingFailureException");
        }

        @Test
        @DisplayName("Transaction integrity audit service detects corrupted balance equation anomalies")
        void transactionAuditService_DetectsBalanceAnomaly() {
            // Direct insertion of a corrupt balance row violating total == used + available
            jdbcTemplate.update(
                    "INSERT INTO leave_balances (employee_id, leave_type_id, year, total, used, available, version) " +
                            "VALUES (?, ?, 2026, 25, 5, 10, 0)", // 25 != 5 + 10 (10 missing)
                    adminEmployee.getId(), annualLeave.getId()
            );

            TransactionIntegrityAuditReportDTO report = auditReportService.auditTransactionIntegrity();

            assertNotNull(report);
            assertEquals("ANOMALY_DETECTED", report.getOverallStatus());
            assertFalse(report.isInvariantsPreserved());
            assertTrue(report.getBalanceAnomaliesCount() > 0);
            assertTrue(report.getBalanceAnomalies().stream()
                    .anyMatch(a -> a.contains("total (25) != used (5) + available (10)")));
        }
    }

    // =========================================================================
    // SECTION 3: IDEMPOTENCY KEY LIFECYCLE & REPLAY
    // =========================================================================
    @Nested
    @DisplayName("Section 3: Idempotency Key Lifecycle & Replay")
    class IdempotencyKeyLifecycleAudit {

        @Test
        @DisplayName("Mutating POST executes once and returns cached response with Idempotency-Replayed header on retry")
        void idempotency_ReplaysCachedResponse_OnIdenticalDuplicate() throws Exception {
            String idempotencyKey = "idemp-dept-" + runId;
            DepartmentRequestDTO dto = new DepartmentRequestDTO();
            dto.setName("Idempotent Dept " + runId);

            String requestPayload = objectMapper.writeValueAsString(dto);

            // First execution: returns HTTP 201 Created
            mockMvc.perform(post("/api/departments")
                            .header("Authorization", "Bearer " + adminToken)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestPayload))
                    .andExpect(status().isCreated())
                    .andExpect(header().doesNotExist("Idempotency-Replayed"))
                    .andExpect(jsonPath("$.name").value("Idempotent Dept " + runId));

            // Verify IdempotencyKey record is marked COMPLETED in database
            Optional<IdempotencyKey> keyOpt = idempotencyKeyRepository.findByIdempotencyKeyAndUserEmail(idempotencyKey, adminUser.getEmail());
            assertTrue(keyOpt.isPresent());
            assertEquals("COMPLETED", keyOpt.get().getStatus());
            assertEquals(201, keyOpt.get().getResponseStatus());

            // Second execution with identical payload and key: returns cached HTTP 201 with Idempotency-Replayed: true
            mockMvc.perform(post("/api/departments")
                            .header("Authorization", "Bearer " + adminToken)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestPayload))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Idempotency-Replayed", "true"))
                    .andExpect(jsonPath("$.name").value("Idempotent Dept " + runId));

            // Verify only ONE department was created in DB
            List<Department> departments = departmentRepository.findAll().stream()
                    .filter(d -> ("Idempotent Dept " + runId).equals(d.getName()))
                    .toList();
            assertEquals(1, departments.size(), "Only 1 department must exist; idempotency prevented duplicate execution");
        }

        @Test
        @DisplayName("Idempotency returns HTTP 409 Conflict when payload hash mismatches previous completed request")
        void idempotency_ReturnsConflict409_OnPayloadMismatch() throws Exception {
            String idempotencyKey = "idemp-mismatch-" + runId;

            DepartmentRequestDTO firstDto = new DepartmentRequestDTO();
            firstDto.setName("Alpha Dept " + runId);

            mockMvc.perform(post("/api/departments")
                            .header("Authorization", "Bearer " + adminToken)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(firstDto)))
                    .andExpect(status().isCreated());

            // Second call with same key but different body payload
            DepartmentRequestDTO secondDto = new DepartmentRequestDTO();
            secondDto.setName("Beta Dept " + runId);

            mockMvc.perform(post("/api/departments")
                            .header("Authorization", "Bearer " + adminToken)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(secondDto)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", containsString("Idempotency key payload mismatch")));
        }

        @Test
        @DisplayName("Idempotency returns HTTP 409 Conflict when an operation is currently IN_PROGRESS")
        void idempotency_ReturnsConflict409_OnConcurrentInProgress() throws Exception {
            String idempotencyKey = "idemp-inprogress-" + runId;

            // Seed an IN_PROGRESS idempotency key record
            IdempotencyKey inProgressKey = new IdempotencyKey(
                    idempotencyKey,
                    adminUser.getEmail(),
                    "/api/departments",
                    "POST",
                    "dummyHash123",
                    "IN_PROGRESS",
                    LocalDateTime.now(),
                    LocalDateTime.now().plusHours(24)
            );
            idempotencyKeyRepository.saveAndFlush(inProgressKey);

            DepartmentRequestDTO dto = new DepartmentRequestDTO();
            dto.setName("Concurrent Dept " + runId);

            mockMvc.perform(post("/api/departments")
                            .header("Authorization", "Bearer " + adminToken)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", containsString("currently in progress")));
        }

        @Test
        @DisplayName("Idempotency allows retry when previous execution recorded status FAILED")
        void idempotency_AllowsRetry_WhenStatusIsFailed() throws Exception {
            String idempotencyKey = "idemp-failed-" + runId;

            // Seed a FAILED idempotency key record
            IdempotencyKey failedKey = new IdempotencyKey(
                    idempotencyKey,
                    adminUser.getEmail(),
                    "/api/departments",
                    "POST",
                    "dummyHash456",
                    "FAILED",
                    LocalDateTime.now(),
                    LocalDateTime.now().plusHours(24)
            );
            failedKey.setResponseStatus(500);
            idempotencyKeyRepository.saveAndFlush(failedKey);

            DepartmentRequestDTO dto = new DepartmentRequestDTO();
            dto.setName("Retry Dept " + runId);

            mockMvc.perform(post("/api/departments")
                            .header("Authorization", "Bearer " + adminToken)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Retry Dept " + runId));
        }

        @Test
        @DisplayName("Idempotency cleanup API deletes expired keys and recovers interrupted in-progress keys")
        void idempotency_CleanupDeletesExpiredAndOrphanedKeys() throws Exception {
            // Seed expired key
            IdempotencyKey expiredKey = new IdempotencyKey(
                    "exp-key-" + runId,
                    adminUser.getEmail(),
                    "/api/departments",
                    "POST",
                    "hashExp",
                    "COMPLETED",
                    LocalDateTime.now().minusHours(25),
                    LocalDateTime.now().minusHours(1)
            );
            idempotencyKeyRepository.saveAndFlush(expiredKey);

            // Seed orphaned interrupted key (older than timeout seconds)
            IdempotencyKey orphanedKey = new IdempotencyKey(
                    "orphan-key-" + runId,
                    adminUser.getEmail(),
                    "/api/departments",
                    "POST",
                    "hashOrphan",
                    "IN_PROGRESS",
                    LocalDateTime.now().minusMinutes(10),
                    LocalDateTime.now().plusHours(24)
            );
            idempotencyKeyRepository.saveAndFlush(orphanedKey);

            mockMvc.perform(post("/api/audit/transactions/idempotency/cleanup")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletedExpiredCount", greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.deletedInterruptedCount", greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.message", containsString("Cleaned up")));
        }
    }

    // =========================================================================
    // SECTION 4: TRANSACTION REVIEW & DUAL SIGNOFF INTEGRITY
    // =========================================================================
    @Nested
    @DisplayName("Section 4: Transaction Review & Dual Signoff Integrity")
    class TransactionReviewAndDualSignoffAudit {

        @Test
        @DisplayName("Dual signoff workflow requires two sequential approvals before transitioning to APPROVED")
        void dualSignoffWorkflow_AndIntegrityAudit() {
            LocalDate mondayStart = getNextMonday();
            LocalDate wednesdayEnd = mondayStart.plusDays(2);

            // 1. Create a leave request requiring dual approval
            LeaveRequest req = new LeaveRequest();
            req.setEmployee(regularEmployee);
            req.setLeaveType(annualLeave);
            req.setStartDate(mondayStart);
            req.setEndDate(wednesdayEnd);
            req.setReason("Strategic conference - requires dual approval");
            req.setRequiresDualApproval(true);
            req.setStatus(LeaveStatus.PENDING);
            LeaveRequest saved = leaveRequestRepository.saveAndFlush(req);

            // 2. Manager approves
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(managerUser.getEmail(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_MANAGER"), new SimpleGrantedAuthority("LEAVE_APPROVE")))
            );
            leaveRequestService.approveLeaveRequest(saved.getId());

            LeaveRequest afterMgr = leaveRequestRepository.findById(saved.getId()).orElseThrow();
            assertEquals(LeaveStatus.PARTIALLY_APPROVED, afterMgr.getStatus());
            assertEquals("APPROVED", afterMgr.getManagerApprovalStatus());
            assertEquals("PENDING", afterMgr.getHrApprovalStatus());

            // 3. HR approves
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(hrUser.getEmail(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_HR"), new SimpleGrantedAuthority("LEAVE_APPROVE")))
            );
            leaveRequestService.approveLeaveRequest(saved.getId());

            LeaveRequest afterHr = leaveRequestRepository.findById(saved.getId()).orElseThrow();
            assertEquals(LeaveStatus.APPROVED, afterHr.getStatus());
            assertEquals("APPROVED", afterHr.getHrApprovalStatus());

            // 4. Verify signoffs in DB
            List<LeaveRequestSignoff> signoffs = leaveRequestSignoffRepository.findByLeaveRequestId(saved.getId());
            assertEquals(2, signoffs.size(), "Both Manager and HR signoff records must be present");
            assertTrue(signoffs.stream().anyMatch(s -> "MANAGER".equals(s.getApproverRole())));
            assertTrue(signoffs.stream().anyMatch(s -> "HR".equals(s.getApproverRole())));

            // 5. Transaction integrity audit verifies signoff integrity
            TransactionIntegrityAuditReportDTO report = auditReportService.auditTransactionIntegrity();
            assertTrue(report.isSignoffIntegrityPreserved());
            assertEquals(0, report.getSignoffAnomaliesCount());
        }

        @Test
        @DisplayName("Transaction audit flags signoff anomaly when dual-approval request is marked APPROVED without signoffs")
        void dualSignoffAudit_DetectsMissingSignoffAnomaly() {
            LocalDate mondayStart = getNextMonday();

            LeaveRequest corruptReq = new LeaveRequest();
            corruptReq.setEmployee(regularEmployee);
            corruptReq.setLeaveType(annualLeave);
            corruptReq.setStartDate(mondayStart);
            corruptReq.setEndDate(mondayStart.plusDays(2));
            corruptReq.setReason("Bypassed approval checks");
            corruptReq.setRequiresDualApproval(true);
            corruptReq.setStatus(LeaveStatus.APPROVED); // Flagged as approved with 0 signoffs
            leaveRequestRepository.saveAndFlush(corruptReq);

            TransactionIntegrityAuditReportDTO report = auditReportService.auditTransactionIntegrity();

            assertNotNull(report);
            assertEquals("ANOMALY_DETECTED", report.getOverallStatus());
            assertFalse(report.isSignoffIntegrityPreserved());
            assertTrue(report.getSignoffAnomaliesCount() > 0);
            assertTrue(report.getSignoffAnomalies().getFirst().contains("requires dual approval and is APPROVED, but only has 0 signoff"));
        }

        @Test
        @DisplayName("Transaction reviews are accurately tracked and reported in audit report")
        void transactionReview_TracksSensitiveActions() {
            TransactionReview review = new TransactionReview();
            review.setTransactionType(TransactionType.LEAVE_REQUEST);
            review.setEntityType("LeaveRequest");
            review.setEntityId(999L);
            review.setEmployee(regularEmployee);
            review.setActor(adminUser);
            review.setActorEmail(adminUser.getEmail());
            review.setRiskLevel(RiskLevel.HIGH);
            review.setRiskScore(85);
            review.setRiskReason("High duration leave detected");
            review.setReviewStatus(ReviewStatus.PENDING_REVIEW);
            review.setCreatedAt(LocalDateTime.now());
            transactionReviewRepository.saveAndFlush(review);

            TransactionIntegrityAuditReportDTO report = auditReportService.auditTransactionIntegrity();
            assertTrue(report.getTotalTransactionReviews() >= 1);
        }
    }

    // =========================================================================
    // SECTION 5: API ENDPOINT LEVEL AUTHORIZATION & SECURITY AUDIT
    // =========================================================================
    @Nested
    @DisplayName("Section 5: API Endpoint Level Authorization & Security Audit")
    class ApiAuthorizationAndSecurityAudit {

        @Test
        @DisplayName("Audit endpoints return HTTP 401 Unauthorized when unauthenticated")
        void auditEndpoints_Return401_WhenUnauthenticated() throws Exception {
            mockMvc.perform(get("/api/audit/transactions/integrity"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/audit/transactions/idempotency"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(post("/api/audit/transactions/idempotency/cleanup"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Audit endpoints return HTTP 403 Forbidden for non-admin roles (Employee and Manager)")
        void auditEndpoints_Return403_ForEmployeeAndManager() throws Exception {
            // Employee role
            mockMvc.perform(get("/api/audit/transactions/integrity")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/audit/transactions/idempotency")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/audit/transactions/idempotency/cleanup")
                            .header("Authorization", "Bearer " + employeeToken))
                    .andExpect(status().isForbidden());

            // Manager role
            mockMvc.perform(get("/api/audit/transactions/integrity")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/audit/transactions/idempotency")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isForbidden());

            mockMvc.perform(post("/api/audit/transactions/idempotency/cleanup")
                            .header("Authorization", "Bearer " + managerToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Audit endpoints return HTTP 200 OK with accurate JSON schemas for Admin")
        void auditEndpoints_Return200_ForAdmin() throws Exception {
            mockMvc.perform(get("/api/audit/transactions/integrity")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overallStatus").exists())
                    .andExpect(jsonPath("$.invariantsPreserved").isBoolean())
                    .andExpect(jsonPath("$.optimisticLockingAudited").value(true));

            mockMvc.perform(get("/api/audit/transactions/idempotency")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").exists())
                    .andExpect(jsonPath("$.totalKeys").isNumber())
                    .andExpect(jsonPath("$.inProgressTimeoutSeconds").value(120));

            mockMvc.perform(post("/api/audit/transactions/idempotency/cleanup")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletedExpiredCount").isNumber())
                    .andExpect(jsonPath("$.deletedInterruptedCount").isNumber())
                    .andExpect(jsonPath("$.message").exists());
        }
    }

    private LocalDate getNextMonday() {
        LocalDate date = LocalDate.now().plusDays(1);
        while (date.getDayOfWeek().getValue() != 1) { // 1 = Monday
            date = date.plusDays(1);
        }
        return date;
    }
}
