package com.leave_management_system.leave_management_system.dto;

import java.time.LocalDateTime;

/**
 * Data Transfer Object representing metadata of a generated database backup.
 */
public class DatabaseBackupResponseDTO {

    private String fileName;
    private String filePath;
    private long fileSizeBytes;
    private String sha256Checksum;
    private LocalDateTime createdAt;
    private String databaseProductName;
    private String status;

    public DatabaseBackupResponseDTO() {
    }

    public DatabaseBackupResponseDTO(String fileName, String filePath, long fileSizeBytes,
                                     String sha256Checksum, LocalDateTime createdAt,
                                     String databaseProductName, String status) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.fileSizeBytes = fileSizeBytes;
        this.sha256Checksum = sha256Checksum;
        this.createdAt = createdAt;
        this.databaseProductName = databaseProductName;
        this.status = status;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getSha256Checksum() {
        return sha256Checksum;
    }

    public void setSha256Checksum(String sha256Checksum) {
        this.sha256Checksum = sha256Checksum;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getDatabaseProductName() {
        return databaseProductName;
    }

    public void setDatabaseProductName(String databaseProductName) {
        this.databaseProductName = databaseProductName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

