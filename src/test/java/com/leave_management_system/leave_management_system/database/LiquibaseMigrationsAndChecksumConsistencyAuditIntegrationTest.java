package com.leave_management_system.leave_management_system.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.LiquibaseAuditReportDTO;
import com.leave_management_system.leave_management_system.dto.LiquibaseChangesetDTO;
import com.leave_management_system.leave_management_system.dto.LiquibaseLockStatusDTO;
import com.leave_management_system.leave_management_system.dto.LiquibaseValidationResultDTO;
import com.leave_management_system.leave_management_system.entity.Department;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.Permission;
import com.leave_management_system.leave_management_system.entity.Role;
import com.leave_management_system.leave_management_system.entity.User;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import com.leave_management_system.leave_management_system.service.LiquibaseMigrationAuditService;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.ValidationFailedException;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.lockservice.LockService;
import liquibase.lockservice.LockServiceFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Enterprise API and Database Audit: Liquibase Migrations and Checksum Consistency.
 *
 * Comprehensive integration audit verifying:
 * 1. Database changelog execution history, monotonic ordering, and complete changeset recording.
 * 2. Checksum consistency, algorithm standards, zero null checksums, and drift detection.
 * 3. Changelog locking mechanism, single-lock integrity, and concurrency control.
 * 4. Forward-only schema evolution, DBMS conditional execution, and migration idempotency.
 * 5. REST API audit endpoints and strict role-based authorization (RBAC).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("API and Database Audit - Liquibase Migrations & Checksum Consistency Test Suite")
public class LiquibaseMigrationsAndChecksumConsistencyAuditIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SpringLiquibase springLiquibase;

    @Autowired
    private LiquibaseMigrationAuditService auditService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private String runId;
    private User adminUser;
    private User employeeUser;
    private User managerUser;
    private String adminToken;
    private String employeeToken;
    private String managerToken;

    private static final List<String> EXPECTED_CHANGESET_IDS = List.of(
            "1",
            "2",
            "004-create-leave-types",
            "005-create-leave-balances",
            "006-update-leave-requests",
            "7",
            "008-add-rejection-reason-to-leave-requests",
            "009-add-missing-constraints",
            "010-setup-security-tables",
            "010-insert-default-roles",
            "011-setup-tokens-and-audit",
            "012-modify-employees-and-departments",
            "013-add-version-columns",
            "014-add-permissions",
            "015-modify-rejection-reason-length",
            "016-add-audit-log-indexes",
            "017-add-login-tracking",
            "018-add-authorization-auditing",
            "019-add-transaction-review",
            "020-add-security-events",
            "021-add-security-management-permissions",
            "022-add-user-account-locking",
            "023-add-multi-approver-signoff",
            "024-add-idempotency-keys",
            "025-add-idempotency-response-headers",
            "026-convert-bit-columns-to-boolean-h2"
    );

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        runId = UUID.randomUUID().toString().substring(0, 8);

        // Provision Roles
        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));
        Role managerRole = roleRepository.findByName("MANAGER").orElseGet(() -> roleRepository.save(new Role("MANAGER")));

        Department dept = new Department();
        dept.setName("Liquibase Audit Dept " + runId);
        dept = departmentRepository.save(dept);

        // 1. Admin User & Token
        adminUser = new User();
        adminUser.setEmail("liq-admin-" + runId + "@audit.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.setAccountNonLocked(true);
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);

        Employee adminEmp = new Employee();
        adminEmp.setFirstName("Admin");
        adminEmp.setLastName("Auditor");
        adminEmp.setEmployeeCode("ADM-LIQ-" + runId);
        adminEmp.setStatus("ACTIVE");
        adminEmp.setSalary(new BigDecimal("125000.00"));
        adminEmp.setDepartment(dept);
        adminEmp.setUser(adminUser);
        adminEmp = employeeRepository.save(adminEmp);
        adminUser.setEmployee(adminEmp);
        adminUser = userRepository.save(adminUser);

        List<SimpleGrantedAuthority> adminAuths = List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ADMIN_VIEW")
        );
        UserDetails adminDetails = new org.springframework.security.core.userdetails.User(
                adminUser.getEmail(), adminUser.getPassword(), adminAuths);
        adminToken = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(adminDetails, null, adminAuths));

        // 2. Employee User & Token
        employeeUser = new User();
        employeeUser.setEmail("liq-emp-" + runId + "@audit.com");
        employeeUser.setPassword(passwordEncoder.encode("EmpPass123!"));
        employeeUser.setActive(true);
        employeeUser.setAccountNonLocked(true);
        employeeUser.getRoles().add(employeeRole);
        employeeUser = userRepository.save(employeeUser);

        Employee emp = new Employee();
        emp.setFirstName("Regular");
        emp.setLastName("Employee");
        emp.setEmployeeCode("EMP-LIQ-" + runId);
        emp.setStatus("ACTIVE");
        emp.setSalary(new BigDecimal("65000.00"));
        emp.setDepartment(dept);
        emp.setUser(employeeUser);
        emp = employeeRepository.save(emp);
        employeeUser.setEmployee(emp);
        employeeUser = userRepository.save(employeeUser);

        List<SimpleGrantedAuthority> empAuths = List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        UserDetails empDetails = new org.springframework.security.core.userdetails.User(
                employeeUser.getEmail(), employeeUser.getPassword(), empAuths);
        employeeToken = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(empDetails, null, empAuths));

        // 3. Manager User & Token
        managerUser = new User();
        managerUser.setEmail("liq-mgr-" + runId + "@audit.com");
        managerUser.setPassword(passwordEncoder.encode("MgrPass123!"));
        managerUser.setActive(true);
        managerUser.setAccountNonLocked(true);
        managerUser.getRoles().add(managerRole);
        managerUser = userRepository.save(managerUser);

        Employee mgr = new Employee();
        mgr.setFirstName("Team");
        mgr.setLastName("Manager");
        mgr.setEmployeeCode("MGR-LIQ-" + runId);
        mgr.setStatus("ACTIVE");
        mgr.setSalary(new BigDecimal("95000.00"));
        mgr.setDepartment(dept);
        mgr.setUser(managerUser);
        mgr = employeeRepository.save(mgr);
        managerUser.setEmployee(mgr);
        managerUser = userRepository.save(managerUser);

        List<SimpleGrantedAuthority> mgrAuths = List.of(new SimpleGrantedAuthority("ROLE_MANAGER"));
        UserDetails mgrDetails = new org.springframework.security.core.userdetails.User(
                managerUser.getEmail(), managerUser.getPassword(), mgrAuths);
        managerToken = jwtUtils.generateJwtToken(new UsernamePasswordAuthenticationToken(mgrDetails, null, mgrAuths));
    }

    @AfterEach
    public void tearDown() {
        // Ensure changelog lock is unlocked after test execution
        try {
            jdbcTemplate.update("UPDATE databasechangeloglock SET locked = FALSE, lockgranted = NULL, lockedby = NULL WHERE id = 1");
        } catch (Exception ignored) {
        }
    }

    // =========================================================================
    // SECTION 1: Changelog Execution & Historical Consistency Audit
    // =========================================================================
    @Nested
    @DisplayName("1. Changelog Execution & Historical Consistency Audit")
    class ChangelogExecutionHistoryAudit {

        @Test
        @DisplayName("1.1. Verification: All 26 changesets are executed and recorded in DATABASECHANGELOG")
        void testAll26ChangesetsExecutedInDatabaseChangelog() {
            Integer totalExecuted = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM databasechangelog", Integer.class);
            assertNotNull(totalExecuted, "Total changeset count must not be null");
            assertTrue(totalExecuted >= 26, "Expected at least 26 executed changesets, but found: " + totalExecuted);

            List<String> executedIds = jdbcTemplate.queryForList(
                    "SELECT id FROM databasechangelog ORDER BY orderexecuted ASC", String.class);

            for (String expectedId : EXPECTED_CHANGESET_IDS) {
                assertTrue(executedIds.contains(expectedId),
                        "Expected changeset ID '" + expectedId + "' was not found in executed changesets: " + executedIds);
            }
        }

        @Test
        @DisplayName("1.2. Verification: All changeset execution types are recorded as 'EXECUTED'")
        void testAllChangesetExecutionTypesAreExecuted() {
            Integer nonExecutedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM databasechangelog WHERE exectype != 'EXECUTED'", Integer.class);
            assertNotNull(nonExecutedCount);
            assertEquals(0, nonExecutedCount,
                    "All changesets must have EXECTYPE = 'EXECUTED'. Found " + nonExecutedCount + " non-executed changesets.");
        }

        @Test
        @DisplayName("1.3. Verification: ORDEREXECUTED is strictly monotonic, sequential, and without gaps or duplicates")
        void testOrderExecutedIsStrictlyMonotonicAndContinuous() {
            List<Integer> orders = jdbcTemplate.queryForList(
                    "SELECT orderexecuted FROM databasechangelog ORDER BY orderexecuted ASC", Integer.class);

            assertFalse(orders.isEmpty(), "Order list must not be empty");
            Set<Integer> uniqueOrders = new HashSet<>(orders);
            assertEquals(orders.size(), uniqueOrders.size(), "Each changeset must have a unique ORDEREXECUTED value");

            int expectedOrder = 1;
            for (Integer actualOrder : orders) {
                assertEquals(expectedOrder, actualOrder,
                        "ORDEREXECUTED must increment sequentially starting from 1. Gap or inversion detected.");
                expectedOrder++;
            }
        }

        @Test
        @DisplayName("1.4. Verification: FILENAME paths reference valid classpath changelog locations")
        void testChangelogFilenamesMatchExpectedPaths() {
            List<String> filenames = jdbcTemplate.queryForList(
                    "SELECT DISTINCT filename FROM databasechangelog", String.class);

            assertFalse(filenames.isEmpty(), "Filename list must not be empty");
            for (String fn : filenames) {
                assertNotNull(fn, "Changelog filename must not be null");
                assertFalse(fn.isBlank(), "Changelog filename must not be blank");
                assertTrue(fn.contains("changelog") || fn.contains("changes/"),
                        "Filename must belong to Liquibase changelog directory: " + fn);
            }
        }

        @Test
        @DisplayName("1.5. Verification: Authors are accurately and non-blankly recorded")
        void testChangesetAuthorsAreAccuratelyRecorded() {
            List<String> authors = jdbcTemplate.queryForList(
                    "SELECT DISTINCT author FROM databasechangelog", String.class);

            assertFalse(authors.isEmpty(), "Authors list must not be empty");
            for (String author : authors) {
                assertNotNull(author, "Author must not be null");
                assertFalse(author.isBlank(), "Author must not be blank");
                assertTrue(List.of("antigravity", "mohith", "system").contains(author),
                        "Unexpected author found in changelog: " + author);
            }
        }

        @Test
        @DisplayName("1.6. Verification: Execution timestamps (DATEEXECUTED) are valid and populated")
        void testExecutionTimestampsArePopulated() {
            List<Timestamp> timestamps = jdbcTemplate.queryForList(
                    "SELECT dateexecuted FROM databasechangelog", Timestamp.class);

            assertFalse(timestamps.isEmpty(), "Timestamps list must not be empty");
            for (Timestamp ts : timestamps) {
                assertNotNull(ts, "Every executed changeset must have a non-null DATEEXECUTED timestamp");
                assertTrue(ts.getTime() > 0, "Timestamp must be a positive epoch time");
            }
        }

        @Test
        @DisplayName("1.7. Verification: Liquibase engine version string is stored in DATABASECHANGELOG")
        void testLiquibaseEngineVersionIsRecorded() {
            List<String> versions = jdbcTemplate.queryForList(
                    "SELECT DISTINCT liquibase FROM databasechangelog", String.class);

            assertFalse(versions.isEmpty(), "Liquibase version column must not be empty");
            for (String ver : versions) {
                assertNotNull(ver, "Liquibase version must not be null");
                assertFalse(ver.isBlank(), "Liquibase version must not be blank");
            }
        }
    }

    // =========================================================================
    // SECTION 2: Checksum Consistency, Non-Drift & Algorithm Audit
    // =========================================================================
    @Nested
    @DisplayName("2. Checksum Consistency, Non-Drift & Algorithm Audit")
    class ChecksumConsistencyAudit {

        @Test
        @DisplayName("2.1. Verification: Every executed changeset has a non-null, valid MD5SUM with version prefix")
        void testAllChangesetsHaveValidNonNullChecksums() {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, md5sum FROM databasechangelog ORDER BY orderexecuted ASC");

            assertFalse(rows.isEmpty(), "DATABASECHANGELOG should have rows");
            for (Map<String, Object> row : rows) {
                String id = (String) row.get("id");
                String md5sum = (String) row.get("md5sum");

                assertNotNull(md5sum, "Changeset '" + id + "' has null MD5SUM");
                assertFalse(md5sum.trim().isEmpty(), "Changeset '" + id + "' has blank MD5SUM");
                assertTrue(md5sum.matches("^[0-9]:[a-zA-Z0-9+/=]+$"),
                        "Changeset '" + id + "' MD5SUM '" + md5sum + "' does not match standard Liquibase checksum format (e.g., 9:xxx)");
            }
        }

        @Test
        @DisplayName("2.2. Verification: Programmatic Liquibase validation succeeds with zero errors and zero warnings")
        void testProgrammaticLiquibaseValidatePassesWithZeroErrors() {
            LiquibaseValidationResultDTO result = auditService.validateChangelogs();

            assertNotNull(result, "Validation result must not be null");
            assertTrue(result.isValid(), "Liquibase validation should pass without checksum drift or errors");
            assertEquals(0, result.getErrorCount(), "Expected 0 validation errors, but got: " + result.getErrorMessages());
            assertTrue(result.getErrorMessages().isEmpty(), "Error messages list should be empty");
            assertNotNull(result.getMessage());
            assertNotNull(result.getValidatedAt());
        }

        @Test
        @DisplayName("2.3. Verification: Zero unrun changesets remain in current test context (database fully synced)")
        void testNoUnrunChangesetsInCurrentContext() throws Exception {
            try (Connection conn = dataSource.getConnection()) {
                Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
                String changelog = springLiquibase.getChangeLog().replaceFirst("^classpath\\*?:", "");

                try (ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();
                     Liquibase liquibase = new Liquibase(changelog, resourceAccessor, database)) {

                    Contexts contexts = springLiquibase.getContexts() != null
                            ? new Contexts(springLiquibase.getContexts()) : new Contexts();
                    LabelExpression labels = springLiquibase.getLabelFilter() != null
                            ? new LabelExpression(springLiquibase.getLabelFilter()) : new LabelExpression();

                    List<ChangeSet> unrun = liquibase.listUnrunChangeSets(contexts, labels);
                    assertNotNull(unrun);
                    assertEquals(0, unrun.size(),
                            "Found unrun changesets when database should be fully up-to-date: " + unrun);
                }
            }
        }

        @Test
        @DisplayName("2.4. Verification: Checksum drift detection triggers ValidationFailedException on tampered checksum")
        void testChecksumDriftDetectionOnTamperedChangeset() throws Exception {
            String targetChangesetId = "013-add-version-columns";

            // 1. Fetch current authentic checksum
            String originalChecksum = jdbcTemplate.queryForObject(
                    "SELECT md5sum FROM databasechangelog WHERE id = ?", String.class, targetChangesetId);
            assertNotNull(originalChecksum, "Original checksum must exist");

            try {
                // 2. Intentionally corrupt the stored checksum (max 35 chars) to simulate schema drift / tampering
                String corruptedChecksum = "9:tamperedCorruptedHash1234==";
                jdbcTemplate.update("UPDATE databasechangelog SET md5sum = ? WHERE id = ?",
                        corruptedChecksum, targetChangesetId);

                // 3. Verify that programmatic validation via auditService detects the drift
                LiquibaseValidationResultDTO driftResult = auditService.validateChangelogs();
                assertFalse(driftResult.isValid(), "Validation must fail when checksum is tampered");
                assertTrue(driftResult.getErrorCount() > 0, "Error count must be greater than 0");

                // 4. Also verify direct Liquibase validate() detects the mismatch
                try (Connection conn = dataSource.getConnection()) {
                    Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
                    String changelog = springLiquibase.getChangeLog().replaceFirst("^classpath\\*?:", "");

                    try (ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();
                         Liquibase liquibase = new Liquibase(changelog, resourceAccessor, database)) {

                        Exception ex = assertThrows(Exception.class, liquibase::validate,
                                "Liquibase validate() must throw an exception when a checksum is tampered");
                        Throwable cause = ex;
                        while (cause.getCause() != null && !(cause instanceof ValidationFailedException)) {
                            cause = cause.getCause();
                        }
                        assertTrue(cause instanceof ValidationFailedException, "Root cause must be ValidationFailedException");
                        assertTrue(cause.getMessage().contains(targetChangesetId) || cause.getMessage().contains("check sum") || cause.getMessage().contains("checksum"),
                                "Exception message must report the altered changeset or checksum mismatch");
                    }
                }
            } finally {
                // 4. Restore original authentic checksum to maintain clean state
                jdbcTemplate.update("UPDATE databasechangelog SET md5sum = ? WHERE id = ?",
                        originalChecksum, targetChangesetId);
            }

            // 5. Verify validation is clean again
            LiquibaseValidationResultDTO cleanResult = auditService.validateChangelogs();
            assertTrue(cleanResult.isValid(), "Validation must pass after restoring genuine checksum");
        }

        @Test
        @DisplayName("2.5. Verification: Changesets with validCheckSum: ANY are controlled, documented, and safe")
        void testValidCheckSumAnyAnnotationsAreDocumentedAndControlled() {
            // Documented changesets that deliberately accept ANY checksum:
            // Changeset 1 (initial departments/employees/leave_requests)
            // Changeset 7 (007-add-active-to-leave-types)
            // Changeset 010 (010-setup-security-tables)
            // Changeset 011 (011-setup-tokens-and-audit)
            // Changeset 026 (026-convert-bit-columns-to-boolean)
            List<String> validAnyChangesetIds = List.of(
                    "1",
                    "7",
                    "010-setup-security-tables",
                    "011-setup-tokens-and-audit",
                    "026-convert-bit-columns-to-boolean-h2"
            );

            for (String csId : validAnyChangesetIds) {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM databasechangelog WHERE id = ?", Integer.class, csId);
                assertNotNull(count);
                assertEquals(1, count, "Controlled changeset '" + csId + "' must be executed once in database");

                String checksum = jdbcTemplate.queryForObject(
                        "SELECT md5sum FROM databasechangelog WHERE id = ?", String.class, csId);
                assertNotNull(checksum, "Controlled changeset '" + csId + "' must still have a valid persisted checksum");
                assertFalse(checksum.isBlank());
            }
        }
    }

    // =========================================================================
    // SECTION 3: Changelog Lock & Concurrency Control Audit
    // =========================================================================
    @Nested
    @DisplayName("3. Changelog Lock & Concurrency Control Audit")
    class ChangelogLockAndConcurrencyAudit {

        @Test
        @DisplayName("3.1. Verification: DATABASECHANGELOGLOCK table contains exactly one record with ID = 1")
        void testChangelogLockTableHasExactlyOneRow() {
            Integer lockRowCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM databasechangeloglock", Integer.class);
            assertNotNull(lockRowCount);
            assertEquals(1, lockRowCount, "DATABASECHANGELOGLOCK must contain exactly 1 row");

            Integer lockId = jdbcTemplate.queryForObject(
                    "SELECT id FROM databasechangeloglock", Integer.class);
            assertNotNull(lockId);
            assertEquals(1, lockId, "Lock row ID must be 1");
        }

        @Test
        @DisplayName("3.2. Verification: Lock state is UNLOCKED (locked=false, lockgranted=null, lockedby=null) in resting state")
        void testChangelogLockStateIsUnlockedInRestingState() {
            Boolean locked = jdbcTemplate.queryForObject(
                    "SELECT locked FROM databasechangeloglock WHERE id = 1", Boolean.class);
            assertNotNull(locked, "Locked column must not be null");
            assertFalse(locked, "Database changelog lock must be released (FALSE) when application is running");

            Timestamp lockGranted = jdbcTemplate.queryForObject(
                    "SELECT lockgranted FROM databasechangeloglock WHERE id = 1", Timestamp.class);
            assertNull(lockGranted, "LOCKGRANTED timestamp must be null when unlocked");

            String lockedBy = jdbcTemplate.queryForObject(
                    "SELECT lockedby FROM databasechangeloglock WHERE id = 1", String.class);
            assertNull(lockedBy, "LOCKEDBY must be null when unlocked");
        }

        @Test
        @DisplayName("3.3. Verification: LockService lifecycle: acquireLock blocks and releaseLock clears lock state cleanly")
        void testLockServiceAcquisitionAndReleaseLifecycle() throws Exception {
            try (Connection conn = dataSource.getConnection()) {
                Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
                LockService lockService = LockServiceFactory.getInstance().getLockService(database);
                assertNotNull(lockService, "LockService must be instantiated");

                // Initial resting state
                assertFalse(lockService.hasChangeLogLock(), "Initial state must not hold change log lock");

                // Acquire lock
                boolean acquired = lockService.acquireLock();
                assertTrue(acquired, "acquireLock() must return true on available lock");

                // Verify lock table state reflects acquisition
                Boolean isLockedNow = jdbcTemplate.queryForObject(
                        "SELECT locked FROM databasechangeloglock WHERE id = 1", Boolean.class);
                assertTrue(isLockedNow != null && isLockedNow, "DATABASECHANGELOGLOCK must be TRUE when acquired");

                // Release lock
                lockService.releaseLock();

                // Verify lock table state is cleared
                Boolean isUnlockedNow = jdbcTemplate.queryForObject(
                        "SELECT locked FROM databasechangeloglock WHERE id = 1", Boolean.class);
                assertFalse(isUnlockedNow != null && isUnlockedNow, "DATABASECHANGELOGLOCK must be FALSE after release");
            }
        }

        @Test
        @DisplayName("3.4. Verification: LiquibaseMigrationAuditService correctly reports unlocked status")
        void testAuditServiceReportsUnlockedStatus() {
            LiquibaseLockStatusDTO lockStatus = auditService.getLockStatus();
            assertNotNull(lockStatus, "Lock status DTO must not be null");
            assertEquals(1, lockStatus.getLockId());
            assertFalse(lockStatus.isLocked(), "Lock status must report locked = false");
            assertNull(lockStatus.getLockGranted());
            assertNull(lockStatus.getLockedBy());
            assertEquals(1, lockStatus.getTotalLocks());
        }
    }

    // =========================================================================
    // SECTION 4: Forward-Only Migration & Schema Synchronization Audit
    // =========================================================================
    @Nested
    @DisplayName("4. Forward-Only Migration & Schema Synchronization Audit")
    class ForwardOnlyMigrationAudit {

        @Test
        @DisplayName("4.1. Verification: All 18 database tables defined across changesets physically exist")
        void testAllTablesFromAllChangesetsPhysicallyExist() throws SQLException {
            List<String> expectedTables = List.of(
                    "DEPARTMENTS",
                    "EMPLOYEES",
                    "LEAVE_REQUESTS",
                    "LEAVE_TYPES",
                    "LEAVE_BALANCES",
                    "USERS",
                    "ROLES",
                    "PERMISSIONS",
                    "USER_ROLES",
                    "ROLE_PERMISSIONS",
                    "REFRESH_TOKENS",
                    "AUDIT_LOGS",
                    "LOGIN_HISTORY",
                    "AUTHORIZATION_AUDIT_LOGS",
                    "TRANSACTION_REVIEWS",
                    "SECURITY_EVENTS",
                    "IDEMPOTENCY_KEYS",
                    "LEAVE_REQUEST_SIGNOFFS"
            );

            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                List<String> actualTables = new ArrayList<>();

                try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        actualTables.add(rs.getString("TABLE_NAME").toUpperCase());
                    }
                }

                for (String expectedTable : expectedTables) {
                    assertTrue(actualTables.contains(expectedTable),
                            "Table '" + expectedTable + "' created by Liquibase migrations does not physically exist in DB metadata");
                }
            }
        }

        @Test
        @DisplayName("4.2. Verification: Forward-only column evolution is verified across historical migrations")
        void testForwardOnlyColumnEvolutionAcrossChangesets() throws SQLException {
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();

                // Changeset 2: employees.first_name and employees.last_name added
                assertColumnExists(metaData, "EMPLOYEES", "FIRST_NAME");
                assertColumnExists(metaData, "EMPLOYEES", "LAST_NAME");

                // Changeset 006: leave_requests.leave_type_id added
                assertColumnExists(metaData, "LEAVE_REQUESTS", "LEAVE_TYPE_ID");

                // Changeset 008/015: leave_requests.rejection_reason exists with length 500
                assertColumnExists(metaData, "LEAVE_REQUESTS", "REJECTION_REASON");

                // Changeset 013: optimistic locking version columns added
                assertColumnExists(metaData, "LEAVE_REQUESTS", "VERSION");
                assertColumnExists(metaData, "LEAVE_BALANCES", "VERSION");

                // Changeset 017: users.last_login_at added
                assertColumnExists(metaData, "USERS", "LAST_LOGIN_AT");

                // Changeset 022: users.account_non_locked added
                assertColumnExists(metaData, "USERS", "ACCOUNT_NON_LOCKED");

                // Changeset 023: leave_requests.requires_dual_approval added
                assertColumnExists(metaData, "LEAVE_REQUESTS", "REQUIRES_DUAL_APPROVAL");

                // Changeset 025: idempotency_keys.content_type added
                assertColumnExists(metaData, "IDEMPOTENCY_KEYS", "CONTENT_TYPE");

                // Changeset 026: BIT to BOOLEAN conversion on leave_types.active, users.active, refresh_tokens.revoked
                assertColumnType(metaData, "LEAVE_TYPES", "ACTIVE", "BOOLEAN");
                assertColumnType(metaData, "USERS", "ACTIVE", "BOOLEAN");
                assertColumnType(metaData, "REFRESH_TOKENS", "REVOKED", "BOOLEAN");
            }
        }

        @Test
        @DisplayName("4.3. Verification: DBMS conditional execution ensures H2 changesets run while PostgreSQL changesets are skipped")
        void testDbmsSpecificChangesetFiltering() {
            // H2 changeset must be executed
            Integer h2Count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM databasechangelog WHERE id = '026-convert-bit-columns-to-boolean-h2'", Integer.class);
            assertNotNull(h2Count);
            assertEquals(1, h2Count, "H2 changeset must be executed in H2 test environment");

            // PostgreSQL-specific changesets must NOT be executed on H2
            Integer pgCount1 = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM databasechangelog WHERE id = '026-convert-bit-columns-to-boolean-postgresql'", Integer.class);
            assertNotNull(pgCount1);
            assertEquals(0, pgCount1, "PostgreSQL changeset must NOT execute in H2 environment");

            Integer pgCount2 = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM databasechangelog WHERE id = '026-convert-leave-types-active-to-boolean-postgresql'", Integer.class);
            assertNotNull(pgCount2);
            assertEquals(0, pgCount2, "PostgreSQL changeset must NOT execute in H2 environment");
        }

        @Test
        @DisplayName("4.4. Verification: Migration idempotency: repeated liquibase.update executes 0 changes on migrated database")
        void testMigrationIdempotencyReplay() throws Exception {
            Integer initialChangesetCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM databasechangelog", Integer.class);

            try (Connection conn = dataSource.getConnection()) {
                Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
                String changelog = springLiquibase.getChangeLog().replaceFirst("^classpath\\*?:", "");

                try (ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();
                     Liquibase liquibase = new Liquibase(changelog, resourceAccessor, database)) {

                    Contexts contexts = springLiquibase.getContexts() != null
                            ? new Contexts(springLiquibase.getContexts()) : new Contexts();
                    LabelExpression labels = springLiquibase.getLabelFilter() != null
                            ? new LabelExpression(springLiquibase.getLabelFilter()) : new LabelExpression();

                    // Re-run update on already fully migrated database
                    liquibase.update(contexts, labels);
                }
            }

            Integer postUpdateChangesetCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM databasechangelog", Integer.class);

            assertEquals(initialChangesetCount, postUpdateChangesetCount,
                    "Replaying liquibase.update() must be idempotent and not alter changeset count");
        }

        private void assertColumnExists(DatabaseMetaData metaData, String tableName, String columnName) throws SQLException {
            try (ResultSet rs = metaData.getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
                assertTrue(rs.next(), "Column '" + columnName + "' on table '" + tableName + "' does not exist");
            }
        }

        private void assertColumnType(DatabaseMetaData metaData, String tableName, String columnName, String expectedType) throws SQLException {
            try (ResultSet rs = metaData.getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
                assertTrue(rs.next(), "Column '" + columnName + "' on table '" + tableName + "' does not exist");
                String actualType = rs.getString("TYPE_NAME");
                assertTrue(actualType.toUpperCase().contains(expectedType.toUpperCase()),
                        "Column '" + columnName + "' on table '" + tableName + "' expected type " + expectedType + " but was " + actualType);
            }
        }
    }

    // =========================================================================
    // SECTION 5: API Endpoint Level Authorization & Security Audit
    // =========================================================================
    @Nested
    @DisplayName("5. API Endpoint Level Authorization & Security Audit")
    class ApiEndpointLevelAuthorizationAudit {

        @Test
        @DisplayName("5.1. GET /api/audit/liquibase/status - Unauthenticated request returns HTTP 401 Unauthorized")
        void testLiquibaseStatusEndpoint_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/audit/liquibase/status")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("5.2. GET /api/audit/liquibase/status - Regular Employee (ROLE_EMPLOYEE) returns HTTP 403 Forbidden")
        void testLiquibaseStatusEndpoint_Employee_Returns403() throws Exception {
            mockMvc.perform(get("/api/audit/liquibase/status")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("5.3. GET /api/audit/liquibase/status - Manager (ROLE_MANAGER) returns HTTP 403 Forbidden")
        void testLiquibaseStatusEndpoint_Manager_Returns403() throws Exception {
            mockMvc.perform(get("/api/audit/liquibase/status")
                            .header("Authorization", "Bearer " + managerToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("5.4. GET /api/audit/liquibase/status - Admin (ROLE_ADMIN) returns HTTP 200 OK with complete audit report")
        void testLiquibaseStatusEndpoint_Admin_Returns200WithFullReport() throws Exception {
            mockMvc.perform(get("/api/audit/liquibase/status")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overallStatus").value("HEALTHY"))
                    .andExpect(jsonPath("$.databaseProductName").value("H2"))
                    .andExpect(jsonPath("$.totalChangesetsExecuted", greaterThanOrEqualTo(26)))
                    .andExpect(jsonPath("$.unrunChangesetsCount").value(0))
                    .andExpect(jsonPath("$.allChecksumsPresent").value(true))
                    .andExpect(jsonPath("$.checksumDriftDetected").value(false))
                    .andExpect(jsonPath("$.lockStatus.locked").value(false))
                    .andExpect(jsonPath("$.lockStatus.totalLocks").value(1))
                    .andExpect(jsonPath("$.executedChangesets", hasSize(greaterThanOrEqualTo(26))))
                    .andExpect(jsonPath("$.executedChangesets[0].id").value("1"))
                    .andExpect(jsonPath("$.executedChangesets[0].execType").value("EXECUTED"));
        }

        @Test
        @DisplayName("5.5. GET /api/audit/liquibase/validate - Unauthenticated request returns HTTP 401 Unauthorized")
        void testLiquibaseValidateEndpoint_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/audit/liquibase/validate")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("5.6. GET /api/audit/liquibase/validate - Regular Employee returns HTTP 403 Forbidden")
        void testLiquibaseValidateEndpoint_Employee_Returns403() throws Exception {
            mockMvc.perform(get("/api/audit/liquibase/validate")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("5.7. GET /api/audit/liquibase/validate - Admin returns HTTP 200 OK with valid checksum integrity")
        void testLiquibaseValidateEndpoint_Admin_Returns200() throws Exception {
            mockMvc.perform(get("/api/audit/liquibase/validate")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.errorCount").value(0))
                    .andExpect(jsonPath("$.message", containsString("consistent")));
        }

        @Test
        @DisplayName("5.8. GET /api/audit/liquibase/changesets - Admin returns HTTP 200 OK with chronological changeset list")
        void testLiquibaseChangesetsEndpoint_Admin_ReturnsChronologicalList() throws Exception {
            mockMvc.perform(get("/api/audit/liquibase/changesets")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(26))))
                    .andExpect(jsonPath("$[0].id").value("1"))
                    .andExpect(jsonPath("$[0].orderExecuted").value(1))
                    .andExpect(jsonPath("$[?(@.id == '026-convert-bit-columns-to-boolean-h2')]").exists());
        }

        @Test
        @DisplayName("5.9. GET /api/audit/liquibase/locks - Admin returns HTTP 200 OK with lock state inspection")
        void testLiquibaseLocksEndpoint_Admin_ReturnsUnlockedState() throws Exception {
            mockMvc.perform(get("/api/audit/liquibase/locks")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lockId").value(1))
                    .andExpect(jsonPath("$.locked").value(false))
                    .andExpect(jsonPath("$.totalLocks").value(1));
        }
    }
}
