package com.leave_management_system.leave_management_system.dto;

import java.time.LocalDateTime;

/**
 * Data Transfer Object providing runtime system health, JVM memory telemetry,
 * thread metrics, and SLA compliance targets.
 */
public class SystemPerformanceSummaryDTO {

    private String status; // OPTIMAL, DEGRADED
    private int availableProcessors;
    private double systemLoadAverage;
    private double heapUsedMb;
    private double heapMaxMb;
    private double heapUsagePercentage;
    private long readSlaTargetMs;
    private long writeSlaTargetMs;
    private int activePoolConnections;
    private LocalDateTime timestamp;

    public SystemPerformanceSummaryDTO() {
        this.timestamp = LocalDateTime.now();
    }

    public SystemPerformanceSummaryDTO(String status, int availableProcessors, double systemLoadAverage,
                                      double heapUsedMb, double heapMaxMb, double heapUsagePercentage,
                                      long readSlaTargetMs, long writeSlaTargetMs, int activePoolConnections) {
        this.status = status;
        this.availableProcessors = availableProcessors;
        this.systemLoadAverage = systemLoadAverage;
        this.heapUsedMb = heapUsedMb;
        this.heapMaxMb = heapMaxMb;
        this.heapUsagePercentage = heapUsagePercentage;
        this.readSlaTargetMs = readSlaTargetMs;
        this.writeSlaTargetMs = writeSlaTargetMs;
        this.activePoolConnections = activePoolConnections;
        this.timestamp = LocalDateTime.now();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAvailableProcessors() {
        return availableProcessors;
    }

    public void setAvailableProcessors(int availableProcessors) {
        this.availableProcessors = availableProcessors;
    }

    public double getSystemLoadAverage() {
        return systemLoadAverage;
    }

    public void setSystemLoadAverage(double systemLoadAverage) {
        this.systemLoadAverage = systemLoadAverage;
    }

    public double getHeapUsedMb() {
        return heapUsedMb;
    }

    public void setHeapUsedMb(double heapUsedMb) {
        this.heapUsedMb = heapUsedMb;
    }

    public double getUsedMemoryMb() {
        return heapUsedMb;
    }

    public void setUsedMemoryMb(double usedMemoryMb) {
        this.heapUsedMb = usedMemoryMb;
    }

    public double getHeapMaxMb() {
        return heapMaxMb;
    }

    public void setHeapMaxMb(double heapMaxMb) {
        this.heapMaxMb = heapMaxMb;
    }

    public double getMaxMemoryMb() {
        return heapMaxMb;
    }

    public void setMaxMemoryMb(double maxMemoryMb) {
        this.heapMaxMb = maxMemoryMb;
    }

    public double getHeapUsagePercentage() {
        return heapUsagePercentage;
    }

    public void setHeapUsagePercentage(double heapUsagePercentage) {
        this.heapUsagePercentage = heapUsagePercentage;
    }

    public long getReadSlaTargetMs() {
        return readSlaTargetMs;
    }

    public void setReadSlaTargetMs(long readSlaTargetMs) {
        this.readSlaTargetMs = readSlaTargetMs;
    }

    public long getTargetReadSlaMs() {
        return readSlaTargetMs;
    }

    public void setTargetReadSlaMs(long targetReadSlaMs) {
        this.readSlaTargetMs = targetReadSlaMs;
    }

    public long getWriteSlaTargetMs() {
        return writeSlaTargetMs;
    }

    public void setWriteSlaTargetMs(long writeSlaTargetMs) {
        this.writeSlaTargetMs = writeSlaTargetMs;
    }

    public long getTargetWriteSlaMs() {
        return writeSlaTargetMs;
    }

    public void setTargetWriteSlaMs(long targetWriteSlaMs) {
        this.writeSlaTargetMs = targetWriteSlaMs;
    }

    public int getActivePoolConnections() {
        return activePoolConnections;
    }

    public void setActivePoolConnections(int activePoolConnections) {
        this.activePoolConnections = activePoolConnections;
    }

    public int getActiveDatabaseConnections() {
        return activePoolConnections;
    }

    public void setActiveDatabaseConnections(int activeDatabaseConnections) {
        this.activePoolConnections = activeDatabaseConnections;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
