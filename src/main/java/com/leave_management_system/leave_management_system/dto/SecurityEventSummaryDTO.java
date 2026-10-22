package com.leave_management_system.leave_management_system.dto;

import java.util.Map;

public class SecurityEventSummaryDTO {

    private long totalEvents;
    private long openCount;
    private long investigatingCount;
    private long resolvedCount;
    private long falsePositiveCount;
    private long lowSeverityCount;
    private long mediumSeverityCount;
    private long highSeverityCount;
    private long criticalSeverityCount;
    private long unresolvedHighRiskCount;
    private double resolutionRatePercentage;
    private Map<String, Long> eventsByType;

    public SecurityEventSummaryDTO() {
    }

    public long getTotalEvents() {
        return totalEvents;
    }

    public void setTotalEvents(long totalEvents) {
        this.totalEvents = totalEvents;
    }

    public long getOpenCount() {
        return openCount;
    }

    public void setOpenCount(long openCount) {
        this.openCount = openCount;
    }

    public long getInvestigatingCount() {
        return investigatingCount;
    }

    public void setInvestigatingCount(long investigatingCount) {
        this.investigatingCount = investigatingCount;
    }

    public long getResolvedCount() {
        return resolvedCount;
    }

    public void setResolvedCount(long resolvedCount) {
        this.resolvedCount = resolvedCount;
    }

    public long getFalsePositiveCount() {
        return falsePositiveCount;
    }

    public void setFalsePositiveCount(long falsePositiveCount) {
        this.falsePositiveCount = falsePositiveCount;
    }

    public long getLowSeverityCount() {
        return lowSeverityCount;
    }

    public void setLowSeverityCount(long lowSeverityCount) {
        this.lowSeverityCount = lowSeverityCount;
    }

    public long getMediumSeverityCount() {
        return mediumSeverityCount;
    }

    public void setMediumSeverityCount(long mediumSeverityCount) {
        this.mediumSeverityCount = mediumSeverityCount;
    }

    public long getHighSeverityCount() {
        return highSeverityCount;
    }

    public void setHighSeverityCount(long highSeverityCount) {
        this.highSeverityCount = highSeverityCount;
    }

    public long getCriticalSeverityCount() {
        return criticalSeverityCount;
    }

    public void setCriticalSeverityCount(long criticalSeverityCount) {
        this.criticalSeverityCount = criticalSeverityCount;
    }

    public long getUnresolvedHighRiskCount() {
        return unresolvedHighRiskCount;
    }

    public void setUnresolvedHighRiskCount(long unresolvedHighRiskCount) {
        this.unresolvedHighRiskCount = unresolvedHighRiskCount;
    }

    public double getResolutionRatePercentage() {
        return resolutionRatePercentage;
    }

    public void setResolutionRatePercentage(double resolutionRatePercentage) {
        this.resolutionRatePercentage = resolutionRatePercentage;
    }

    public Map<String, Long> getEventsByType() {
        return eventsByType;
    }

    public void setEventsByType(Map<String, Long> eventsByType) {
        this.eventsByType = eventsByType;
    }
}
