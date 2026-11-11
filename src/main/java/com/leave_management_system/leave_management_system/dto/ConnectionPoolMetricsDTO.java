package com.leave_management_system.leave_management_system.dto;

import java.time.LocalDateTime;

/**
 * Data Transfer Object representing runtime HikariCP connection pool health,
 * active leases, thread contention, and configuration parameters.
 */
public class ConnectionPoolMetricsDTO {

    private String poolName;
    private int activeConnections;
    private int idleConnections;
    private int totalConnections;
    private int threadsAwaitingConnection;
    private int maximumPoolSize;
    private int minimumIdle;
    private long connectionTimeoutMs;
    private long idleTimeoutMs;
    private long maxLifetimeMs;
    private long leakDetectionThresholdMs;
    private String poolStatus; // HEALTHY, WARNING, CRITICAL
    private LocalDateTime timestamp;

    public ConnectionPoolMetricsDTO() {
        this.timestamp = LocalDateTime.now();
    }

    public ConnectionPoolMetricsDTO(String poolName, int activeConnections, int idleConnections,
                                    int totalConnections, int threadsAwaitingConnection,
                                    int maximumPoolSize, int minimumIdle, long connectionTimeoutMs,
                                    long idleTimeoutMs, long maxLifetimeMs, long leakDetectionThresholdMs,
                                    String poolStatus) {
        this.poolName = poolName;
        this.activeConnections = activeConnections;
        this.idleConnections = idleConnections;
        this.totalConnections = totalConnections;
        this.threadsAwaitingConnection = threadsAwaitingConnection;
        this.maximumPoolSize = maximumPoolSize;
        this.minimumIdle = minimumIdle;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.idleTimeoutMs = idleTimeoutMs;
        this.maxLifetimeMs = maxLifetimeMs;
        this.leakDetectionThresholdMs = leakDetectionThresholdMs;
        this.poolStatus = poolStatus;
        this.timestamp = LocalDateTime.now();
    }

    public String getPoolName() {
        return poolName;
    }

    public void setPoolName(String poolName) {
        this.poolName = poolName;
    }

    public int getActiveConnections() {
        return activeConnections;
    }

    public void setActiveConnections(int activeConnections) {
        this.activeConnections = activeConnections;
    }

    public int getIdleConnections() {
        return idleConnections;
    }

    public void setIdleConnections(int idleConnections) {
        this.idleConnections = idleConnections;
    }

    public int getTotalConnections() {
        return totalConnections;
    }

    public void setTotalConnections(int totalConnections) {
        this.totalConnections = totalConnections;
    }

    public int getThreadsAwaitingConnection() {
        return threadsAwaitingConnection;
    }

    public void setThreadsAwaitingConnection(int threadsAwaitingConnection) {
        this.threadsAwaitingConnection = threadsAwaitingConnection;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    public int getMaxPoolSize() {
        return maximumPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maximumPoolSize = maxPoolSize;
    }

    public int getMinimumIdle() {
        return minimumIdle;
    }

    public void setMinimumIdle(int minimumIdle) {
        this.minimumIdle = minimumIdle;
    }

    public long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public void setConnectionTimeoutMs(long connectionTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    public long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }

    public void setIdleTimeoutMs(long idleTimeoutMs) {
        this.idleTimeoutMs = idleTimeoutMs;
    }

    public long getMaxLifetimeMs() {
        return maxLifetimeMs;
    }

    public void setMaxLifetimeMs(long maxLifetimeMs) {
        this.maxLifetimeMs = maxLifetimeMs;
    }

    public long getLeakDetectionThresholdMs() {
        return leakDetectionThresholdMs;
    }

    public void setLeakDetectionThresholdMs(long leakDetectionThresholdMs) {
        this.leakDetectionThresholdMs = leakDetectionThresholdMs;
    }

    public String getPoolStatus() {
        return poolStatus;
    }

    public void setPoolStatus(String poolStatus) {
        this.poolStatus = poolStatus;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
