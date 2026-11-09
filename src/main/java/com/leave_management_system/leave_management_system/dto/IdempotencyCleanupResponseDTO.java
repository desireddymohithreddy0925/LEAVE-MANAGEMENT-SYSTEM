package com.leave_management_system.leave_management_system.dto;

import java.time.LocalDateTime;

/**
 * Data Transfer Object representing the result of an idempotency key cleanup operation.
 */
public class IdempotencyCleanupResponseDTO {

    private int deletedExpiredCount;
    private int deletedInterruptedCount;
    private LocalDateTime cleanedAt;
    private String message;

    public IdempotencyCleanupResponseDTO() {
        this.cleanedAt = LocalDateTime.now();
    }

    public IdempotencyCleanupResponseDTO(int deletedExpiredCount, int deletedInterruptedCount, String message) {
        this.deletedExpiredCount = deletedExpiredCount;
        this.deletedInterruptedCount = deletedInterruptedCount;
        this.cleanedAt = LocalDateTime.now();
        this.message = message;
    }

    public int getDeletedExpiredCount() {
        return deletedExpiredCount;
    }

    public void setDeletedExpiredCount(int deletedExpiredCount) {
        this.deletedExpiredCount = deletedExpiredCount;
    }

    public int getDeletedInterruptedCount() {
        return deletedInterruptedCount;
    }

    public void setDeletedInterruptedCount(int deletedInterruptedCount) {
        this.deletedInterruptedCount = deletedInterruptedCount;
    }

    public LocalDateTime getCleanedAt() {
        return cleanedAt;
    }

    public void setCleanedAt(LocalDateTime cleanedAt) {
        this.cleanedAt = cleanedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

