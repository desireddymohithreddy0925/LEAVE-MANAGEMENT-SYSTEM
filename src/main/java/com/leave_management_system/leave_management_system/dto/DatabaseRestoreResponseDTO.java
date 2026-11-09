package com.leave_management_system.leave_management_system.dto;

import java.time.LocalDateTime;

/**
 * Data Transfer Object representing the outcome of a database restoration operation.
 */
public class DatabaseRestoreResponseDTO {

    private String fileName;
    private String status;
    private LocalDateTime restoredAt;
    private String sha256Checksum;
    private String message;

    public DatabaseRestoreResponseDTO() {
        this.restoredAt = LocalDateTime.now();
    }

    public DatabaseRestoreResponseDTO(String fileName, String status, LocalDateTime restoredAt,
                                      String sha256Checksum, String message) {
        this.fileName = fileName;
        this.status = status;
        this.restoredAt = restoredAt;
        this.sha256Checksum = sha256Checksum;
        this.message = message;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getRestoredAt() {
        return restoredAt;
    }

    public void setRestoredAt(LocalDateTime restoredAt) {
        this.restoredAt = restoredAt;
    }

    public String getSha256Checksum() {
        return sha256Checksum;
    }

    public void setSha256Checksum(String sha256Checksum) {
        this.sha256Checksum = sha256Checksum;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

