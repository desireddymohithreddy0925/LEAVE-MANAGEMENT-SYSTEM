package com.leave_management_system.leave_management_system.database;

import com.leave_management_system.leave_management_system.entity.Department;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.LeaveRequest;
import com.leave_management_system.leave_management_system.entity.LeaveStatus;
import com.leave_management_system.leave_management_system.entity.LeaveType;
import com.leave_management_system.leave_management_system.repository.DepartmentRepository;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.LeaveRequestRepository;
import com.leave_management_system.leave_management_system.repository.LeaveTypeRepository;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class DatabaseBackupRestoreIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SpringLiquibase springLiquibase;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Test
    @DisplayName("Liquibase Schema Validation: All changesets execute cleanly with valid checksums")
    void liquibase_ChecksumsAndExecution_Verified() {
        assertNotNull(springLiquibase, "SpringLiquibase must be initialized");

        Integer changesetCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM databasechangelog", Integer.class);
        assertNotNull(changesetCount);
        assertTrue(changesetCount >= 26, "All 26+ changesets must be recorded in databasechangelog");

        // Verify forward-only migration 026 exists
        Integer migration026Count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM databasechangelog WHERE id LIKE '026%'", Integer.class);
        assertNotNull(migration026Count);
        assertTrue(migration026Count >= 1, "Migration 026 must be executed");
    }

    @Test
    @DisplayName("Database Backup and Restore: data integrity preserved across full backup/restore cycle")
    void databaseBackupAndRestore_DataIntegrityPreserved() {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);
        String deptName = "BackupDept_" + testSuffix;
        String empCode = "BCK-" + testSuffix;

        Department dept = new Department();
        dept.setName(deptName);
        dept = departmentRepository.save(dept);

        Employee emp = new Employee();
        emp.setFirstName("Backup");
        emp.setLastName("Tester");
        emp.setEmployeeCode(empCode);
        emp.setStatus("ACTIVE");
        emp.setSalary(new BigDecimal("75000.00"));
        emp.setDepartment(dept);
        emp = employeeRepository.save(emp);

        LeaveType lt = new LeaveType("BackupLeave_" + testSuffix, "Quota for backup test", 15);
        lt.setActive(true);
        lt = leaveTypeRepository.save(lt);

        LeaveRequest lr = new LeaveRequest();
        lr.setEmployee(emp);
        lr.setLeaveType(lt);
        lr.setStartDate(LocalDate.of(2027, 3, 1));
        lr.setEndDate(LocalDate.of(2027, 3, 5));
        lr.setStatus(LeaveStatus.APPROVED);
        lr = leaveRequestRepository.save(lr);

        Long savedRequestId = lr.getId();
        Long savedEmployeeId = emp.getId();
        Long savedDeptId = dept.getId();

        // 1. Create SQL backup file
        File backupFile = new File("target/test-db-backup.sql");
        if (backupFile.exists()) {
            backupFile.delete();
        }

        // Export database script with DROP statements to allow clean re-import
        jdbcTemplate.execute("SCRIPT DROP TO 'target/test-db-backup.sql'");
        assertTrue(backupFile.exists(), "Backup script must be generated on disk");
        assertTrue(backupFile.length() > 0, "Backup file must not be empty");

        // 2. Clear entities to simulate disaster/data loss
        leaveRequestRepository.deleteById(savedRequestId);
        employeeRepository.deleteById(savedEmployeeId);
        departmentRepository.deleteById(savedDeptId);

        assertFalse(leaveRequestRepository.findById(savedRequestId).isPresent(), "Leave request must be cleared");
        assertFalse(employeeRepository.findById(savedEmployeeId).isPresent(), "Employee must be cleared");
        assertFalse(departmentRepository.findById(savedDeptId).isPresent(), "Department must be cleared");

        // 3. Restore database from backup script
        jdbcTemplate.execute("RUNSCRIPT FROM 'target/test-db-backup.sql'");

        // 4. Verify data integrity after restore
        assertTrue(departmentRepository.findById(savedDeptId).isPresent(), "Department must be restored");
        assertEquals(deptName, departmentRepository.findById(savedDeptId).get().getName());

        assertTrue(employeeRepository.findById(savedEmployeeId).isPresent(), "Employee must be restored");
        assertEquals(empCode, employeeRepository.findById(savedEmployeeId).get().getEmployeeCode());

        assertTrue(leaveRequestRepository.findById(savedRequestId).isPresent(), "Leave request must be restored");
        LeaveRequest restoredRequest = leaveRequestRepository.findById(savedRequestId).get();
        assertEquals(LeaveStatus.APPROVED, restoredRequest.getStatus());
        assertEquals(LocalDate.of(2027, 3, 1), restoredRequest.getStartDate());
        assertEquals(LocalDate.of(2027, 3, 5), restoredRequest.getEndDate());

        // Cleanup
        if (backupFile.exists()) {
            backupFile.delete();
        }
    }

    @Test
    @DisplayName("Database Schema Evolution: columns active and revoked are boolean across tables")
    void databaseSchemaEvolution_BooleanColumnTypesVerified() {
        // Verify leave_types.active is boolean
        Integer ltActiveBit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE UPPER(table_name) = 'LEAVE_TYPES' AND UPPER(column_name) = 'ACTIVE' AND UPPER(data_type) = 'BOOLEAN'",
                Integer.class);
        assertEquals(1, ltActiveBit, "leave_types.active must be of type boolean");

        // Verify users.active is boolean
        Integer usersActive = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE UPPER(table_name) = 'USERS' AND UPPER(column_name) = 'ACTIVE' AND UPPER(data_type) = 'BOOLEAN'",
                Integer.class);
        assertEquals(1, usersActive, "users.active must be of type boolean");

        // Verify refresh_tokens.revoked is boolean
        Integer tokensRevoked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE UPPER(table_name) = 'REFRESH_TOKENS' AND UPPER(column_name) = 'REVOKED' AND UPPER(data_type) = 'BOOLEAN'",
                Integer.class);
        assertEquals(1, tokensRevoked, "refresh_tokens.revoked must be of type boolean");
    }
}
