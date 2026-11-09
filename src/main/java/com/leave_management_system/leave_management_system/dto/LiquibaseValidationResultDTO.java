package com.leave_management_system.leave_management_system.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object representing the programmatic validation result of
 * Liquibase changelogs and checksums.
 */
public class LiquibaseValidationResultDTO {

    private boolean valid;
    private int errorCount;
    private List<String> errorMessages = new ArrayList<>();
    private List<String> warningMessages = new ArrayList<>();
    private String message;
    private LocalDateTime validatedAt;

    public LiquibaseValidationResultDTO() {
        this.validatedAt = LocalDateTime.now();
    }

    public LiquibaseValidationResultDTO(boolean valid, int errorCount, List<String> errorMessages,
                                         List<String> warningMessages, String message) {
        this.valid = valid;
        this.errorCount = errorCount;
        this.errorMessages = errorMessages != null ? errorMessages : new ArrayList<>();
        this.warningMessages = warningMessages != null ? warningMessages : new ArrayList<>();
        this.message = message;
        this.validatedAt = LocalDateTime.now();
    }

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public List<String> getErrorMessages() {
        return errorMessages;
    }

    public void setErrorMessages(List<String> errorMessages) {
        this.errorMessages = errorMessages;
    }

    public List<String> getWarningMessages() {
        return warningMessages;
    }

    public void setWarningMessages(List<String> warningMessages) {
        this.warningMessages = warningMessages;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(LocalDateTime validatedAt) {
        this.validatedAt = validatedAt;
    }
}

