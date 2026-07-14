package com.ticket.dto;

public class ConnectionPoolStatusDTO {
    private String role;
    private String poolName;
    private int maximumPoolSize;
    private int minimumIdle;
    private int activeConnections;
    private int idleConnections;
    private int totalConnections;
    private int threadsAwaitingConnection;
    private long connectionTimeoutMs;
    private long idleTimeoutMs;
    private long maxLifetimeMs;
    private long leakDetectionThresholdMs;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPoolName() {
        return poolName;
    }

    public void setPoolName(String poolName) {
        this.poolName = poolName;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    public int getMinimumIdle() {
        return minimumIdle;
    }

    public void setMinimumIdle(int minimumIdle) {
        this.minimumIdle = minimumIdle;
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

    public int getUsagePercent() {
        if (maximumPoolSize <= 0) {
            return 0;
        }
        return Math.min(100, Math.round(activeConnections * 100.0f / maximumPoolSize));
    }

    public int getAvailableConnections() {
        return Math.max(0, maximumPoolSize - activeConnections);
    }

    public String getStatusText() {
        if (threadsAwaitingConnection > 0) {
            return "存在等待";
        }
        if (maximumPoolSize > 0 && activeConnections >= maximumPoolSize) {
            return "连接已满";
        }
        if (getUsagePercent() >= 80) {
            return "负载较高";
        }
        return "正常";
    }
}
