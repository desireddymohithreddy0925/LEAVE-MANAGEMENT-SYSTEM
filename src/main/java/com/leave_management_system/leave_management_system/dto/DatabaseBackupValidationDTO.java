package com.leave_management_system.leave_management_system.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object representing backup file verification and integrity results.
 */
public class DatabaseBackupValidationDTO {

    private String fileName;
    private boolean valid;
    private long fileSizeBytes;
    private String sha256Checksum;
    private String message;
    private List<String> validationErrors = new ArrayList<>();

    public DatabaseBackupValidationDTO() {
    }

    public DatabaseBackupValidationDTO(String fileName, boolean valid, long fileSizeBytes,
                                       String sha256Checksum, String message, List<String> validationErrors) {
        this.fileName = fileName;
        this.valid = valid;
        this.fileSizeBytes = fileSizeBytes;
        this.sha256Checksum = sha256Checksum;
        this.message = message;
        this.validationErrors = validationErrors != null ? validationErrors : new ArrayList<>();
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<String> validationErrors) {
        this.validationErrors = validationErrors;
    }
}

