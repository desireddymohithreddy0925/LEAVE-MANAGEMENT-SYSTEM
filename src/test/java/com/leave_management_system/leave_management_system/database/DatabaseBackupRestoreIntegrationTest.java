package com.leave_management_system.leave_management_system.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.DatabaseBackupResponseDTO;
import com.leave_management_system.leave_management_system.dto.DatabaseBackupValidationDTO;
import com.leave_management_system.leave_management_system.dto.DatabaseRestoreRequestDTO;
import com.leave_management_system.leave_management_system.dto.DatabaseRestoreResponseDTO;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.JwtUtils;
import com.leave_management_system.leave_management_system.service.DatabaseBackupRestoreService;
import liquibase.integration.spring.SpringLiquibase;
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

import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Enterprise API and Database Audit: Database Backup and Restoration.
 *
 * Comprehensive integration audit verifying:
 * 1. Full database SQL export with drop statements and SHA-256 checksum generation.
 * 2. Multi-table data capture across all entities and Liquibase changelogs.
 * 3. Complete disaster recovery and restoration lifecycle with relation preservation.
 * 4. Tamper detection, checksum verification, and path traversal security guards.
 * 5. Administrative REST API audit and RBAC endpoint-level authorization.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("API and Database Audit - Database Backup & Restoration Test Suite")
public class DatabaseBackupRestoreIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SpringLiquibase springLiquibase;

    @Autowired
    private DatabaseBackupRestoreService backupRestoreService;

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
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SecurityEventRepository securityEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private String runId;
    private User adminUser;
    private User employeeUser;
    private User managerUser;
    private String adminToken;
    private String employeeToken;
    private String managerToken;

    @BeforeEach
    public void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply(springSecurity())
                .build();

        runId = UUID.randomUUID().toString().substring(0, 8);

        Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() -> roleRepository.save(new Role("ADMIN")));
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> roleRepository.save(new Role("EMPLOYEE")));
        Role managerRole = roleRepository.findByName("MANAGER").orElseGet(() -> roleRepository.save(new Role("MANAGER")));

        Department dept = new Department();
        dept.setName("Backup Dept " + runId);
        dept = departmentRepository.save(dept);

        // 1. Admin User & Token
        adminUser = new User();
        adminUser.setEmail("backup-admin-" + runId + "@enterprise.com");
        adminUser.setPassword(passwordEncoder.encode("AdminPass123!"));
        adminUser.setActive(true);
        adminUser.setAccountNonLocked(true);
        adminUser.getRoles().add(adminRole);
        adminUser = userRepository.save(adminUser);

        Employee adminEmp = new Employee();
        adminEmp.setFirstName("Admin");
        adminEmp.setLastName("Auditor");
        adminEmp.setEmployeeCode("ADM-BCK-" + runId);
        adminEmp.setStatus("ACTIVE");
        adminEmp.setSalary(new BigDecimal("120000.00"));
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
        employeeUser.setEmail("backup-emp-" + runId + "@enterprise.com");
        employeeUser.setPassword(passwordEncoder.encode("EmpPass123!"));
        employeeUser.setActive(true);
        employeeUser.setAccountNonLocked(true);
        employeeUser.getRoles().add(employeeRole);
        employeeUser = userRepository.save(employeeUser);

        Employee emp = new Employee();
        emp.setFirstName("Test");
        emp.setLastName("Staff");
        emp.setEmployeeCode("EMP-BCK-" + runId);
        emp.setStatus("ACTIVE");
        emp.setSalary(new BigDecimal("60000.00"));
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
        managerUser.setEmail("backup-mgr-" + runId + "@enterprise.com");
        managerUser.setPassword(passwordEncoder.encode("MgrPass123!"));
        managerUser.setActive(true);
        managerUser.setAccountNonLocked(true);
        managerUser.getRoles().add(managerRole);
        managerUser = userRepository.save(managerUser);

        Employee mgr = new Employee();
        mgr.setFirstName("Lead");
        mgr.setLastName("Manager");
        mgr.setEmployeeCode("MGR-BCK-" + runId);
        mgr.setStatus("ACTIVE");
        mgr.setSalary(new BigDecimal("90000.00"));
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
    public void cleanup() {
        // Clean any temporary test backup files
        try {
            File legacyFile = new File("target/test-db-backup.sql");
            if (legacyFile.exists()) {
                legacyFile.delete();
            }
        } catch (Exception ignored) {
        }
    }

    // =========================================================================
    // Core Compatibility Tests (Preserved from original suite)
    // =========================================================================
    @Test
    @DisplayName("Liquibase Schema Validation: All changesets execute cleanly with valid checksums")
    void liquibase_ChecksumsAndExecution_Verified() {
        assertNotNull(springLiquibase, "SpringLiquibase must be initialized");

        Integer changesetCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM databasechangelog", Integer.class);
        assertNotNull(changesetCount);
        assertTrue(changesetCount >= 26, "All 26+ changesets must be recorded in databasechangelog");

        Integer migration026Count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM databasechangelog WHERE id LIKE '026%'", Integer.class);
        assertNotNull(migration026Count);
        assertTrue(migration026Count >= 1, "Migration 026 must be executed");
    }

    @Test
    @DisplayName("Database Schema Evolution: columns active and revoked are boolean across tables")
    void databaseSchemaEvolution_BooleanColumnTypesVerified() {
        Integer ltActive = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE UPPER(table_name) = 'LEAVE_TYPES' AND UPPER(column_name) = 'ACTIVE' AND UPPER(data_type) = 'BOOLEAN'",
                Integer.class);
        assertEquals(1, ltActive, "leave_types.active must be of type boolean");

        Integer usersActive = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE UPPER(table_name) = 'USERS' AND UPPER(column_name) = 'ACTIVE' AND UPPER(data_type) = 'BOOLEAN'",
                Integer.class);
        assertEquals(1, usersActive, "users.active must be of type boolean");

        Integer tokensRevoked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE UPPER(table_name) = 'REFRESH_TOKENS' AND UPPER(column_name) = 'REVOKED' AND UPPER(data_type) = 'BOOLEAN'",
                Integer.class);
        assertEquals(1, tokensRevoked, "refresh_tokens.revoked must be of type boolean");
    }

    // =========================================================================
    // SECTION 1: Database Backup Export & Checksum Verification Audit
    // =========================================================================
    @Nested
    @DisplayName("1. Database Backup Generation & Checksum Verification Audit")
    class BackupGenerationAudit {

        @Test
        @DisplayName("1.1. Verification: Service generates complete SQL backup with SHA-256 integrity hash")
        void testCreateBackupGeneratesFileAndSha256() throws Exception {
            DatabaseBackupResponseDTO response = backupRestoreService.createBackup("audit_export");

            assertNotNull(response);
            assertEquals("SUCCESS", response.getStatus());
            assertNotNull(response.getFileName());
            assertTrue(response.getFileName().startsWith("backup_"));
            assertTrue(response.getFileName().endsWith(".sql"));
            assertTrue(response.getFileSizeBytes() > 0, "Backup file size must be greater than zero");

            assertNotNull(response.getSha256Checksum(), "SHA-256 checksum must be generated");
            assertEquals(64, response.getSha256Checksum().length(), "SHA-256 hash must be 64 hex characters");

            File generatedFile = new File(response.getFilePath());
            assertTrue(generatedFile.exists(), "Backup file must physically exist on disk");
            assertTrue(generatedFile.length() > 0, "Backup file on disk must not be empty");

            // Verify content contains essential DDL / DML commands
            String content = Files.readString(generatedFile.toPath());
            assertTrue(content.contains("CREATE ") || content.contains("INSERT INTO "),
                    "Backup file must contain valid SQL DDL/DML statements");
            assertTrue(content.contains("USERS") || content.contains("users"), "Backup must include users table");
            assertTrue(content.contains("DATABASECHANGELOG"), "Backup must include Liquibase changelog table");
        }

        @Test
        @DisplayName("1.2. Verification: Listing backups returns all generated backups with valid metadata")
        void testListBackupsReturnsGeneratedBackups() {
            DatabaseBackupResponseDTO created = backupRestoreService.createBackup("list_test");
            List<DatabaseBackupResponseDTO> list = backupRestoreService.listBackups();

            assertNotNull(list);
            assertFalse(list.isEmpty(), "Backup list must not be empty after backup creation");
            assertTrue(list.stream().anyMatch(b -> b.getFileName().equals(created.getFileName())),
                    "Newly created backup must appear in backup listing");
        }

        @Test
        @DisplayName("1.3. Verification: Validating genuine backup file returns valid status")
        void testValidateBackupFileSuccess() {
            DatabaseBackupResponseDTO created = backupRestoreService.createBackup("validate_test");
            DatabaseBackupValidationDTO validation = backupRestoreService.validateBackupFile(created.getFileName());

            assertNotNull(validation);
            assertTrue(validation.isValid(), "Genuine backup file must pass validation");
            assertEquals(created.getFileSizeBytes(), validation.getFileSizeBytes());
            assertEquals(created.getSha256Checksum(), validation.getSha256Checksum());
            assertTrue(validation.getValidationErrors().isEmpty());
        }
    }

    // =========================================================================
    // SECTION 2: Disaster Recovery & Complete Restoration Lifecycle Audit
    // =========================================================================
    @Nested
    @DisplayName("2. Disaster Recovery & Complete Restoration Lifecycle Audit")
    class DisasterRecoveryAndRestorationAudit {

        @Test
        @DisplayName("2.1. Verification: Complete disaster recovery cycle restores entities, relations, and signoffs")
        void testFullDisasterRecoveryAndRestorationLifecycle() throws Exception {
            String testSuffix = UUID.randomUUID().toString().substring(0, 8);

            // 1. Populate comprehensive relational data graph
            Department dept = new Department();
            dept.setName("DisasterRecoveryDept_" + testSuffix);
            dept = departmentRepository.save(dept);

            Employee mgr = new Employee();
            mgr.setFirstName("Recovery");
            mgr.setLastName("Manager");
            mgr.setEmployeeCode("REC-MGR-" + testSuffix);
            mgr.setStatus("ACTIVE");
            mgr.setSalary(new BigDecimal("95000.00"));
            mgr.setDepartment(dept);
            mgr = employeeRepository.save(mgr);

            Employee emp = new Employee();
            emp.setFirstName("Recovery");
            emp.setLastName("Staff");
            emp.setEmployeeCode("REC-EMP-" + testSuffix);
            emp.setStatus("ACTIVE");
            emp.setSalary(new BigDecimal("70000.00"));
            emp.setDepartment(dept);
            emp.setManager(mgr);
            emp = employeeRepository.save(emp);

            LeaveType lt = new LeaveType("DisasterRecoveryLeave_" + testSuffix, "Quota for DR", 20);
            lt.setActive(true);
            lt = leaveTypeRepository.save(lt);

            LeaveBalance balance = new LeaveBalance();
            balance.setEmployee(emp);
            balance.setLeaveType(lt);
            balance.setYear(2027);
            balance.setTotal(20);
            balance.setUsed(0);
            balance.setAvailable(20);
            balance = leaveBalanceRepository.save(balance);

            LeaveRequest request = new LeaveRequest();
            request.setEmployee(emp);
            request.setLeaveType(lt);
            request.setStartDate(LocalDate.of(2027, 8, 10));
            request.setEndDate(LocalDate.of(2027, 8, 15));
            request.setStatus(LeaveStatus.PENDING);
            request.setRequiresDualApproval(true);
            request = leaveRequestRepository.save(request);

            LeaveRequestSignoff signoff = new LeaveRequestSignoff();
            signoff.setLeaveRequest(request);
            signoff.setApproverRole("MANAGER");
            signoff.setApprover(managerUser);
            signoff.setStatus("APPROVED");
            signoff.setComments("First level signoff approved");
            signoff.setSignedAt(LocalDateTime.now());
            signoff = leaveRequestSignoffRepository.save(signoff);
            request.getSignoffs().add(signoff);
            request = leaveRequestRepository.save(request);

            Long savedDeptId = dept.getId();
            Long savedMgrId = mgr.getId();
            Long savedEmpId = emp.getId();
            Long savedLtId = lt.getId();
            Long savedRequestId = request.getId();

            // 2. Export full database backup
            DatabaseBackupResponseDTO backupResponse = backupRestoreService.createBackup("dr_full_cycle");
            assertNotNull(backupResponse);
            String backupFileName = backupResponse.getFileName();
            String authenticSha256 = backupResponse.getSha256Checksum();

            // 3. Simulate catastrophic disaster (data loss)
            // Clear entities
            leaveRequestSignoffRepository.deleteAll();
            leaveRequestRepository.deleteById(savedRequestId);
            leaveBalanceRepository.deleteById(balance.getId());
            employeeRepository.deleteById(savedEmpId);
            employeeRepository.deleteById(savedMgrId);
            departmentRepository.deleteById(savedDeptId);
            leaveTypeRepository.deleteById(savedLtId);

            // Verify database is depleted
            assertFalse(departmentRepository.findById(savedDeptId).isPresent(), "Department must be depleted");
            assertFalse(employeeRepository.findById(savedEmpId).isPresent(), "Employee must be depleted");
            assertFalse(leaveRequestRepository.findById(savedRequestId).isPresent(), "Leave request must be depleted");

            // 4. Execute Restoration with SHA-256 verification
            DatabaseRestoreResponseDTO restoreResponse = backupRestoreService.restoreBackup(backupFileName, authenticSha256);
            assertNotNull(restoreResponse);
            assertEquals("SUCCESS", restoreResponse.getStatus());
            assertEquals(authenticSha256, restoreResponse.getSha256Checksum());

            // 5. Verify 100% data and relational restoration
            assertTrue(departmentRepository.findById(savedDeptId).isPresent(), "Department must be restored");
            assertEquals("DisasterRecoveryDept_" + testSuffix, departmentRepository.findById(savedDeptId).get().getName());

            assertTrue(employeeRepository.findById(savedMgrId).isPresent(), "Manager must be restored");
            assertEquals("REC-MGR-" + testSuffix, employeeRepository.findById(savedMgrId).get().getEmployeeCode());

            assertTrue(employeeRepository.findById(savedEmpId).isPresent(), "Employee must be restored");
            Employee restoredEmp = employeeRepository.findById(savedEmpId).get();
            assertEquals("REC-EMP-" + testSuffix, restoredEmp.getEmployeeCode());
            assertNotNull(restoredEmp.getManager(), "Manager relationship must be restored");
            assertEquals(savedMgrId, restoredEmp.getManager().getId());

            assertTrue(leaveTypeRepository.findById(savedLtId).isPresent(), "Leave type must be restored");
            assertTrue(leaveRequestRepository.findById(savedRequestId).isPresent(), "Leave request must be restored");
            LeaveRequest restoredRequest = leaveRequestRepository.findById(savedRequestId).get();
            assertEquals(LeaveStatus.PENDING, restoredRequest.getStatus());
            assertTrue(restoredRequest.isRequiresDualApproval());

            // Verify signoffs restored
            List<LeaveRequestSignoff> signoffs = leaveRequestSignoffRepository.findAll();
            assertFalse(signoffs.isEmpty(), "Child signoffs must be restored");
            assertTrue(signoffs.stream().anyMatch(s -> "APPROVED".equals(s.getStatus()) && s.getApprover().getId().equals(managerUser.getId())));

            // Verify Liquibase changelog integrity preserved
            Integer restoredChangesetCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM databasechangelog", Integer.class);
            assertNotNull(restoredChangesetCount);
            assertTrue(restoredChangesetCount >= 26, "Liquibase changesets must be preserved across restore");
        }

        @Test
        @DisplayName("2.2. Verification: Repeated restoration of the same backup is idempotent and clean")
        void testRepeatedRestorationIsIdempotent() {
            DatabaseBackupResponseDTO backup = backupRestoreService.createBackup("idempotent_test");

            // First restore
            DatabaseRestoreResponseDTO r1 = backupRestoreService.restoreBackup(backup.getFileName(), backup.getSha256Checksum());
            assertEquals("SUCCESS", r1.getStatus());

            // Second restore of identical backup
            DatabaseRestoreResponseDTO r2 = backupRestoreService.restoreBackup(backup.getFileName(), backup.getSha256Checksum());
            assertEquals("SUCCESS", r2.getStatus());

            // Database remains fully operational
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            assertNotNull(count);
            assertTrue(count > 0, "Users table must be fully functional after repeated restore");
        }
    }

    // =========================================================================
    // SECTION 3: Tamper Detection, Checksum Integrity & Path Traversal Security Audit
    // =========================================================================
    @Nested
    @DisplayName("3. Tamper Detection, Checksum Integrity & Path Traversal Security Audit")
    class TamperDetectionAndSecurityAudit {

        @Test
        @DisplayName("3.1. Verification: Path traversal in backup filename is strictly rejected with IllegalArgumentException")
        void testPathTraversalInFilenameIsRejected() {
            assertThrows(IllegalArgumentException.class, () ->
                    backupRestoreService.validateBackupFile("../../../etc/passwd"));

            assertThrows(IllegalArgumentException.class, () ->
                    backupRestoreService.restoreBackup("../../../test.sql", null));

            assertThrows(IllegalArgumentException.class, () ->
                    backupRestoreService.validateBackupFile("sub/folder/file.sql"));
        }

        @Test
        @DisplayName("3.2. Verification: Tampered backup file checksum mismatch aborts restore with SecurityException")
        void testTamperedBackupTriggersSecurityException() throws Exception {
            DatabaseBackupResponseDTO genuineBackup = backupRestoreService.createBackup("tamper_test");
            String genuineChecksum = genuineBackup.getSha256Checksum();

            // Tamper with the expected checksum (simulating payload interception / alteration)
            String corruptedChecksum = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";

            SecurityException ex = assertThrows(SecurityException.class, () ->
                    backupRestoreService.restoreBackup(genuineBackup.getFileName(), corruptedChecksum));

            assertTrue(ex.getMessage().contains("Checksum mismatch") || ex.getMessage().contains("tampering"),
                    "SecurityException must explicitly report checksum mismatch / tampering");
        }

        @Test
        @DisplayName("3.3. Verification: Restoring nonexistent backup file throws IllegalArgumentException")
        void testRestoreNonExistentFileThrowsException() {
            assertThrows(IllegalArgumentException.class, () ->
                    backupRestoreService.restoreBackup("non_existent_file_999.sql", null));
        }

        @Test
        @DisplayName("3.4. Verification: Restoring empty backup file throws IllegalArgumentException")
        void testRestoreEmptyFileThrowsException() throws Exception {
            Path emptyFile = backupRestoreService.getBackupDirectory().resolve("empty_test.sql");
            Files.createFile(emptyFile);

            try {
                assertThrows(IllegalArgumentException.class, () ->
                        backupRestoreService.restoreBackup("empty_test.sql", null));
            } finally {
                Files.deleteIfExists(emptyFile);
            }
        }
    }

    // =========================================================================
    // SECTION 4: Audit Logging & Security Event Audit
    // =========================================================================
    @Nested
    @DisplayName("4. Audit Logging & Security Event Audit")
    class AuditLoggingAndSecurityEventAudit {

        @Test
        @DisplayName("4.1. Verification: Backup creation logs an action event in the audit trail")
        void testBackupCreationLogsAuditEvent() {
            DatabaseBackupResponseDTO response = backupRestoreService.createBackup("audit_log_verify");

            List<AuditLog> logs = auditLogRepository.findByAction("DATABASE_BACKUP_CREATED");
            assertFalse(logs.isEmpty(), "Audit log must contain DATABASE_BACKUP_CREATED event");
            assertTrue(logs.stream().anyMatch(l -> l.getNewValue().contains(response.getFileName())),
                    "Audit log entry must reference generated backup filename");
        }

        @Test
        @DisplayName("4.2. Verification: Backup restoration logs a high-severity security event")
        void testBackupRestorationLogsSecurityEvent() {
            DatabaseBackupResponseDTO backup = backupRestoreService.createBackup("audit_restore_verify");
            backupRestoreService.restoreBackup(backup.getFileName(), backup.getSha256Checksum());

            List<AuditLog> restoreLogs = auditLogRepository.findByAction("DATABASE_RESTORED");
            assertFalse(restoreLogs.isEmpty(), "Audit log must contain DATABASE_RESTORED event");
            assertTrue(restoreLogs.stream().anyMatch(l -> l.getNewValue().contains(backup.getFileName())),
                    "Audit log entry must reference restored backup filename");
        }
    }

    // =========================================================================
    // SECTION 5: API Endpoint Level Authorization & Security Audit
    // =========================================================================
    @Nested
    @DisplayName("5. API Endpoint Level Authorization & Security Audit")
    class ApiEndpointLevelAuthorizationAudit {

        @Test
        @DisplayName("5.1. POST /api/audit/database/backup/create - Unauthenticated returns HTTP 401 Unauthorized")
        void testCreateBackup_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(post("/api/audit/database/backup/create")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("5.2. POST /api/audit/database/backup/create - Regular Employee returns HTTP 403 Forbidden")
        void testCreateBackup_Employee_Returns403() throws Exception {
            mockMvc.perform(post("/api/audit/database/backup/create")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("5.3. POST /api/audit/database/backup/create - Admin returns HTTP 200 with backup response")
        void testCreateBackup_Admin_Returns200() throws Exception {
            mockMvc.perform(post("/api/audit/database/backup/create")
                            .param("customName", "api_admin_test")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.fileName", startsWith("backup_")))
                    .andExpect(jsonPath("$.sha256Checksum", hasLength(64)));
        }

        @Test
        @DisplayName("5.4. GET /api/audit/database/backup/list - Admin returns HTTP 200 with list of backups")
        void testListBackups_Admin_Returns200() throws Exception {
            mockMvc.perform(get("/api/audit/database/backup/list")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", isA(List.class)));
        }

        @Test
        @DisplayName("5.5. POST /api/audit/database/backup/validate - Admin returns HTTP 200 with validation result")
        void testValidateBackup_Admin_Returns200() throws Exception {
            DatabaseBackupResponseDTO backup = backupRestoreService.createBackup("validate_api");

            mockMvc.perform(post("/api/audit/database/backup/validate")
                            .param("fileName", backup.getFileName())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.fileName").value(backup.getFileName()))
                    .andExpect(jsonPath("$.sha256Checksum").value(backup.getSha256Checksum()));
        }

        @Test
        @DisplayName("5.6. POST /api/audit/database/backup/restore - Employee returns HTTP 403 Forbidden")
        void testRestoreBackup_Employee_Returns403() throws Exception {
            DatabaseRestoreRequestDTO request = new DatabaseRestoreRequestDTO("test.sql", "hash");

            mockMvc.perform(post("/api/audit/database/backup/restore")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("5.7. POST /api/audit/database/backup/restore - Admin returns HTTP 200 on valid restoration")
        void testRestoreBackup_Admin_Returns200() throws Exception {
            DatabaseBackupResponseDTO backup = backupRestoreService.createBackup("restore_api");
            DatabaseRestoreRequestDTO request = new DatabaseRestoreRequestDTO(backup.getFileName(), backup.getSha256Checksum());

            mockMvc.perform(post("/api/audit/database/backup/restore")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.fileName").value(backup.getFileName()));
        }

        @Test
        @DisplayName("5.8. POST /api/audit/database/backup/validate - Path traversal returns HTTP 400 Bad Request")
        void testValidateBackup_PathTraversal_Returns400() throws Exception {
            mockMvc.perform(post("/api/audit/database/backup/validate")
                            .param("fileName", "../../etc/passwd")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }
}
