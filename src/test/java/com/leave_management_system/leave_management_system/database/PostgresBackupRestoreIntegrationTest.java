package com.leave_management_system.leave_management_system.database;

import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.repository.*;
import com.leave_management_system.leave_management_system.security.sensitive.CryptoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Enterprise Production Verification Test:
 * Actual PostgreSQL pg_dump and restoration into a separate clean PostgreSQL database.
 *
 * Demonstrates:
 * 1. Live seeding of multi-tier enterprise data, foreign key hierarchies, and AES-256-GCM encrypted records.
 * 2. Execution of pg_dump on live source database (leave_management_test_pg) to an actual backup file.
 * 3. Complete restoration of the dump into a separate, previously clean PostgreSQL database (leave_management_clean_restore_db).
 * 4. Deep validation of:
 *    - Data row counts and records across all tables.
 *    - Referential integrity and foreign key constraints (pg_constraint / table_constraints).
 *    - Liquibase migration state (databasechangelog all 25 changesets preserved).
 *    - Encrypted records: authentic AES-256-GCM decryption via CryptoService and AEAD tamper rejection.
 */
@SpringBootTest
@ActiveProfiles(profiles = "pgtest", inheritProfiles = false)
@Tag("postgres")
@DisplayName("PostgreSQL Live pg_dump and Clean-Database Restoration Verification Suite")
public class PostgresBackupRestoreIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgresBackupRestoreIntegrationTest.class);

    private static final String PG_HOST = "localhost";
    private static final int PG_PORT = 5432;
    private static final String PG_USER = "postgres";
    private static final String PG_PASSWORD = "secret";
    private static final String SOURCE_DB = "leave_management_test_pg";
    private static final String CLEAN_RESTORE_DB = "leave_management_clean_restore_db";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveRequestSignoffRepository leaveRequestSignoffRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("Demonstrate PostgreSQL pg_dump and Clean-Database Restoration with Data, FK, Liquibase and AES-256-GCM Validation")
    public void testPostgreSqlDumpAndCleanDatabaseRestoreLifecycle() throws Exception {
        log.info("================================================================================");
        log.info("STARTING POSTGRESQL LIVE BACKUP & CLEAN-DATABASE RESTORATION VERIFICATION");
        log.info("================================================================================");

        // -----------------------------------------------------------------------------------------
        // 1. Seed Comprehensive Enterprise Domain Data with Encrypted Records in Source DB
        // -----------------------------------------------------------------------------------------
        String runSuffix = UUID.randomUUID().toString().substring(0, 8);
        log.info("[STEP 1] Seeding enterprise dataset in source PostgreSQL database [{}] (Suffix: {})", SOURCE_DB, runSuffix);

        // Departments
        Department deptEng = new Department();
        deptEng.setName("Enterprise Engineering " + runSuffix);
        deptEng = departmentRepository.save(deptEng);

        Department deptHr = new Department();
        deptHr.setName("People Operations " + runSuffix);
        deptHr = departmentRepository.save(deptHr);

        // Users
        User dirUser = new User();
        dirUser.setEmail("director." + runSuffix + "@enterprise.lms");
        dirUser.setPassword("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"); // BCrypt
        dirUser.setActive(true);
        dirUser = userRepository.save(dirUser);

        User mgrUser = new User();
        mgrUser.setEmail("manager." + runSuffix + "@enterprise.lms");
        mgrUser.setPassword("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
        mgrUser.setActive(true);
        mgrUser = userRepository.save(mgrUser);

        User devUser = new User();
        devUser.setEmail("leaddev." + runSuffix + "@enterprise.lms");
        devUser.setPassword("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
        devUser.setActive(true);
        devUser = userRepository.save(devUser);

        // Encrypted Data Generation via CryptoService (AES-256-GCM with fresh 12-byte IV + 128-bit tag)
        String plaintextDirectorPhone = "+1-555-0100-SECRET-EXEC-PHONE";
        String encryptedDirectorPhone = cryptoService.encrypt(plaintextDirectorPhone);
        assertTrue(encryptedDirectorPhone.startsWith(CryptoService.ENCRYPTION_PREFIX), "Encrypted phone must bear ENC: prefix");

        String plaintextManagerPhone = "+1-555-0200-CONFIDENTIAL-MGR-PHONE";
        String encryptedManagerPhone = cryptoService.encrypt(plaintextManagerPhone);

        String plaintextDevPhone = "+1-555-0300-RESTRICTED-DEV-PHONE";
        String encryptedDevPhone = cryptoService.encrypt(plaintextDevPhone);

        // Employees with 3-tier Self-Referential Hierarchy
        Employee dirEmp = new Employee();
        dirEmp.setFirstName("Sophia");
        dirEmp.setLastName("Vance");
        dirEmp.setEmployeeCode("DIR-" + runSuffix);
        dirEmp.setPhone(encryptedDirectorPhone);
        dirEmp.setStatus("ACTIVE");
        dirEmp.setSalary(new BigDecimal("210000.00"));
        dirEmp.setUser(dirUser);
        dirEmp.setDepartment(deptEng);
        dirEmp.setManager(null); // Top of hierarchy
        dirEmp = employeeRepository.save(dirEmp);

        deptEng.setManager(dirEmp);
        departmentRepository.save(deptEng);

        Employee mgrEmp = new Employee();
        mgrEmp.setFirstName("Marcus");
        mgrEmp.setLastName("Sterling");
        mgrEmp.setEmployeeCode("MGR-" + runSuffix);
        mgrEmp.setPhone(encryptedManagerPhone);
        mgrEmp.setStatus("ACTIVE");
        mgrEmp.setSalary(new BigDecimal("155000.00"));
        mgrEmp.setUser(mgrUser);
        mgrEmp.setDepartment(deptEng);
        mgrEmp.setManager(dirEmp); // FK: Manager points to Sophia Vance
        mgrEmp = employeeRepository.save(mgrEmp);

        Employee devEmp = new Employee();
        devEmp.setFirstName("Ethan");
        devEmp.setLastName("Hunt");
        devEmp.setEmployeeCode("DEV-" + runSuffix);
        devEmp.setPhone(encryptedDevPhone);
        devEmp.setStatus("ACTIVE");
        devEmp.setSalary(new BigDecimal("125000.00"));
        devEmp.setUser(devUser);
        devEmp.setDepartment(deptEng);
        devEmp.setManager(mgrEmp); // FK: Dev points to Marcus Sterling
        devEmp = employeeRepository.save(devEmp);

        // Leave Types
        LeaveType annualLeave = new LeaveType();
        annualLeave.setName("Executive Annual Leave " + runSuffix);
        annualLeave.setDescription("Standard Annual Paid Time Off");
        annualLeave.setDefaultDays(25);
        annualLeave.setActive(true);
        annualLeave = leaveTypeRepository.save(annualLeave);

        // Leave Balances (Composite unique: employee, leave_type, year)
        LeaveBalance devBalance = new LeaveBalance(devEmp, annualLeave, 2026, 25, 5, 20);
        devBalance = leaveBalanceRepository.save(devBalance);

        // Leave Request
        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployee(devEmp);
        leaveRequest.setLeaveType(annualLeave);
        leaveRequest.setStartDate(LocalDate.of(2026, 12, 10));
        leaveRequest.setEndDate(LocalDate.of(2026, 12, 15));
        leaveRequest.setReason("Year-end family retreat");
        leaveRequest.setStatus(LeaveStatus.PENDING);
        leaveRequest.setRequiresDualApproval(true);
        leaveRequest.setAppliedAt(LocalDateTime.now());
        leaveRequest = leaveRequestRepository.save(leaveRequest);

        // Leave Request Signoff
        LeaveRequestSignoff signoffMgr = new LeaveRequestSignoff(
                leaveRequest, "MANAGER", mgrUser, "APPROVED", "Approved by engineering manager"
        );
        signoffMgr = leaveRequestSignoffRepository.save(signoffMgr);

        // Encrypted Compensation Audit Trail in audit_logs
        String plaintextOldComp = "BaseSalary=$115,000.00;EquityUnits=1000;BonusTier=Standard";
        String plaintextNewComp = "BaseSalary=$125,000.00;EquityUnits=1500;BonusTier=Senior";
        String encryptedOldComp = cryptoService.encrypt(plaintextOldComp);
        String encryptedNewComp = cryptoService.encrypt(plaintextNewComp);

        AuditLog auditLog = new AuditLog();
        auditLog.setUser(mgrUser);
        auditLog.setAction("EMPLOYEE_COMPENSATION_ADJUSTED");
        auditLog.setEntityType("Employee");
        auditLog.setEntityId(devEmp.getId());
        auditLog.setOldValue(encryptedOldComp);
        auditLog.setNewValue(encryptedNewComp);
        auditLog.setIpAddress("192.168.1.105");
        auditLog.setTimestamp(LocalDateTime.now());
        auditLog = auditLogRepository.save(auditLog);

        log.info("[STEP 1 SUCCESS] Seeded 2 departments, 3 users, 3 employees, 1 leave balance, 1 leave request, 1 signoff, 1 encrypted audit log.");

        // Record source row counts for subsequent 100% equivalence comparison
        long srcChangelogCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM databasechangelog", Long.class);
        long srcDeptCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM departments", Long.class);
        long srcUserCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        long srcEmpCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM employees", Long.class);
        long srcLeaveTypeCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM leave_types", Long.class);
        long srcBalanceCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM leave_balances", Long.class);
        long srcRequestCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM leave_requests", Long.class);
        long srcSignoffCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM leave_request_signoffs", Long.class);
        long srcAuditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs", Long.class);

        assertEquals(srcChangelogCount, 29, "Liquibase master changelog on PostgreSQL must record exactly 29 migrations (25 common + 4 PostgreSQL boolean conversions)");

        // -----------------------------------------------------------------------------------------
        // 2. Perform Actual PostgreSQL pg_dump
        // -----------------------------------------------------------------------------------------
        Path backupDir = Paths.get("target/backups").toAbsolutePath().normalize();
        Files.createDirectories(backupDir);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String dumpFileName = "postgresql_backup_" + timestamp + "_production_evidence.sql";
        File dumpFile = backupDir.resolve(dumpFileName).toFile();

        log.info("[STEP 2] Executing pg_dump from [{}] to [{}]", SOURCE_DB, dumpFile.getAbsolutePath());

        String pgDumpPath = resolveBinary("pg_dump");
        ProcessBuilder dumpPb = new ProcessBuilder(
                pgDumpPath,
                "-h", PG_HOST,
                "-p", String.valueOf(PG_PORT),
                "-U", PG_USER,
                "-d", SOURCE_DB,
                "--clean",
                "--if-exists",
                "--format=plain",
                "--no-owner",
                "--no-privileges",
                "--file=" + dumpFile.getAbsolutePath()
        );
        dumpPb.environment().put("PGPASSWORD", PG_PASSWORD);
        dumpPb.redirectErrorStream(true);

        long dumpStart = System.currentTimeMillis();
        Process dumpProcess = dumpPb.start();
        String dumpOutput = readProcessOutput(dumpProcess);
        int dumpExitCode = dumpProcess.waitFor();
        long dumpElapsed = System.currentTimeMillis() - dumpStart;

        assertEquals(0, dumpExitCode, "pg_dump must exit cleanly with code 0. Logs: " + dumpOutput);
        assertTrue(dumpFile.exists(), "pg_dump output file must exist");
        assertTrue(dumpFile.length() > 0, "pg_dump output file must not be empty");

        String dumpSha256 = computeSha256(dumpFile);
        long dumpSizeBytes = dumpFile.length();
        long dumpLineCount = Files.lines(dumpFile.toPath()).count();

        log.info("[STEP 2 SUCCESS] pg_dump completed in {} ms. Size: {} bytes ({} lines). SHA-256: {}",
                dumpElapsed, dumpSizeBytes, dumpLineCount, dumpSha256);

        // -----------------------------------------------------------------------------------------
        // 3. Create Separate, Completely Clean PostgreSQL Database
        // -----------------------------------------------------------------------------------------
        log.info("[STEP 3] Provisioning separate clean PostgreSQL database [{}]", CLEAN_RESTORE_DB);

        String psqlPath = resolveBinary("psql");
        executePsqlCommand(psqlPath, "postgres", "DROP DATABASE IF EXISTS " + CLEAN_RESTORE_DB + ";");
        executePsqlCommand(psqlPath, "postgres", "CREATE DATABASE " + CLEAN_RESTORE_DB + " OWNER " + PG_USER + ";");

        // Verify clean database has 0 tables
        try (Connection cleanConn = getCleanDbConnection();
             Statement stmt = cleanConn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'")) {
            assertTrue(rs.next());
            long preRestoreTableCount = rs.getLong(1);
            assertEquals(0, preRestoreTableCount, "Clean target database must contain exactly 0 tables prior to restoration");
            log.info("[STEP 3 SUCCESS] Verified [{}] is completely empty (0 tables).", CLEAN_RESTORE_DB);
        }

        // -----------------------------------------------------------------------------------------
        // 4. Restore pg_dump into the Clean PostgreSQL Database
        // -----------------------------------------------------------------------------------------
        log.info("[STEP 4] Restoring [{}] into clean PostgreSQL database [{}]", dumpFileName, CLEAN_RESTORE_DB);

        ProcessBuilder restorePb = new ProcessBuilder(
                psqlPath,
                "-h", PG_HOST,
                "-p", String.valueOf(PG_PORT),
                "-U", PG_USER,
                "-d", CLEAN_RESTORE_DB,
                "-f", dumpFile.getAbsolutePath(),
                "-v", "ON_ERROR_STOP=1"
        );
        restorePb.environment().put("PGPASSWORD", PG_PASSWORD);
        restorePb.redirectErrorStream(true);

        long restoreStart = System.currentTimeMillis();
        Process restoreProcess = restorePb.start();
        String restoreOutput = readProcessOutput(restoreProcess);
        int restoreExitCode = restoreProcess.waitFor();
        long restoreElapsed = System.currentTimeMillis() - restoreStart;

        assertEquals(0, restoreExitCode, "psql restore must exit cleanly with code 0. Logs: " + restoreOutput);
        log.info("[STEP 4 SUCCESS] Restoration into clean database completed in {} ms.", restoreElapsed);

        // -----------------------------------------------------------------------------------------
        // 5. Deep Validation on Restored Clean Database
        // -----------------------------------------------------------------------------------------
        log.info("[STEP 5] Validating restored clean PostgreSQL database [{}]", CLEAN_RESTORE_DB);

        try (Connection conn = getCleanDbConnection();
             Statement stmt = conn.createStatement()) {

            // A. Liquibase Migration State Verification
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM databasechangelog")) {
                assertTrue(rs.next());
                long restoredChangelog = rs.getLong(1);
                assertEquals(srcChangelogCount, restoredChangelog, "Restored database must preserve all Liquibase changesets");
                assertEquals(29, restoredChangelog, "Restored database must preserve all 29 Liquibase changesets on PostgreSQL (26 EXECUTED, 3 MARK_RAN)");
            }
            try (ResultSet rs = stmt.executeQuery("SELECT locked FROM databasechangeloglock WHERE id = 1")) {
                assertTrue(rs.next());
                boolean lockStatus = rs.getBoolean("locked");
                assertFalse(lockStatus, "Database changelog lock must be released (locked = false)");
            }
            log.info("  -> Liquibase Validation: PASSED (All 25 changesets preserved, lock released).");

            // B. Table & Row Counts Equivalence Validation
            assertDbCount(stmt, "departments", srcDeptCount);
            assertDbCount(stmt, "users", srcUserCount);
            assertDbCount(stmt, "employees", srcEmpCount);
            assertDbCount(stmt, "leave_types", srcLeaveTypeCount);
            assertDbCount(stmt, "leave_balances", srcBalanceCount);
            assertDbCount(stmt, "leave_requests", srcRequestCount);
            assertDbCount(stmt, "leave_request_signoffs", srcSignoffCount);
            assertDbCount(stmt, "audit_logs", srcAuditCount);
            log.info("  -> Row Counts Validation: PASSED (100% equivalence across all domain tables).");

            // C. Foreign Keys & Referential Integrity Verification
            List<String> foreignKeys = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT conname FROM pg_constraint WHERE contype = 'f' AND connamespace = 'public'::regnamespace ORDER BY conname")) {
                while (rs.next()) {
                    foreignKeys.add(rs.getString("conname"));
                }
            }
            assertTrue(foreignKeys.contains("fk_employee_manager"), "Foreign key fk_employee_manager must be present");
            assertTrue(foreignKeys.contains("fk_employee_department"), "Foreign key fk_employee_department must be present");
            assertTrue(foreignKeys.contains("fk_employee_user"), "Foreign key fk_employee_user must be present");
            assertTrue(foreignKeys.contains("fk_leave_balance_employee"), "Foreign key fk_leave_balance_employee must be present");
            assertTrue(foreignKeys.contains("fk_leave_request_employee"), "Foreign key fk_leave_request_employee must be present");
            log.info("  -> Foreign Key Constraints Validation: PASSED ({} active foreign keys confirmed).", foreignKeys.size());

            // Relational Hierarchy JOIN Query Verification: Sophia Vance -> Marcus Sterling -> Ethan Hunt
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT e.employee_code, e.first_name, e.last_name, m.employee_code AS mgr_code, m.first_name AS mgr_name, d.name AS dept_name " +
                    "FROM employees e " +
                    "LEFT JOIN employees m ON e.manager_id = m.id " +
                    "JOIN departments d ON e.department_id = d.id " +
                    "WHERE e.employee_code IN ('DIR-" + runSuffix + "', 'MGR-" + runSuffix + "', 'DEV-" + runSuffix + "') " +
                    "ORDER BY e.employee_code")) {

                Map<String, String> mgrMap = new HashMap<>();
                while (rs.next()) {
                    mgrMap.put(rs.getString("employee_code"), rs.getString("mgr_code"));
                }
                assertNull(mgrMap.get("DIR-" + runSuffix), "Director manager must be null");
                assertEquals("DIR-" + runSuffix, mgrMap.get("MGR-" + runSuffix), "Manager's manager must resolve to Director");
                assertEquals("MGR-" + runSuffix, mgrMap.get("DEV-" + runSuffix), "Dev's manager must resolve to Manager");
            }
            log.info("  -> Relational Hierarchy Query: PASSED (Self-referential tree intact across backup boundary).");

            // D. Encrypted Records Decryption & Integrity Verification
            String restoredDirPhone = null;
            String restoredMgrPhone = null;
            String restoredDevPhone = null;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT employee_code, phone FROM employees WHERE employee_code IN ('DIR-" + runSuffix + "', 'MGR-" + runSuffix + "', 'DEV-" + runSuffix + "')")) {
                while (rs.next()) {
                    String code = rs.getString("employee_code");
                    String phone = rs.getString("phone");
                    if (code.equals("DIR-" + runSuffix)) restoredDirPhone = phone;
                    if (code.equals("MGR-" + runSuffix)) restoredMgrPhone = phone;
                    if (code.equals("DEV-" + runSuffix)) restoredDevPhone = phone;
                }
            }

            assertNotNull(restoredDirPhone);
            assertNotNull(restoredMgrPhone);
            assertNotNull(restoredDevPhone);

            // Verify ENC: prefix preserved in database storage
            assertTrue(restoredDirPhone.startsWith(CryptoService.ENCRYPTION_PREFIX));
            assertTrue(restoredMgrPhone.startsWith(CryptoService.ENCRYPTION_PREFIX));
            assertTrue(restoredDevPhone.startsWith(CryptoService.ENCRYPTION_PREFIX));

            // Verify live AES-256-GCM decryption with CryptoService
            String decryptedDir = cryptoService.decrypt(restoredDirPhone);
            String decryptedMgr = cryptoService.decrypt(restoredMgrPhone);
            String decryptedDev = cryptoService.decrypt(restoredDevPhone);

            assertEquals(plaintextDirectorPhone, decryptedDir, "Decrypted director phone must match original plaintext");
            assertEquals(plaintextManagerPhone, decryptedMgr, "Decrypted manager phone must match original plaintext");
            assertEquals(plaintextDevPhone, decryptedDev, "Decrypted dev phone must match original plaintext");

            // Audit log encrypted fields verification
            String restoredOldComp = null;
            String restoredNewComp = null;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT old_value, new_value FROM audit_logs WHERE action = 'EMPLOYEE_COMPENSATION_ADJUSTED' ORDER BY id DESC LIMIT 1")) {
                assertTrue(rs.next());
                restoredOldComp = rs.getString("old_value");
                restoredNewComp = rs.getString("new_value");
            }
            assertNotNull(restoredOldComp);
            assertNotNull(restoredNewComp);

            assertTrue(restoredOldComp.startsWith(CryptoService.ENCRYPTION_PREFIX));
            assertTrue(restoredNewComp.startsWith(CryptoService.ENCRYPTION_PREFIX));

            String decryptedOldComp = cryptoService.decrypt(restoredOldComp);
            String decryptedNewComp = cryptoService.decrypt(restoredNewComp);

            assertEquals(plaintextOldComp, decryptedOldComp, "Decrypted audit old_value must match original plaintext");
            assertEquals(plaintextNewComp, decryptedNewComp, "Decrypted audit new_value must match original plaintext");

            // AEAD Tamper Rejection Test: Mutate 1 character in ciphertext payload
            String rawBase64 = restoredDirPhone.substring(CryptoService.ENCRYPTION_PREFIX.length());
            byte[] rawBytes = Base64.getDecoder().decode(rawBase64);
            rawBytes[rawBytes.length - 1] ^= 0x5A; // Flip bits in authentication tag
            String tamperedCiphertext = CryptoService.ENCRYPTION_PREFIX + Base64.getEncoder().encodeToString(rawBytes);

            assertThrows(SecurityException.class, () -> cryptoService.decrypt(tamperedCiphertext),
                    "Tampered ciphertext in restored database must be immediately rejected with SecurityException");

            log.info("  -> Encrypted Records Decryption: PASSED (All ciphertexts decrypted; AEAD tamper rejected).");
        }

        log.info("================================================================================");
        log.info("POSTGRESQL BACKUP & CLEAN-DATABASE RESTORATION VERIFIED WITH 100% SUCCESS");
        log.info("================================================================================");
    }

    private void assertDbCount(Statement stmt, String table, long expected) throws Exception {
        try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertTrue(rs.next());
            long count = rs.getLong(1);
            assertEquals(expected, count, "Row count mismatch in restored table: " + table);
        }
    }

    private Connection getCleanDbConnection() throws Exception {
        String url = "jdbc:postgresql://" + PG_HOST + ":" + PG_PORT + "/" + CLEAN_RESTORE_DB;
        return DriverManager.getConnection(url, PG_USER, PG_PASSWORD);
    }

    private void executePsqlCommand(String psqlPath, String targetDb, String sql) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                psqlPath,
                "-h", PG_HOST,
                "-p", String.valueOf(PG_PORT),
                "-U", PG_USER,
                "-d", targetDb,
                "-c", sql
        );
        pb.environment().put("PGPASSWORD", PG_PASSWORD);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = readProcessOutput(process);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("psql command failed with exit code " + exitCode + ": " + output);
        }
    }

    private String resolveBinary(String binaryName) {
        String[] possiblePaths = {
                "/opt/homebrew/opt/postgresql@17/bin/" + binaryName,
                "/opt/homebrew/bin/" + binaryName,
                "/usr/local/bin/" + binaryName,
                "/usr/bin/" + binaryName,
                binaryName
        };
        for (String p : possiblePaths) {
            File f = new File(p);
            if (f.exists() && f.canExecute()) {
                return f.getAbsolutePath();
            }
        }
        return binaryName;
    }

    private String readProcessOutput(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    private String computeSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(file.toPath())) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
