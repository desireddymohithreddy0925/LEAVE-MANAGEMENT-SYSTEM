package com.leave_management_system.leave_management_system.dto;

import java.time.LocalDateTime;

public class AuthorizationSummaryDTO {

    private long totalDecisions;
    private long grantedCount;
    private long deniedCount;
    private double denialRatePercentage;
    private LocalDateTime lastDecisionAt;

    public AuthorizationSummaryDTO() {
    }

    public AuthorizationSummaryDTO(long totalDecisions, long grantedCount, long deniedCount,
                                   double denialRatePercentage, LocalDateTime lastDecisionAt) {
        this.totalDecisions = totalDecisions;
        this.grantedCount = grantedCount;
        this.deniedCount = deniedCount;
        this.denialRatePercentage = denialRatePercentage;
        this.lastDecisionAt = lastDecisionAt;
    }

    public long getTotalDecisions() {
        return totalDecisions;
    }

    public void setTotalDecisions(long totalDecisions) {
        this.totalDecisions = totalDecisions;
    }

    public long getGrantedCount() {
        return grantedCount;
    }

    public void setGrantedCount(long grantedCount) {
        this.grantedCount = grantedCount;
    }

    public long getDeniedCount() {
        return deniedCount;
    }

    public void setDeniedCount(long deniedCount) {
        this.deniedCount = deniedCount;
    }

    public double getDenialRatePercentage() {
        return denialRatePercentage;
    }

    public void setDenialRatePercentage(double denialRatePercentage) {
        this.denialRatePercentage = denialRatePercentage;
    }

    public LocalDateTime getLastDecisionAt() {
        return lastDecisionAt;
    }

    public void setLastDecisionAt(LocalDateTime lastDecisionAt) {
        this.lastDecisionAt = lastDecisionAt;
    }
}
