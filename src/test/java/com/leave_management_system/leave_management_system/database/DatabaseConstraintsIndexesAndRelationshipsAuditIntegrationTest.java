package com.leave_management_system.leave_management_system.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.DepartmentRequestDTO;
import com.leave_management_system.leave_management_system.dto.EmployeeRequestDTO;
import com.leave_management_system.leave_management_system.dto.LeaveTypeRequestDTO;
import com.leave_management_system.leave_management_system.dto.RegisterRequestDTO;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.*;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("API and Database Audit - Comprehensive Constraints, Indexes & Relationships Test Suite")
public class DatabaseConstraintsIndexesAndRelationshipsAuditIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

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

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private String runId;
    private Department engineeringDept;
    private User adminUser;
    private Employee adminEmployee;
    private String adminToken;
    private LeaveType annualLeave;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        cleanupDatabase();

        runId = UUID.randomUUID().toString().substring(0, 8);

        // 1. Roles & Permissions Setup
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        Permission permDeptManage = permissionRepository.findByName("DEPARTMENT_MANAGE")
                .orElseGet(() -> permissionRepository.save(new Permission("DEPARTMENT_MANAGE", "Manage departments")));
        Permission permDeptView = permissionRepository.findByName("DEPARTMENT_VIEW")
                .orElseGet(() -> permissionRepository.save(new Permission("DEPARTMENT_VIEW", "View departments")));
        Permission permEmpCreate = permissionRepository.findByName("EMPLOYEE_CREATE")
                .orElseGet(() -> permissionRepository.save(new Permission("EMPLOYEE_CREATE", "Create employees")));
        Permission permLeaveTypeManage = permissionRepository.findByName("LEAVE_TYPE_MANAGE")
                .orElseGet(() -> permissionRepository.save(new Permission("LEAVE_TYPE_MANAGE", "Manage leave types")));

        adminRole.getPermissions().clear();
        adminRole.getPermissions().addAll(Set.of(permDeptManage, permDeptView, permEmpCreate, permLeaveTypeManage));
        roleRepository.save(adminRole);

        // 2. Department
        engineeringDept = new Department();
        engineeringDept.setName("Audit Dept " + runId);
        engineeringDept = departmentRepository.save(engineeringDept);

        // 3. Admin User & Employee
        adminUser = new User();
        adminUser.setEmail("audit-admin-" + runId + "@enterprise.com");
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

        // 4. Leave Type
        annualLeave = new LeaveType();
        annualLeave.setName("Annual Paid Time Off " + runId);
        annualLeave.setDescription("Standard Annual Vacation");
        annualLeave.setDefaultDays(25);
        annualLeave.setActive(true);
        annualLeave = leaveTypeRepository.save(annualLeave);

        // 5. Admin JWT Token
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        adminRole.getPermissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.getName())));
        UserDetails details = new org.springframework.security.core.userdetails.User(
                adminUser.getEmail(), adminUser.getPassword(), authorities);
        adminToken = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
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
            idempotencyKeyRepository.deleteAll();
            leaveRequestSignoffRepository.deleteAll();
            leaveRequestRepository.deleteAll();
            leaveBalanceRepository.deleteAll();
            leaveTypeRepository.deleteAll();

            // Safely unlink department managers
            departmentRepository.findAll().forEach(dept -> {
                if (dept.getManager() != null) {
                    dept.setManager(null);
                    departmentRepository.save(dept);
                }
            });

            // Safely unlink employee references
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
    // SECTION 1: DATABASE CONSTRAINTS AUDIT
    // =========================================================================
    @Nested
    @DisplayName("1. Database Schema Constraints Audit (PK, FK, Unique & Not-Null)")
    class SchemaConstraintsAudit {

        @Test
        @DisplayName("Audit Primary Keys: Every table has a valid defined Primary Key constraint")
        void testPrimaryKeysExistOnAllTables() throws Exception {
            List<String> coreTables = List.of(
                    "DEPARTMENTS", "EMPLOYEES", "LEAVE_REQUESTS", "LEAVE_TYPES", "LEAVE_BALANCES",
                    "USERS", "ROLES", "PERMISSIONS", "USER_ROLES", "ROLE_PERMISSIONS",
                    "AUDIT_LOGS", "SECURITY_EVENTS", "LOGIN_HISTORY", "REFRESH_TOKENS",
                    "AUTHORIZATION_AUDIT_LOGS", "TRANSACTION_REVIEWS", "IDEMPOTENCY_KEYS",
                    "LEAVE_REQUEST_SIGNOFFS"
            );

            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                for (String table : coreTables) {
                    try (ResultSet rs = metaData.getPrimaryKeys(null, "PUBLIC", table)) {
                        List<String> pkColumns = new ArrayList<>();
                        while (rs.next()) {
                            pkColumns.add(rs.getString("COLUMN_NAME").toUpperCase());
                        }
                        assertFalse(pkColumns.isEmpty(), "Table " + table + " must have a Primary Key constraint");

                        if ("USER_ROLES".equals(table)) {
                            assertTrue(pkColumns.contains("USER_ID") && pkColumns.contains("ROLE_ID"),
                                    "USER_ROLES must have composite primary key (USER_ID, ROLE_ID)");
                        } else if ("ROLE_PERMISSIONS".equals(table)) {
                            assertTrue(pkColumns.contains("ROLE_ID") && pkColumns.contains("PERMISSION_ID"),
                                    "ROLE_PERMISSIONS must have composite primary key (ROLE_ID, PERMISSION_ID)");
                        } else {
                            assertTrue(pkColumns.contains("ID"),
                                    "Entity table " + table + " must have ID as primary key");
                        }
                    }
                }
            }
        }

        @Test
        @DisplayName("Audit Foreign Keys: Referential foreign key constraints exist across entity boundaries")
        void testForeignKeysEnforcedAcrossRelationships() throws Exception {
            Map<String, List<String>> expectedFkMap = Map.of(
                    "EMPLOYEES", List.of("DEPARTMENT_ID", "MANAGER_ID", "USER_ID"),
                    "DEPARTMENTS", List.of("MANAGER_ID"),
                    "LEAVE_REQUESTS", List.of("EMPLOYEE_ID", "LEAVE_TYPE_ID"),
                    "LEAVE_BALANCES", List.of("EMPLOYEE_ID", "LEAVE_TYPE_ID"),
                    "USER_ROLES", List.of("USER_ID", "ROLE_ID"),
                    "ROLE_PERMISSIONS", List.of("ROLE_ID", "PERMISSION_ID"),
                    "REFRESH_TOKENS", List.of("USER_ID"),
                    "LEAVE_REQUEST_SIGNOFFS", List.of("LEAVE_REQUEST_ID", "APPROVER_ID")
            );

            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                for (Map.Entry<String, List<String>> entry : expectedFkMap.entrySet()) {
                    String table = entry.getKey();
                    List<String> expectedFkCols = entry.getValue();

                    try (ResultSet rs = metaData.getImportedKeys(null, "PUBLIC", table)) {
                        Set<String> actualFkCols = new HashSet<>();
                        while (rs.next()) {
                            actualFkCols.add(rs.getString("FKCOLUMN_NAME").toUpperCase());
                        }
                        for (String expectedCol : expectedFkCols) {
                            assertTrue(actualFkCols.contains(expectedCol),
                                    "Table " + table + " must have Foreign Key constraint on column " + expectedCol);
                        }
                    }
                }
            }
        }

        @Test
        @DisplayName("Audit Unique Constraints: Unique indexes and constraints exist on business keys")
        void testUniqueConstraintsExistInMetadata() throws Exception {
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();

                // 1. users(email)
                assertTableHasUniqueIndex(metaData, "USERS", "EMAIL");

                // 2. employees(employee_code) & employees(user_id)
                assertTableHasUniqueIndex(metaData, "EMPLOYEES", "EMPLOYEE_CODE");
                assertTableHasUniqueIndex(metaData, "EMPLOYEES", "USER_ID");

                // 3. departments(name)
                assertTableHasUniqueIndex(metaData, "DEPARTMENTS", "NAME");

                // 4. leave_types(name)
                assertTableHasUniqueIndex(metaData, "LEAVE_TYPES", "NAME");

                // 5. roles(name)
                assertTableHasUniqueIndex(metaData, "ROLES", "NAME");

                // 6. leave_balances composite unique (employee_id, leave_type_id, year)
                assertCompositeUniqueIndex(metaData, "LEAVE_BALANCES", Set.of("EMPLOYEE_ID", "LEAVE_TYPE_ID", "YEAR"));

                // 7. idempotency_keys composite unique (idempotency_key, user_email)
                assertCompositeUniqueIndex(metaData, "IDEMPOTENCY_KEYS", Set.of("IDEMPOTENCY_KEY", "USER_EMAIL"));
            }
        }

        @Test
        @DisplayName("Audit NOT NULL Constraints: Mandatory columns are defined with NOT NULL in schema")
        void testNotNullColumnConstraintsInMetadata() throws Exception {
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();

                assertColumnNotNull(metaData, "USERS", "EMAIL");
                assertColumnNotNull(metaData, "USERS", "PASSWORD");
                assertColumnNotNull(metaData, "EMPLOYEES", "FIRST_NAME");
                assertColumnNotNull(metaData, "EMPLOYEES", "LAST_NAME");
                assertColumnNotNull(metaData, "DEPARTMENTS", "NAME");
                assertColumnNotNull(metaData, "LEAVE_REQUESTS", "START_DATE");
                assertColumnNotNull(metaData, "LEAVE_REQUESTS", "END_DATE");
                assertColumnNotNull(metaData, "LEAVE_REQUESTS", "STATUS");
                assertColumnNotNull(metaData, "LEAVE_BALANCES", "YEAR");
                assertColumnNotNull(metaData, "LEAVE_BALANCES", "TOTAL");
                assertColumnNotNull(metaData, "LEAVE_BALANCES", "AVAILABLE");
                assertColumnNotNull(metaData, "LEAVE_BALANCES", "USED");
            }
        }

        @Test
        @DisplayName("Runtime Constraint Enforcement: Duplicate user email throws DataIntegrityViolationException")
        void testDuplicateUserEmailThrowsException() {
            User duplicateUser = new User();
            duplicateUser.setEmail(adminUser.getEmail()); // Duplicate
            duplicateUser.setPassword("AnyPassword123!");
            duplicateUser.setActive(true);

            assertThrows(DataIntegrityViolationException.class, () -> {
                userRepository.saveAndFlush(duplicateUser);
            }, "Inserting duplicate user email must violate unique constraint");
        }

        @Test
        @DisplayName("Runtime Constraint Enforcement: Duplicate employee code throws DataIntegrityViolationException")
        void testDuplicateEmployeeCodeThrowsException() {
            User secondUser = new User();
            secondUser.setEmail("second-emp-" + runId + "@enterprise.com");
            secondUser.setPassword("Pass1234!");
            secondUser.setActive(true);
            secondUser = userRepository.saveAndFlush(secondUser);

            Employee duplicateEmp = new Employee();
            duplicateEmp.setFirstName("Duplicate");
            duplicateEmp.setLastName("Code");
            duplicateEmp.setEmployeeCode(adminEmployee.getEmployeeCode()); // Duplicate employee code
            duplicateEmp.setStatus("ACTIVE");
            duplicateEmp.setSalary(new BigDecimal("60000.00"));
            duplicateEmp.setDepartment(engineeringDept);
            duplicateEmp.setUser(secondUser);

            assertThrows(DataIntegrityViolationException.class, () -> {
                employeeRepository.saveAndFlush(duplicateEmp);
            }, "Inserting duplicate employee code must violate unique constraint");
        }

        @Test
        @DisplayName("Runtime Constraint Enforcement: Duplicate leave balance per year throws DataIntegrityViolationException")
        void testDuplicateLeaveBalanceThrowsException() {
            int currentYear = LocalDate.now().getYear();

            LeaveBalance firstBalance = new LeaveBalance();
            firstBalance.setEmployee(adminEmployee);
            firstBalance.setLeaveType(annualLeave);
            firstBalance.setYear(currentYear);
            firstBalance.setTotal(25);
            firstBalance.setAvailable(25);
            firstBalance.setUsed(0);
            leaveBalanceRepository.saveAndFlush(firstBalance);

            LeaveBalance duplicateBalance = new LeaveBalance();
            duplicateBalance.setEmployee(adminEmployee);
            duplicateBalance.setLeaveType(annualLeave);
            duplicateBalance.setYear(currentYear); // Duplicate year + employee + leaveType
            duplicateBalance.setTotal(30);
            duplicateBalance.setAvailable(30);
            duplicateBalance.setUsed(0);

            assertThrows(DataIntegrityViolationException.class, () -> {
                leaveBalanceRepository.saveAndFlush(duplicateBalance);
            }, "Composite unique constraint uq_leave_balance must reject duplicate year allocations");
        }

        @Test
        @DisplayName("Runtime Constraint Enforcement: Non-existent foreign key throws DataIntegrityViolationException")
        void testInvalidForeignKeyThrowsException() {
            assertThrows(DataIntegrityViolationException.class, () -> {
                jdbcTemplate.update(
                        "INSERT INTO employees (first_name, last_name, employee_code, status, salary, department_id) " +
                                "VALUES ('Ghost', 'Employee', 'EMP-GHOST-" + runId + "', 'ACTIVE', 50000.00, 9999999)");
            }, "Inserting employee with invalid department foreign key must be blocked by DB engine");
        }

        @Test
        @DisplayName("Runtime Constraint Enforcement: Deleting referenced employee throws DataIntegrityViolationException")
        void testDeletingReferencedEmployeeThrowsException() {
            LeaveRequest request = new LeaveRequest();
            request.setEmployee(adminEmployee);
            request.setLeaveType(annualLeave);
            request.setStartDate(LocalDate.now().plusDays(10));
            request.setEndDate(LocalDate.now().plusDays(12));
            request.setStatus(LeaveStatus.PENDING);
            request.setReason("Doctor visit");
            leaveRequestRepository.saveAndFlush(request);

            assertThrows(DataIntegrityViolationException.class, () -> {
                jdbcTemplate.update("DELETE FROM employees WHERE id = ?", adminEmployee.getId());
            }, "Deleting employee with existing leave requests must fail due to foreign key constraint");
        }

        @Test
        @DisplayName("Runtime Constraint Enforcement: NULL in NOT NULL column throws DataIntegrityViolationException")
        void testNullInMandatoryColumnThrowsException() {
            assertThrows(DataIntegrityViolationException.class, () -> {
                jdbcTemplate.update(
                        "INSERT INTO departments (name) VALUES (NULL)");
            }, "Inserting NULL into departments.name must violate NOT NULL constraint");
        }
    }

    // =========================================================================
    // SECTION 2: DATABASE INDEXES AUDIT
    // =========================================================================
    @Nested
    @DisplayName("2. Database Indexes Audit (Performance Indexes & Query Plan Execution)")
    class DatabaseIndexesAudit {

        @Test
        @DisplayName("Audit Explicit Performance Indexes: Required query and filtering indexes exist in schema")
        void testExplicitPerformanceIndexesExist() throws Exception {
            Map<String, List<String>> expectedIndexMap = Map.of(
                    "LEAVE_REQUESTS", List.of("IDX_LEAVE_REQUESTS_EMPLOYEE_ID", "IDX_LEAVE_REQUESTS_STATUS"),
                    "AUDIT_LOGS", List.of("IDX_AUDIT_LOGS_TIMESTAMP", "IDX_AUDIT_LOGS_ACTION", "IDX_AUDIT_LOGS_ENTITY", "IDX_AUDIT_LOGS_USER_ID"),
                    "SECURITY_EVENTS", List.of("IDX_SEC_EVENT_CREATED_AT", "IDX_SEC_EVENT_TYPE", "IDX_SEC_EVENT_SEVERITY", "IDX_SEC_EVENT_STATUS"),
                    "LOGIN_HISTORY", List.of("IDX_LOGIN_HISTORY_USER_ID", "IDX_LOGIN_HISTORY_LOGIN_TIME"),
                    "AUTHORIZATION_AUDIT_LOGS", List.of("IDX_AUTHZ_AUDIT_USER_ID", "IDX_AUTHZ_AUDIT_TIMESTAMP", "IDX_AUTHZ_AUDIT_ACTION", "IDX_AUTHZ_AUDIT_DECISION"),
                    "IDEMPOTENCY_KEYS", List.of("IDX_IDEMPOTENCY_EXPIRES_AT"),
                    "LEAVE_REQUEST_SIGNOFFS", List.of("IDX_SIGNOFF_LEAVE_REQUEST", "IDX_SIGNOFF_APPROVER")
            );

            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                for (Map.Entry<String, List<String>> entry : expectedIndexMap.entrySet()) {
                    String table = entry.getKey();
                    List<String> expectedIndexes = entry.getValue();

                    try (ResultSet rs = metaData.getIndexInfo(null, "PUBLIC", table, false, false)) {
                        Set<String> actualIndexes = new HashSet<>();
                        while (rs.next()) {
                            String indexName = rs.getString("INDEX_NAME");
                            if (indexName != null) {
                                actualIndexes.add(indexName.toUpperCase());
                            }
                        }
                        for (String expectedIndex : expectedIndexes) {
                            assertTrue(actualIndexes.contains(expectedIndex),
                                    "Table " + table + " must have index " + expectedIndex);
                        }
                    }
                }
            }
        }

        @Test
        @DisplayName("Audit Query Execution Plan: Database engine utilizes indexes on leave_requests queries")
        void testQueryPlanUsesLeaveRequestsIndex() {
            String explainSql = "EXPLAIN SELECT * FROM leave_requests WHERE employee_id = " + adminEmployee.getId();
            List<String> explainOutput = jdbcTemplate.query(explainSql, (rs, rowNum) -> rs.getString(1));

            assertFalse(explainOutput.isEmpty(), "Query execution plan must not be empty");
            String plan = String.join(" ", explainOutput).toUpperCase();

            // Verify index scan on employee_id index or primary key
            assertTrue(plan.contains("IDX_LEAVE_REQUESTS_EMPLOYEE_ID") || plan.contains("INDEX"),
                    "Explain plan must reference index scan for leave_requests query: " + plan);
        }

        @Test
        @DisplayName("Audit Query Execution Plan: Database engine utilizes indexes on audit_logs queries")
        void testQueryPlanUsesAuditLogsTimestampIndex() {
            String explainSql = "EXPLAIN SELECT * FROM audit_logs WHERE timestamp >= CURRENT_TIMESTAMP";
            List<String> explainOutput = jdbcTemplate.query(explainSql, (rs, rowNum) -> rs.getString(1));

            assertFalse(explainOutput.isEmpty());
            String plan = String.join(" ", explainOutput).toUpperCase();
            assertTrue(plan.contains("IDX_AUDIT_LOGS_TIMESTAMP") || plan.contains("INDEX"),
                    "Explain plan must reference index scan for audit_logs query: " + plan);
        }

        @Test
        @DisplayName("Audit Query Execution Plan: Database engine utilizes index on idempotency_keys expires_at")
        void testQueryPlanUsesIdempotencyKeysExpiryIndex() {
            String explainSql = "EXPLAIN SELECT * FROM idempotency_keys WHERE expires_at < CURRENT_TIMESTAMP";
            List<String> explainOutput = jdbcTemplate.query(explainSql, (rs, rowNum) -> rs.getString(1));

            assertFalse(explainOutput.isEmpty());
            String plan = String.join(" ", explainOutput).toUpperCase();
            assertTrue(plan.contains("IDX_IDEMPOTENCY_EXPIRES_AT") || plan.contains("INDEX"),
                    "Explain plan must reference index scan for idempotency cleanup query: " + plan);
        }
    }

    // =========================================================================
    // SECTION 3: JPA RELATIONSHIPS & ENTITY MAPPING AUDIT
    // =========================================================================
    @Nested
    @DisplayName("3. JPA Entity Relationships & Mapping Integrity Audit")
    class EntityRelationshipsAudit {

        @Test
        @DisplayName("Relationship Audit: OneToOne User <-> Employee bidirectional integrity and unique binding")
        void testOneToOneUserEmployeeBidirectionalIntegrity() {
            // Forward and reverse navigation
            assertNotNull(adminEmployee.getUser(), "Employee must navigate to User");
            assertNotNull(adminUser.getEmployee(), "User must navigate to Employee");
            assertEquals(adminUser.getId(), adminEmployee.getUser().getId());
            assertEquals(adminEmployee.getId(), adminUser.getEmployee().getId());

            // Unique constraint on employees.user_id prevents binding second employee to same user
            Employee rogueEmp = new Employee();
            rogueEmp.setFirstName("Rogue");
            rogueEmp.setLastName("Claimant");
            rogueEmp.setEmployeeCode("EMP-ROGUE-" + runId);
            rogueEmp.setStatus("ACTIVE");
            rogueEmp.setSalary(new BigDecimal("50000.00"));
            rogueEmp.setDepartment(engineeringDept);
            rogueEmp.setUser(adminUser); // Same user

            assertThrows(DataIntegrityViolationException.class, () -> {
                employeeRepository.saveAndFlush(rogueEmp);
            }, "employees.user_id unique constraint must enforce strict 1:1 cardinality");
        }

        @Test
        @DisplayName("Relationship Audit: Hierarchical self-referencing Employee manager relationships")
        void testSelfReferentialManagerHierarchy() {
            // 1. Manager Employee reporting to Admin
            User mgrUser = new User();
            mgrUser.setEmail("mgr-" + runId + "@enterprise.com");
            mgrUser.setPassword(passwordEncoder.encode("MgrPass123!"));
            mgrUser.setActive(true);
            mgrUser = userRepository.saveAndFlush(mgrUser);

            Employee manager = new Employee();
            manager.setFirstName("Mid");
            manager.setLastName("Manager");
            manager.setEmployeeCode("MGR-" + runId);
            manager.setStatus("ACTIVE");
            manager.setSalary(new BigDecimal("90000.00"));
            manager.setDepartment(engineeringDept);
            manager.setManager(adminEmployee); // Reports to Admin
            manager.setUser(mgrUser);
            manager = employeeRepository.saveAndFlush(manager);

            // 2. Subordinate reporting to Manager
            User subUser = new User();
            subUser.setEmail("sub-" + runId + "@enterprise.com");
            subUser.setPassword(passwordEncoder.encode("SubPass123!"));
            subUser.setActive(true);
            subUser = userRepository.saveAndFlush(subUser);

            Employee subordinate = new Employee();
            subordinate.setFirstName("Junior");
            subordinate.setLastName("Dev");
            subordinate.setEmployeeCode("DEV-" + runId);
            subordinate.setStatus("ACTIVE");
            subordinate.setSalary(new BigDecimal("70000.00"));
            subordinate.setDepartment(engineeringDept);
            subordinate.setManager(manager); // Reports to Mid Manager
            subordinate.setUser(subUser);
            subordinate = employeeRepository.saveAndFlush(subordinate);

            // Traversal verification
            assertEquals(manager.getId(), subordinate.getManager().getId(), "Subordinate manager must match Mid Manager");
            assertEquals(adminEmployee.getId(), subordinate.getManager().getManager().getId(), "Multi-level traversal must resolve Admin");
            assertNull(adminEmployee.getManager(), "Top-level executive must have null manager");

            // Repository scoping verification
            List<Employee> directReports = employeeRepository.findByManagerId(manager.getId());
            assertEquals(1, directReports.size(), "Manager must have exactly 1 direct report");
            assertEquals(subordinate.getId(), directReports.get(0).getId());
        }

        @Test
        @DisplayName("Relationship Audit: Department manager relationship can be safely set and unlinked")
        void testDepartmentManagerRelationship() {
            engineeringDept.setManager(adminEmployee);
            engineeringDept = departmentRepository.saveAndFlush(engineeringDept);

            Department reloaded = departmentRepository.findById(engineeringDept.getId()).orElseThrow();
            assertNotNull(reloaded.getManager(), "Department manager must be persisted");
            assertEquals(adminEmployee.getId(), reloaded.getManager().getId());

            // Safe unlinking
            reloaded.setManager(null);
            departmentRepository.saveAndFlush(reloaded);

            Department unlinked = departmentRepository.findById(engineeringDept.getId()).orElseThrow();
            assertNull(unlinked.getManager(), "Department manager can be cleanly unlinked");
        }

        @Test
        @DisplayName("Relationship Audit: OneToMany cascade and orphan removal on LeaveRequest -> LeaveRequestSignoff")
        void testLeaveRequestSignoffsCascadeAndOrphanRemoval() {
            LeaveRequest request = new LeaveRequest();
            request.setEmployee(adminEmployee);
            request.setLeaveType(annualLeave);
            request.setStartDate(LocalDate.now().plusDays(20));
            request.setEndDate(LocalDate.now().plusDays(26));
            request.setStatus(LeaveStatus.PENDING);
            request.setReason("Signoff Cascade Test");
            request.setRequiresDualApproval(true);

            // Add signoff child items
            LeaveRequestSignoff signoff1 = new LeaveRequestSignoff();
            signoff1.setLeaveRequest(request);
            signoff1.setApproverRole("ROLE_MANAGER");
            signoff1.setApprover(adminUser);
            signoff1.setStatus("APPROVED");
            signoff1.setComments("Manager approved");

            LeaveRequestSignoff signoff2 = new LeaveRequestSignoff();
            signoff2.setLeaveRequest(request);
            signoff2.setApproverRole("ROLE_HR");
            signoff2.setApprover(adminUser);
            signoff2.setStatus("PENDING");

            request.getSignoffs().add(signoff1);
            request.getSignoffs().add(signoff2);

            // Save parent with cascade
            request = leaveRequestRepository.saveAndFlush(request);
            Long requestId = request.getId();

            List<LeaveRequestSignoff> persistedSignoffs = leaveRequestSignoffRepository.findByLeaveRequestId(requestId);
            assertEquals(2, persistedSignoffs.size(), "Cascade persist must save both child signoff records");

            // Test orphan removal: Remove signoff2 from parent collection
            request.getSignoffs().remove(1);
            request = leaveRequestRepository.saveAndFlush(request);

            List<LeaveRequestSignoff> remainingSignoffs = leaveRequestSignoffRepository.findByLeaveRequestId(requestId);
            assertEquals(1, remainingSignoffs.size(), "Orphan removal must delete removed signoff from database");

            // Test cascade delete: Deleting parent deletes remaining children
            leaveRequestRepository.delete(request);
            leaveRequestRepository.flush();

            List<LeaveRequestSignoff> afterDeleteSignoffs = leaveRequestSignoffRepository.findByLeaveRequestId(requestId);
            assertTrue(afterDeleteSignoffs.isEmpty(), "Deleting LeaveRequest must cascade and remove all associated signoffs");
        }

        @Test
        @DisplayName("Relationship Audit: ManyToMany User <-> Role and Role <-> Permission mapping integrity")
        void testManyToManyRolePermissionMapping() {
            Role customRole = new Role("AUDIT_SPECIALIST_" + runId);
            Permission customPerm = new Permission("AUDIT_EXECUTE_" + runId, "Execute deep audits");
            customPerm = permissionRepository.saveAndFlush(customPerm);

            customRole.getPermissions().add(customPerm);
            customRole = roleRepository.saveAndFlush(customRole);

            User testUser = new User();
            testUser.setEmail("spec-" + runId + "@enterprise.com");
            testUser.setPassword(passwordEncoder.encode("Pass1234!"));
            testUser.setActive(true);
            testUser.getRoles().add(customRole);
            testUser = userRepository.saveAndFlush(testUser);

            // Verify mapping via JDBC
            Integer userRoleCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_roles WHERE user_id = ? AND role_id = ?",
                    Integer.class, testUser.getId(), customRole.getId());
            assertEquals(1, userRoleCount, "user_roles join table must contain row linking user and role");

            Integer rolePermCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM role_permissions WHERE role_id = ? AND permission_id = ?",
                    Integer.class, customRole.getId(), customPerm.getId());
            assertEquals(1, rolePermCount, "role_permissions join table must contain row linking role and permission");
        }

        @Test
        @DisplayName("Optimistic Locking: Version column increments and prevents lost updates on LeaveRequest")
        void testOptimisticLockingOnLeaveRequest() {
            LeaveRequest request = new LeaveRequest();
            request.setEmployee(adminEmployee);
            request.setLeaveType(annualLeave);
            request.setStartDate(LocalDate.now().plusDays(10));
            request.setEndDate(LocalDate.now().plusDays(12));
            request.setStatus(LeaveStatus.PENDING);
            request.setReason("Initial reason");
            request = leaveRequestRepository.saveAndFlush(request);

            Long version0 = request.getVersion();
            assertNotNull(version0, "Persisted entity must have initial version 0");

            request.setReason("Updated reason");
            request = leaveRequestRepository.saveAndFlush(request);

            assertEquals(version0 + 1, request.getVersion(), "JPA update must increment version to 1");

            // Simulate concurrent stale update directly via JDBC
            jdbcTemplate.update("UPDATE leave_requests SET version = ? WHERE id = ?", version0 + 10, request.getId());
            entityManager.detach(request);

            LeaveRequest staleRequest = request;
            staleRequest.setReason("Conflicting concurrent update");
            assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
                leaveRequestRepository.saveAndFlush(staleRequest);
            }, "Stale version update must throw ObjectOptimisticLockingFailureException");
        }

        @Test
        @DisplayName("Optimistic Locking: Version column increments and prevents lost updates on LeaveBalance")
        void testOptimisticLockingOnLeaveBalance() {
            LeaveBalance balance = new LeaveBalance();
            balance.setEmployee(adminEmployee);
            balance.setLeaveType(annualLeave);
            balance.setYear(LocalDate.now().getYear());
            balance.setTotal(25);
            balance.setAvailable(25);
            balance.setUsed(0);
            balance = leaveBalanceRepository.saveAndFlush(balance);

            Long version0 = balance.getVersion();
            assertNotNull(version0, "LeaveBalance must have version initialized");

            balance.setUsed(5);
            balance.setAvailable(20);
            balance = leaveBalanceRepository.saveAndFlush(balance);

            assertEquals(version0 + 1, balance.getVersion(), "LeaveBalance update must increment version");
        }
    }

    // =========================================================================
    // SECTION 4: API-DATABASE INTEGRITY BOUNDARY AUDIT
    // =========================================================================
    @Nested
    @DisplayName("4. API-Database Integrity Boundary Audit (HTTP Constraint Translation)")
    class ApiDatabaseIntegrityBoundaryAudit {

        @Test
        @DisplayName("API Constraint Audit: Duplicate Department Name returns 409 Conflict")
        void testApiDuplicateDepartmentNameReturns409() throws Exception {
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
        @DisplayName("API Constraint Audit: Duplicate Leave Type Name returns 409 Conflict")
        void testApiDuplicateLeaveTypeNameReturns409() throws Exception {
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
        @DisplayName("API Constraint Audit: Duplicate Registration Email returns 409 Conflict")
        void testApiDuplicateUserEmailReturns409() throws Exception {
            RegisterRequestDTO dto = new RegisterRequestDTO();
            dto.setFirstName("Dup");
            dto.setLastName("User");
            dto.setEmail(adminUser.getEmail()); // Existing email
            dto.setPassword("NewPassword123!");
            dto.setPhone("+12345678901");
            dto.setEmployeeCode("EMP-DUP-" + runId);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value(containsString("Email already exists")));
        }

        @Test
        @DisplayName("API Constraint Audit: Deleting Department with active employees returns 409 Conflict")
        void testApiDeleteDepartmentWithActiveEmployeesReturns409() throws Exception {
            mockMvc.perform(delete("/api/departments/" + engineeringDept.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));

            assertTrue(departmentRepository.findById(engineeringDept.getId()).isPresent(),
                    "Referential integrity must preserve department with active employees");
        }

        @Test
        @DisplayName("API Constraint Audit: Deleting Leave Type with active balances returns 400 Bad Request")
        void testApiDeleteLeaveTypeWithActiveBalancesReturns400() throws Exception {
            LeaveBalance balance = new LeaveBalance();
            balance.setEmployee(adminEmployee);
            balance.setLeaveType(annualLeave);
            balance.setYear(LocalDate.now().getYear());
            balance.setTotal(25);
            balance.setAvailable(25);
            balance.setUsed(0);
            leaveBalanceRepository.saveAndFlush(balance);

            mockMvc.perform(delete("/api/leave-types/" + annualLeave.getId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("existing balances or requests")));

            assertTrue(leaveTypeRepository.findById(annualLeave.getId()).isPresent(),
                    "Referential integrity must preserve leave type with active allocations");
        }

        @Test
        @DisplayName("API Constraint Audit: Employee referencing non-existent department returns 404 Not Found")
        void testApiEmployeeInvalidDepartmentFkReturns404() throws Exception {
            EmployeeRequestDTO dto = new EmployeeRequestDTO();
            dto.setFirstName("Invalid");
            dto.setLastName("Fk");
            dto.setEmail("invalid.fk." + runId + "@enterprise.com");
            dto.setEmployeeCode("EMP-INV-" + runId);
            dto.setStatus("ACTIVE");
            dto.setSalary(new BigDecimal("55000.00"));
            dto.setDepartmentId(9999999L); // Non-existent foreign key

            mockMvc.perform(post("/api/employees")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").value(containsString("Department not found")));
        }
    }

    // =========================================================================
    // HELPER ASSERTION METHODS
    // =========================================================================
    private void assertTableHasUniqueIndex(DatabaseMetaData metaData, String tableName, String columnName) throws Exception {
        try (ResultSet rs = metaData.getIndexInfo(null, "PUBLIC", tableName, false, false)) {
            while (rs.next()) {
                boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                String col = rs.getString("COLUMN_NAME");
                if (!nonUnique && col != null && col.equalsIgnoreCase(columnName)) {
                    return; // Found
                }
            }
        }
        fail("Expected unique constraint or index on " + tableName + "(" + columnName + ") but none found");
    }

    private void assertCompositeUniqueIndex(DatabaseMetaData metaData, String tableName, Set<String> expectedColumns) throws Exception {
        Map<String, Set<String>> uniqueIndexes = new HashMap<>();
        try (ResultSet rs = metaData.getIndexInfo(null, "PUBLIC", tableName, false, false)) {
            while (rs.next()) {
                boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                String indexName = rs.getString("INDEX_NAME");
                String col = rs.getString("COLUMN_NAME");
                if (!nonUnique && indexName != null && col != null) {
                    uniqueIndexes.computeIfAbsent(indexName, k -> new HashSet<>()).add(col.toUpperCase());
                }
            }
        }

        boolean found = uniqueIndexes.values().stream().anyMatch(cols -> cols.containsAll(expectedColumns));
        assertTrue(found, "Expected composite unique index on " + tableName + " covering columns " + expectedColumns +
                " but found unique indexes: " + uniqueIndexes);
    }

    private void assertColumnNotNull(DatabaseMetaData metaData, String tableName, String columnName) throws Exception {
        try (ResultSet rs = metaData.getColumns(null, "PUBLIC", tableName, columnName)) {
            if (rs.next()) {
                String isNullable = rs.getString("IS_NULLABLE");
                assertEquals("NO", isNullable.toUpperCase(),
                        "Column " + tableName + "." + columnName + " must be defined as NOT NULL in schema");
                return;
            }
        }
        fail("Column " + tableName + "." + columnName + " was not found in schema metadata");
    }
}

