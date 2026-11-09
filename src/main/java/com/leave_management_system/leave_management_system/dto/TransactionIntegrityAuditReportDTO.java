package com.leave_management_system.leave_management_system.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object representing the database transaction integrity audit report,
 * verifying balance equation invariants, optimistic locking, and relational consistency.
 */
public class TransactionIntegrityAuditReportDTO {

    private String overallStatus; // HEALTHY, ANOMALY_DETECTED
    private boolean invariantsPreserved;
    private long totalBalanceRecordsAudited;
    private int balanceAnomaliesCount;
    private List<String> balanceAnomalies = new ArrayList<>();
    private boolean optimisticLockingAudited;
    private boolean signoffIntegrityPreserved;
    private int signoffAnomaliesCount;
    private List<String> signoffAnomalies = new ArrayList<>();
    private long totalTransactionReviews;
    private LocalDateTime auditTimestamp;

    public TransactionIntegrityAuditReportDTO() {
        this.auditTimestamp = LocalDateTime.now();
    }

    public String getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(String overallStatus) {
        this.overallStatus = overallStatus;
    }

    public boolean isInvariantsPreserved() {
        return invariantsPreserved;
    }

    public void setInvariantsPreserved(boolean invariantsPreserved) {
        this.invariantsPreserved = invariantsPreserved;
    }

    public long getTotalBalanceRecordsAudited() {
        return totalBalanceRecordsAudited;
    }

    public void setTotalBalanceRecordsAudited(long totalBalanceRecordsAudited) {
        this.totalBalanceRecordsAudited = totalBalanceRecordsAudited;
    }

    public int getBalanceAnomaliesCount() {
        return balanceAnomaliesCount;
    }

    public void setBalanceAnomaliesCount(int balanceAnomaliesCount) {
        this.balanceAnomaliesCount = balanceAnomaliesCount;
    }

    public List<String> getBalanceAnomalies() {
        return balanceAnomalies;
    }

    public void setBalanceAnomalies(List<String> balanceAnomalies) {
        this.balanceAnomalies = balanceAnomalies;
    }

    public boolean isOptimisticLockingAudited() {
        return optimisticLockingAudited;
    }

    public void setOptimisticLockingAudited(boolean optimisticLockingAudited) {
        this.optimisticLockingAudited = optimisticLockingAudited;
    }

    public boolean isSignoffIntegrityPreserved() {
        return signoffIntegrityPreserved;
    }

    public void setSignoffIntegrityPreserved(boolean signoffIntegrityPreserved) {
        this.signoffIntegrityPreserved = signoffIntegrityPreserved;
    }

    public int getSignoffAnomaliesCount() {
        return signoffAnomaliesCount;
    }

    public void setSignoffAnomaliesCount(int signoffAnomaliesCount) {
        this.signoffAnomaliesCount = signoffAnomaliesCount;
    }

    public List<String> getSignoffAnomalies() {
        return signoffAnomalies;
    }

    public void setSignoffAnomalies(List<String> signoffAnomalies) {
        this.signoffAnomalies = signoffAnomalies;
    }

    public long getTotalTransactionReviews() {
        return totalTransactionReviews;
    }

    public void setTotalTransactionReviews(long totalTransactionReviews) {
        this.totalTransactionReviews = totalTransactionReviews;
    }

    public LocalDateTime getAuditTimestamp() {
        return auditTimestamp;
    }

    public void setAuditTimestamp(LocalDateTime auditTimestamp) {
        this.auditTimestamp = auditTimestamp;
    }
}

