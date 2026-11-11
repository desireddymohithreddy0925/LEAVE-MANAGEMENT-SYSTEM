package com.leave_management_system.leave_management_system.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object representing database query diagnostics, index coverage,
 * and N+1 query prevention audits.
 */
public class QueryPerformanceDiagnosticsDTO {

    private int monitoredTablesCount;
    private int totalIndexesChecked;
    private List<String> indexesVerified = new ArrayList<>();
    private String status; // OPTIMAL, ATTENTION_NEEDED
    private List<String> nPlusOneProtections = new ArrayList<>();
    private List<String> recommendations = new ArrayList<>();
    private LocalDateTime timestamp;

    public QueryPerformanceDiagnosticsDTO() {
        this.timestamp = LocalDateTime.now();
    }

    public int getMonitoredTablesCount() {
        return monitoredTablesCount;
    }

    public void setMonitoredTablesCount(int monitoredTablesCount) {
        this.monitoredTablesCount = monitoredTablesCount;
    }

    public int getTotalIndexesChecked() {
        return totalIndexesChecked;
    }

    public void setTotalIndexesChecked(int totalIndexesChecked) {
        this.totalIndexesChecked = totalIndexesChecked;
    }

    public List<String> getIndexesVerified() {
        return indexesVerified;
    }

    public void setIndexesVerified(List<String> indexesVerified) {
        this.indexesVerified = indexesVerified;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getnPlusOneProtections() {
        return nPlusOneProtections;
    }

    public void setnPlusOneProtections(List<String> nPlusOneProtections) {
        this.nPlusOneProtections = nPlusOneProtections;
    }

    public List<String> getNPlusOneProtections() {
        return nPlusOneProtections;
    }

    public void setNPlusOneProtections(List<String> nPlusOneProtections) {
        this.nPlusOneProtections = nPlusOneProtections;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<String> recommendations) {
        this.recommendations = recommendations;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
