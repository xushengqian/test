package com.example.metaspace.service;

import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MemoryService {

    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final List<MemoryPoolMXBean> memoryPools = ManagementFactory.getMemoryPoolMXBeans();

    public Map<String, Object> getMemoryInfo() {
        Map<String, Object> info = new HashMap<>();
        
        // Heap memory info
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        long heapCommitted = memoryBean.getHeapMemoryUsage().getCommitted();
        
        info.put("heapUsed", formatBytes(heapUsed));
        info.put("heapMax", formatBytes(heapMax));
        info.put("heapCommitted", formatBytes(heapCommitted));
        info.put("heapUsagePercent", (double) heapUsed / heapMax * 100);
        
        // Non-heap memory info
        long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed();
        long nonHeapMax = memoryBean.getNonHeapMemoryUsage().getMax();
        long nonHeapCommitted = memoryBean.getNonHeapMemoryUsage().getCommitted();
        
        info.put("nonHeapUsed", formatBytes(nonHeapUsed));
        info.put("nonHeapMax", formatBytes(nonHeapMax));
        info.put("nonHeapCommitted", formatBytes(nonHeapCommitted));
        
        return info;
    }

    public Map<String, Object> getMetaspaceInfo() {
        Map<String, Object> info = new HashMap<>();
        
        for (MemoryPoolMXBean pool : memoryPools) {
            if (pool.getName().contains("Metaspace")) {
                long used = pool.getUsage().getUsed();
                long max = pool.getUsage().getMax();
                long committed = pool.getUsage().getCommitted();
                
                info.put("name", pool.getName());
                info.put("used", formatBytes(used));
                info.put("max", formatBytes(max));
                info.put("committed", formatBytes(committed));
                info.put("usagePercent", max > 0 ? (double) used / max * 100 : 0);
                info.put("isUsageThresholdExceeded", pool.isUsageThresholdExceeded());
                info.put("isCollectionUsageThresholdExceeded", pool.isCollectionUsageThresholdExceeded());
            }
        }
        
        return info;
    }

    public String triggerMetaspaceOOM() {
        try {
            // This is a dangerous method that can cause OOM
            // Only use for demonstration purposes
            return "WARNING: This endpoint can cause OutOfMemoryError. Use with caution!";
        } catch (OutOfMemoryError e) {
            return "OutOfMemoryError occurred: " + e.getMessage();
        }
    }

    public String forceGarbageCollection() {
        System.gc();
        return "Garbage collection triggered";
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}