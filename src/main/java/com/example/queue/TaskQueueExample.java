package com.example.queue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务队列使用示例
 */
public class TaskQueueExample {
    
    public static void main(String[] args) {
        // 创建任务队列（最大容量100）
        TaskQueue taskQueue = new TaskQueue(100);
        
        // 创建线程池管理器（核心线程数4，最大线程数8）
        ThreadPoolManager manager = new ThreadPoolManager(
            taskQueue, 
            4,  // 核心线程数
            8,  // 最大线程数
            60L, 
            TimeUnit.SECONDS
        );
        
        // 创建任务计数器
        AtomicInteger taskCounter = new AtomicInteger(0);
        
        // 提交多个任务
        System.out.println("开始提交任务...");
        for (int i = 0; i < 20; i++) {
            final int taskId = i;
            try {
                manager.submit(() -> {
                    int currentTask = taskCounter.incrementAndGet();
                    System.out.println(Thread.currentThread().getName() + 
                                     " 执行任务 " + taskId + 
                                     " (总任务数: " + currentTask + ")");
                    
                    // 模拟任务执行时间
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    System.out.println("任务 " + taskId + " 完成");
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // 监控队列状态
        new Thread(() -> {
            while (!manager.isShutdown()) {
                try {
                    System.out.println("\n=== 队列状态 ===");
                    System.out.println("队列中等待任务数: " + manager.getQueueSize());
                    System.out.println("活跃线程数: " + manager.getActiveThreadCount());
                    System.out.println("线程池大小: " + manager.getPoolSize());
                    System.out.println("已完成任务数: " + manager.getCompletedTaskCount());
                    System.out.println("失败任务数: " + manager.getFailedTaskCount());
                    System.out.println("================\n");
                    
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
        
        // 等待所有任务完成
        try {
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 关闭线程池
        System.out.println("正在关闭线程池...");
        try {
            manager.shutdown(10, TimeUnit.SECONDS);
            System.out.println("线程池已关闭");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            manager.shutdownNow();
        }
        
        // 打印最终统计
        System.out.println("\n=== 最终统计 ===");
        System.out.println("已完成任务数: " + manager.getCompletedTaskCount());
        System.out.println("失败任务数: " + manager.getFailedTaskCount());
    }
}
