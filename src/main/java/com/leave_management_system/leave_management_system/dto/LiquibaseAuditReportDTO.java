package com.leave_management_system.leave_management_system.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object representing the complete Liquibase migration and checksum
 * consistency audit report.
 */
public class LiquibaseAuditReportDTO {

    private String overallStatus; // HEALTHY, DRIFT_DETECTED, UNRUN_CHANGESETS, LOCKED
    private String databaseProductName;
    private String changeLogFile;
    private int totalChangesetsExecuted;
    private int unrunChangesetsCount;
    private boolean allChecksumsPresent;
    private boolean checksumDriftDetected;
    private String databaseChangeLogTable;
    private String databaseChangeLogLockTable;
    private LiquibaseLockStatusDTO lockStatus;
    private List<LiquibaseChangesetDTO> executedChangesets = new ArrayList<>();
    private LocalDateTime auditTimestamp;

    public LiquibaseAuditReportDTO() {
        this.auditTimestamp = LocalDateTime.now();
    }

    public String getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(String overallStatus) {
        this.overallStatus = overallStatus;
    }

    public String getDatabaseProductName() {
        return databaseProductName;
    }

    public void setDatabaseProductName(String databaseProductName) {
        this.databaseProductName = databaseProductName;
    }

    public String getChangeLogFile() {
        return changeLogFile;
    }

    public void setChangeLogFile(String changeLogFile) {
        this.changeLogFile = changeLogFile;
    }

    public int getTotalChangesetsExecuted() {
        return totalChangesetsExecuted;
    }

    public void setTotalChangesetsExecuted(int totalChangesetsExecuted) {
        this.totalChangesetsExecuted = totalChangesetsExecuted;
    }

    public int getUnrunChangesetsCount() {
        return unrunChangesetsCount;
    }

    public void setUnrunChangesetsCount(int unrunChangesetsCount) {
        this.unrunChangesetsCount = unrunChangesetsCount;
    }

    public boolean isAllChecksumsPresent() {
        return allChecksumsPresent;
    }

    public void setAllChecksumsPresent(boolean allChecksumsPresent) {
        this.allChecksumsPresent = allChecksumsPresent;
    }

    public boolean isChecksumDriftDetected() {
        return checksumDriftDetected;
    }

    public void setChecksumDriftDetected(boolean checksumDriftDetected) {
        this.checksumDriftDetected = checksumDriftDetected;
    }

    public String getDatabaseChangeLogTable() {
        return databaseChangeLogTable;
    }

    public void setDatabaseChangeLogTable(String databaseChangeLogTable) {
        this.databaseChangeLogTable = databaseChangeLogTable;
    }

    public String getDatabaseChangeLogLockTable() {
        return databaseChangeLogLockTable;
    }

    public void setDatabaseChangeLogLockTable(String databaseChangeLogLockTable) {
        this.databaseChangeLogLockTable = databaseChangeLogLockTable;
    }

    public LiquibaseLockStatusDTO getLockStatus() {
        return lockStatus;
    }

    public void setLockStatus(LiquibaseLockStatusDTO lockStatus) {
        this.lockStatus = lockStatus;
    }

    public List<LiquibaseChangesetDTO> getExecutedChangesets() {
        return executedChangesets;
    }

    public void setExecutedChangesets(List<LiquibaseChangesetDTO> executedChangesets) {
        this.executedChangesets = executedChangesets;
    }

    public LocalDateTime getAuditTimestamp() {
        return auditTimestamp;
    }

    public void setAuditTimestamp(LocalDateTime auditTimestamp) {
        this.auditTimestamp = auditTimestamp;
    }
}

