package com.leave_management_system.leave_management_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Data Transfer Object for initiating a database restoration request.
 */
public class DatabaseRestoreRequestDTO {

    @NotBlank(message = "Backup filename must not be blank")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-\\.]+$", message = "Filename contains invalid characters")
    private String fileName;

    private String expectedSha256;

    public DatabaseRestoreRequestDTO() {
    }

    public DatabaseRestoreRequestDTO(String fileName, String expectedSha256) {
        this.fileName = fileName;
        this.expectedSha256 = expectedSha256;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getExpectedSha256() {
        return expectedSha256;
    }

    public void setExpectedSha256(String expectedSha256) {
        this.expectedSha256 = expectedSha256;
    }
}

