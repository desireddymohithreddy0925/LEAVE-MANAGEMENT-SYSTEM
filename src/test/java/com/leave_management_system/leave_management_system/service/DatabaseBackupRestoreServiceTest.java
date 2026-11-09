package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.DatabaseBackupValidationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseBackupRestoreService Unit Tests")
class DatabaseBackupRestoreServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DataSource dataSource;

    @Mock
    private AuditService auditService;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metaData;

    @TempDir
    Path tempDir;

    private DatabaseBackupRestoreService backupService;

    @BeforeEach
    void setUp() {
        backupService = new DatabaseBackupRestoreService(
                jdbcTemplate, dataSource, auditService, tempDir.toString());
    }

    @Test
    @DisplayName("validateBackupFile rejects path traversal attempts")
    void validateBackupFile_RejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class, () ->
                backupService.validateBackupFile("../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () ->
                backupService.validateBackupFile("sub/file.sql"));
        assertThrows(IllegalArgumentException.class, () ->
                backupService.validateBackupFile(""));
    }

    @Test
    @DisplayName("validateBackupFile returns invalid when file does not exist")
    void validateBackupFile_NonExistentFile() {
        DatabaseBackupValidationDTO result = backupService.validateBackupFile("non_existent.sql");
        assertNotNull(result);
        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("File not found") || result.getValidationErrors().size() > 0);
    }

    @Test
    @DisplayName("validateBackupFile validates genuine SQL backup file")
    void validateBackupFile_GenuineSqlFile() throws IOException {
        File sqlFile = tempDir.resolve("backup_test.sql").toFile();
        try (FileWriter writer = new FileWriter(sqlFile)) {
            writer.write("-- Database Backup Script\n");
            writer.write("CREATE TABLE TEST (ID INT);\n");
            writer.write("INSERT INTO TEST VALUES (1);\n");
        }

        DatabaseBackupValidationDTO result = backupService.validateBackupFile("backup_test.sql");
        assertNotNull(result);
        assertTrue(result.isValid());
        assertEquals(sqlFile.length(), result.getFileSizeBytes());
        assertNotNull(result.getSha256Checksum());
        assertEquals(64, result.getSha256Checksum().length());
    }

    @Test
    @DisplayName("restoreBackup throws SecurityException on SHA-256 checksum mismatch")
    void restoreBackup_ThrowsSecurityExceptionOnChecksumMismatch() throws IOException {
        File sqlFile = tempDir.resolve("tampered.sql").toFile();
        try (FileWriter writer = new FileWriter(sqlFile)) {
            writer.write("CREATE TABLE TAMPERED (ID INT);\n");
        }

        String wrongChecksum = "0000000000000000000000000000000000000000000000000000000000000000";

        SecurityException ex = assertThrows(SecurityException.class, () ->
                backupService.restoreBackup("tampered.sql", wrongChecksum));

        assertTrue(ex.getMessage().contains("Checksum mismatch") || ex.getMessage().contains("tampering"));
        verify(auditService, times(1)).logSecurityEvent(eq("DATABASE_RESTORE_INTEGRITY_FAILED"), any(), any(), any());
    }

    @Test
    @DisplayName("restoreBackup rejects path traversal attempt")
    void restoreBackup_RejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class, () ->
                backupService.restoreBackup("../../secret.sql", null));
    }
}

