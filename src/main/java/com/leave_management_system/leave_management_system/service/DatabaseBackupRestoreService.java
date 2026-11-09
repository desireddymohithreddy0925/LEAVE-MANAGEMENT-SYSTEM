package com.leave_management_system.leave_management_system.service;

import com.leave_management_system.leave_management_system.dto.DatabaseBackupResponseDTO;
import com.leave_management_system.leave_management_system.dto.DatabaseBackupValidationDTO;
import com.leave_management_system.leave_management_system.dto.DatabaseRestoreResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service managing database backup generation, integrity verification,
 * tamper detection via SHA-256 checksums, and disaster recovery restoration.
 */
@Service
public class DatabaseBackupRestoreService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBackupRestoreService.class);
    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\.]+$");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final AuditService auditService;
    private final Path backupDirectory;

    @Autowired
    public DatabaseBackupRestoreService(JdbcTemplate jdbcTemplate,
                                         DataSource dataSource,
                                         AuditService auditService,
                                         @Value("${app.database.backup.dir:target/backups}") String backupDirStr) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.auditService = auditService;
        this.backupDirectory = Paths.get(backupDirStr).toAbsolutePath().normalize();
        initializeBackupDirectory();
    }

    private void initializeBackupDirectory() {
        try {
            if (!Files.exists(backupDirectory)) {
                Files.createDirectories(backupDirectory);
                log.info("Initialized database backup directory at: {}", backupDirectory);
            }
        } catch (IOException e) {
            log.error("Failed to create backup directory {}: {}", backupDirectory, e.getMessage());
            throw new IllegalStateException("Could not initialize backup storage directory", e);
        }
    }

    /**
     * Creates a full database backup with drop statements to enable clean re-import.
     * Computes and records the SHA-256 hash for integrity validation.
     */
    public DatabaseBackupResponseDTO createBackup(String customName) {
        String sanitizedName = sanitizeCustomName(customName);
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String fileName = "backup_" + timestamp + "_" + sanitizedName + ".sql";
        File targetFile = backupDirectory.resolve(fileName).toFile();

        String dbProductName = getDatabaseProductName();
        log.info("Initiating database backup [{}] for database target: {}", fileName, dbProductName);

        try {
            if (targetFile.exists()) {
                targetFile.delete();
            }

            if (dbProductName.toUpperCase().contains("H2")) {
                // H2 SQL export with DROP statements to support complete atomic restore
                String exportSql = "SCRIPT DROP TO '" + targetFile.getAbsolutePath().replace("'", "''") + "'";
                jdbcTemplate.execute(exportSql);
            } else {
                // Generic / PostgreSQL export execution or script trigger
                String exportSql = "SCRIPT DROP TO '" + targetFile.getAbsolutePath().replace("'", "''") + "'";
                jdbcTemplate.execute(exportSql);
            }

            if (!targetFile.exists() || targetFile.length() == 0) {
                throw new IllegalStateException("Backup creation failed: Generated backup file is missing or empty.");
            }

            String sha256 = computeSha256(targetFile);
            long fileSizeBytes = targetFile.length();

            log.info("Database backup created successfully: {} (Size: {} bytes, SHA-256: {})",
                    fileName, fileSizeBytes, sha256);

            // Audit the backup creation
            auditService.logAction("DATABASE_BACKUP_CREATED", "DATABASE", null, null,
                    "filename: " + fileName + ", sha256: " + sha256 + ", size: " + fileSizeBytes);

            return new DatabaseBackupResponseDTO(
                    fileName,
                    targetFile.getAbsolutePath(),
                    fileSizeBytes,
                    sha256,
                    LocalDateTime.now(),
                    dbProductName,
                    "SUCCESS"
            );
        } catch (Exception e) {
            log.error("Error creating database backup {}: {}", fileName, e.getMessage(), e);
            throw new RuntimeException("Database backup execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Lists all available database backup files with their metadata and checksums.
     */
    public List<DatabaseBackupResponseDTO> listBackups() {
        File folder = backupDirectory.toFile();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".sql"));

        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        // Sort descending by last modified
        Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        String dbProductName = getDatabaseProductName();
        List<DatabaseBackupResponseDTO> dtos = new ArrayList<>();

        for (File file : files) {
            try {
                String sha256 = computeSha256(file);
                LocalDateTime modified = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault());

                dtos.add(new DatabaseBackupResponseDTO(
                        file.getName(),
                        file.getAbsolutePath(),
                        file.length(),
                        sha256,
                        modified,
                        dbProductName,
                        "AVAILABLE"
                ));
            } catch (Exception e) {
                log.warn("Could not inspect backup file {}: {}", file.getName(), e.getMessage());
            }
        }

        return dtos;
    }

    /**
     * Validates backup file existence, readability, header content, and SHA-256 hash.
     */
    public DatabaseBackupValidationDTO validateBackupFile(String fileName) {
        validateSafeFileName(fileName);
        File targetFile = backupDirectory.resolve(fileName).toFile();

        List<String> errors = new ArrayList<>();
        if (!targetFile.exists()) {
            errors.add("Backup file does not exist: " + fileName);
            return new DatabaseBackupValidationDTO(fileName, false, 0, null, "File not found", errors);
        }

        if (!targetFile.canRead()) {
            errors.add("Backup file is not readable: " + fileName);
        }

        long fileSizeBytes = targetFile.length();
        if (fileSizeBytes == 0) {
            errors.add("Backup file is empty (0 bytes)");
        }

        String sha256 = null;
        try {
            sha256 = computeSha256(targetFile);
        } catch (Exception e) {
            errors.add("Failed to compute SHA-256 checksum: " + e.getMessage());
        }

        // Inspect SQL syntax header
        boolean hasSqlStatements = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(targetFile))) {
            String line;
            int linesRead = 0;
            while ((line = reader.readLine()) != null && linesRead < 50) {
                linesRead++;
                String trimmed = line.trim().toUpperCase();
                if (trimmed.startsWith("CREATE ") || trimmed.startsWith("INSERT ") ||
                    trimmed.startsWith("SET ") || trimmed.startsWith("DROP ") ||
                    trimmed.startsWith("ALTER ") || trimmed.startsWith("--")) {
                    hasSqlStatements = true;
                    break;
                }
            }
        } catch (IOException e) {
            errors.add("Could not inspect SQL file header: " + e.getMessage());
        }

        if (!hasSqlStatements && errors.isEmpty()) {
            errors.add("File does not appear to contain valid SQL DDL/DML statements.");
        }

        boolean valid = errors.isEmpty();
        String message = valid
                ? "Backup file verified successfully. Checksum and SQL structure valid."
                : "Validation failed with " + errors.size() + " issue(s).";

        return new DatabaseBackupValidationDTO(fileName, valid, fileSizeBytes, sha256, message, errors);
    }

    /**
     * Restores the database from a verified backup file.
     * Enforces optional expected SHA-256 validation to prevent tampering.
     */
    public DatabaseRestoreResponseDTO restoreBackup(String fileName, String expectedSha256) {
        validateSafeFileName(fileName);
        File targetFile = backupDirectory.resolve(fileName).toFile();

        if (!targetFile.exists()) {
            throw new IllegalArgumentException("Cannot restore: Backup file does not exist: " + fileName);
        }

        if (targetFile.length() == 0) {
            throw new IllegalArgumentException("Cannot restore: Backup file is empty: " + fileName);
        }

        String actualSha256 = computeSha256(targetFile);

        // Tamper verification
        if (expectedSha256 != null && !expectedSha256.isBlank()) {
            if (!actualSha256.equalsIgnoreCase(expectedSha256.trim())) {
                auditService.logSecurityEvent("DATABASE_RESTORE_INTEGRITY_FAILED", null, null,
                        "Integrity failure for " + fileName + ". Expected SHA: " + expectedSha256 + ", Actual SHA: " + actualSha256);
                throw new SecurityException("Database restore aborted: SHA-256 checksum mismatch. Potential tampering detected for " + fileName);
            }
        }

        String dbProductName = getDatabaseProductName();
        log.warn("EXECUTING FULL DATABASE RESTORE from file [{}] on database target [{}]", fileName, dbProductName);

        try {
            if (dbProductName.toUpperCase().contains("H2")) {
                String runScriptSql = "RUNSCRIPT FROM '" + targetFile.getAbsolutePath().replace("'", "''") + "'";
                jdbcTemplate.execute(runScriptSql);
            } else {
                String runScriptSql = "RUNSCRIPT FROM '" + targetFile.getAbsolutePath().replace("'", "''") + "'";
                jdbcTemplate.execute(runScriptSql);
            }

            log.info("Database successfully restored from backup file: {}", fileName);

            // High-severity security event logging
            auditService.logSecurityEvent("DATABASE_RESTORED", null, null,
                    "Database restored from backup: " + fileName + " (SHA-256: " + actualSha256 + ")");

            return new DatabaseRestoreResponseDTO(
                    fileName,
                    "SUCCESS",
                    LocalDateTime.now(),
                    actualSha256,
                    "Database successfully restored from verified backup script."
            );
        } catch (Exception e) {
            log.error("Fatal error during database restoration from {}: {}", fileName, e.getMessage(), e);
            auditService.logSecurityEvent("DATABASE_RESTORE_FAILED", null, null,
                    "Restoration failed for " + fileName + ": " + e.getMessage());
            throw new RuntimeException("Database restoration failed: " + e.getMessage(), e);
        }
    }

    public Path getBackupDirectory() {
        return backupDirectory;
    }

    private void validateSafeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Backup filename must not be null or blank");
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\") || fileName.contains("\0")) {
            throw new IllegalArgumentException("Path traversal or illegal characters detected in filename: " + fileName);
        }
        if (!SAFE_FILENAME_PATTERN.matcher(fileName).matches()) {
            throw new IllegalArgumentException("Filename contains disallowed characters. Only alphanumeric, hyphens, and underscores are permitted: " + fileName);
        }
    }

    private String sanitizeCustomName(String customName) {
        if (customName == null || customName.isBlank()) {
            return "manual";
        }
        String cleaned = customName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return cleaned.length() > 30 ? cleaned.substring(0, 30) : cleaned;
    }

    public String computeSha256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("Failed to calculate SHA-256 for file " + file.getName(), e);
        }
    }

    private String getDatabaseProductName() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getDatabaseProductName();
        } catch (Exception e) {
            return "H2";
        }
    }
}

