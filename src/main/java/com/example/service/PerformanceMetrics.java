package com.example.service;

/**
 * 性能指标数据类
 */
public class PerformanceMetrics {
    private long totalQueries;
    private long slowQueries;
    private long averageQueryTime;
    private long connectionErrors;
    private double cpuUsage;
    private int activeThreads;
    private int queueSize;
    private int activeConnections;

    // Getters and Setters
    public long getTotalQueries() { return totalQueries; }
    public void setTotalQueries(long totalQueries) { this.totalQueries = totalQueries; }

    public long getSlowQueries() { return slowQueries; }
    public void setSlowQueries(long slowQueries) { this.slowQueries = slowQueries; }

    public long getAverageQueryTime() { return averageQueryTime; }
    public void setAverageQueryTime(long averageQueryTime) { this.averageQueryTime = averageQueryTime; }

    public long getConnectionErrors() { return connectionErrors; }
    public void setConnectionErrors(long connectionErrors) { this.connectionErrors = connectionErrors; }

    public double getCpuUsage() { return cpuUsage; }
    public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }

    public int getActiveThreads() { return activeThreads; }
    public void setActiveThreads(int activeThreads) { this.activeThreads = activeThreads; }

    public int getQueueSize() { return queueSize; }
    public void setQueueSize(int queueSize) { this.queueSize = queueSize; }

    public int getActiveConnections() { return activeConnections; }
    public void setActiveConnections(int activeConnections) { this.activeConnections = activeConnections; }
}