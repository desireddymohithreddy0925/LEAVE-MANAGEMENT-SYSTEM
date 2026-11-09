package com.leave_management_system.leave_management_system.dto;

import java.time.LocalDateTime;

/**
 * Data Transfer Object representing idempotency key storage health, active keys,
 * expired counts, and interrupted request monitoring.
 */
public class IdempotencyAuditReportDTO {

    private String status; // HEALTHY, MAINTENANCE_REQUIRED
    private long totalKeys;
    private long completedKeys;
    private long inProgressKeys;
    private long failedKeys;
    private long expiredKeys;
    private long orphanedKeys;
    private long inProgressTimeoutSeconds;
    private LocalDateTime auditTimestamp;

    public IdempotencyAuditReportDTO() {
        this.auditTimestamp = LocalDateTime.now();
    }

    public IdempotencyAuditReportDTO(String status, long totalKeys, long completedKeys,
                                     long inProgressKeys, long failedKeys, long expiredKeys,
                                     long orphanedKeys, long inProgressTimeoutSeconds) {
        this.status = status;
        this.totalKeys = totalKeys;
        this.completedKeys = completedKeys;
        this.inProgressKeys = inProgressKeys;
        this.failedKeys = failedKeys;
        this.expiredKeys = expiredKeys;
        this.orphanedKeys = orphanedKeys;
        this.inProgressTimeoutSeconds = inProgressTimeoutSeconds;
        this.auditTimestamp = LocalDateTime.now();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getTotalKeys() {
        return totalKeys;
    }

    public void setTotalKeys(long totalKeys) {
        this.totalKeys = totalKeys;
    }

    public long getCompletedKeys() {
        return completedKeys;
    }

    public void setCompletedKeys(long completedKeys) {
        this.completedKeys = completedKeys;
    }

    public long getInProgressKeys() {
        return inProgressKeys;
    }

    public void setInProgressKeys(long inProgressKeys) {
        this.inProgressKeys = inProgressKeys;
    }

    public long getFailedKeys() {
        return failedKeys;
    }

    public void setFailedKeys(long failedKeys) {
        this.failedKeys = failedKeys;
    }

    public long getExpiredKeys() {
        return expiredKeys;
    }

    public void setExpiredKeys(long expiredKeys) {
        this.expiredKeys = expiredKeys;
    }

    public long getOrphanedKeys() {
        return orphanedKeys;
    }

    public void setOrphanedKeys(long orphanedKeys) {
        this.orphanedKeys = orphanedKeys;
    }

    public long getInProgressTimeoutSeconds() {
        return inProgressTimeoutSeconds;
    }

    public void setInProgressTimeoutSeconds(long inProgressTimeoutSeconds) {
        this.inProgressTimeoutSeconds = inProgressTimeoutSeconds;
    }

    public LocalDateTime getAuditTimestamp() {
        return auditTimestamp;
    }

    public void setAuditTimestamp(LocalDateTime auditTimestamp) {
        this.auditTimestamp = auditTimestamp;
    }
}

