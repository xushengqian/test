package com.parallel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

/**
 * 演示多个主方法并行执行的类
 * 这个类可以同时执行多个不同的主方法逻辑
 */
public class ParallelMainExecutor {
    
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    
    public static void main(String[] args) {
        System.out.println("=== 并行执行多个主方法逻辑 ===");
        
        // 创建多个并行任务，每个代表一个"主方法"的逻辑
        List<CompletableFuture<Void>> mainTasks = new ArrayList<>();
        
        // 主方法1：数据处理任务
        CompletableFuture<Void> dataProcessingMain = CompletableFuture.runAsync(() -> {
            executeDataProcessingMain();
        }, executor);
        mainTasks.add(dataProcessingMain);
        
        // 主方法2：网络服务任务
        CompletableFuture<Void> networkServiceMain = CompletableFuture.runAsync(() -> {
            executeNetworkServiceMain();
        }, executor);
        mainTasks.add(networkServiceMain);
        
        // 主方法3：文件处理任务
        CompletableFuture<Void> fileProcessingMain = CompletableFuture.runAsync(() -> {
            executeFileProcessingMain();
        }, executor);
        mainTasks.add(fileProcessingMain);
        
        // 主方法4：计算任务
        CompletableFuture<Void> calculationMain = CompletableFuture.runAsync(() -> {
            executeCalculationMain();
        }, executor);
        mainTasks.add(calculationMain);
        
        // 等待所有主方法执行完成
        CompletableFuture<Void> allMainTasks = CompletableFuture.allOf(
            mainTasks.toArray(new CompletableFuture[0])
        );
        
        try {
            allMainTasks.get(30, TimeUnit.SECONDS);
            System.out.println("所有主方法并行执行完成！");
        } catch (Exception e) {
            System.err.println("执行过程中出现异常: " + e.getMessage());
        } finally {
            executor.shutdown();
        }
    }
    
    /**
     * 数据处理主方法逻辑
     */
    private static void executeDataProcessingMain() {
        System.out.println("[数据处理主方法] 开始执行 - 线程: " + Thread.currentThread().getName());
        
        try {
            // 模拟数据处理工作
            for (int i = 1; i <= 5; i++) {
                System.out.println("[数据处理主方法] 处理数据批次 " + i);
                Thread.sleep(500);
            }
            System.out.println("[数据处理主方法] 执行完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[数据处理主方法] 被中断");
        }
    }
    
    /**
     * 网络服务主方法逻辑
     */
    private static void executeNetworkServiceMain() {
        System.out.println("[网络服务主方法] 开始执行 - 线程: " + Thread.currentThread().getName());
        
        try {
            // 模拟网络服务工作
            for (int i = 1; i <= 3; i++) {
                System.out.println("[网络服务主方法] 处理网络请求 " + i);
                Thread.sleep(800);
            }
            System.out.println("[网络服务主方法] 执行完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[网络服务主方法] 被中断");
        }
    }
    
    /**
     * 文件处理主方法逻辑
     */
    private static void executeFileProcessingMain() {
        System.out.println("[文件处理主方法] 开始执行 - 线程: " + Thread.currentThread().getName());
        
        try {
            // 模拟文件处理工作
            for (int i = 1; i <= 4; i++) {
                System.out.println("[文件处理主方法] 处理文件 " + i);
                Thread.sleep(600);
            }
            System.out.println("[文件处理主方法] 执行完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[文件处理主方法] 被中断");
        }
    }
    
    /**
     * 计算任务主方法逻辑
     */
    private static void executeCalculationMain() {
        System.out.println("[计算任务主方法] 开始执行 - 线程: " + Thread.currentThread().getName());
        
        try {
            // 模拟计算工作
            long result = 0;
            for (int i = 1; i <= 1000000; i++) {
                result += i;
                if (i % 200000 == 0) {
                    System.out.println("[计算任务主方法] 计算进度: " + (i / 10000) + "%");
                    Thread.sleep(300);
                }
            }
            System.out.println("[计算任务主方法] 计算结果: " + result + " - 执行完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[计算任务主方法] 被中断");
        }
    }
}