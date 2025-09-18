package com.example.metaspace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;

@Configuration
@EnableScheduling
public class MemoryConfig {

    @Bean
    public MemoryPoolMXBean metaspacePool() {
        return ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getName().contains("Metaspace"))
                .findFirst()
                .orElse(null);
    }

    // Monitor Metaspace usage every 30 seconds
    @Scheduled(fixedRate = 30000)
    public void monitorMetaspace() {
        MemoryPoolMXBean metaspacePool = metaspacePool();
        if (metaspacePool != null) {
            long used = metaspacePool.getUsage().getUsed();
            long max = metaspacePool.getUsage().getMax();
            double usagePercent = max > 0 ? (double) used / max * 100 : 0;
            
            if (usagePercent > 80) {
                System.err.println("WARNING: Metaspace usage is at " + 
                    String.format("%.2f", usagePercent) + "%");
            }
        }
    }
}