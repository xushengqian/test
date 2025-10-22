package com.example.parallel;

import java.util.concurrent.*;
import java.util.stream.IntStream;

/**
 * 基础并行执行示例 - 使用多线程和线程池
 */
public class BasicParallelExecution {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        System.out.println("===== 基础并行执行示例 =====\n");
        
        // 1. 使用Thread直接创建线程并行执行
        System.out.println("1. 使用Thread直接创建线程:");
        threadExample();
        
        // 2. 使用ExecutorService线程池并行执行
        System.out.println("\n2. 使用ExecutorService线程池:");
        executorServiceExample();
        
        // 3. 使用FixedThreadPool并行执行多个任务
        System.out.println("\n3. 使用FixedThreadPool并行执行多个任务:");
        fixedThreadPoolExample();
        
        // 4. 使用Callable和Future获取并行任务结果
        System.out.println("\n4. 使用Callable和Future获取并行任务结果:");
        callableFutureExample();
    }
    
    /**
     * 使用Thread直接创建线程并行执行
     */
    private static void threadExample() throws InterruptedException {
        Thread thread1 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                System.out.println(Thread.currentThread().getName() + " - 任务1执行: " + i);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        
        Thread thread2 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                System.out.println(Thread.currentThread().getName() + " - 任务2执行: " + i);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        
        // 启动线程并行执行
        thread1.start();
        thread2.start();
        
        // 等待线程完成
        thread1.join();
        thread2.join();
    }
    
    /**
     * 使用ExecutorService线程池并行执行
     */
    private static void executorServiceExample() throws InterruptedException {
        ExecutorService executor = Executors.newCachedThreadPool();
        
        // 提交多个任务并行执行
        executor.submit(() -> performTask("任务A", 3));
        executor.submit(() -> performTask("任务B", 3));
        executor.submit(() -> performTask("任务C", 3));
        
        // 关闭线程池并等待任务完成
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
    
    /**
     * 使用FixedThreadPool并行执行多个任务
     */
    private static void fixedThreadPoolExample() throws InterruptedException {
        // 创建固定大小的线程池
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        // 提交10个任务，但只有3个线程并行执行
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            executor.submit(() -> {
                System.out.println(Thread.currentThread().getName() + 
                    " 执行任务 " + taskId);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
    }
    
    /**
     * 使用Callable和Future获取并行任务结果
     */
    private static void callableFutureExample() throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        // 创建Callable任务
        Callable<Integer> task1 = () -> {
            System.out.println("计算任务1开始...");
            Thread.sleep(1000);
            return IntStream.rangeClosed(1, 100).sum();
        };
        
        Callable<Integer> task2 = () -> {
            System.out.println("计算任务2开始...");
            Thread.sleep(1000);
            return IntStream.rangeClosed(101, 200).sum();
        };
        
        Callable<Integer> task3 = () -> {
            System.out.println("计算任务3开始...");
            Thread.sleep(1000);
            return IntStream.rangeClosed(201, 300).sum();
        };
        
        // 提交任务并获取Future对象
        Future<Integer> future1 = executor.submit(task1);
        Future<Integer> future2 = executor.submit(task2);
        Future<Integer> future3 = executor.submit(task3);
        
        // 获取并行计算的结果
        int result1 = future1.get();
        int result2 = future2.get();
        int result3 = future3.get();
        
        System.out.println("任务1结果: " + result1);
        System.out.println("任务2结果: " + result2);
        System.out.println("任务3结果: " + result3);
        System.out.println("总和: " + (result1 + result2 + result3));
        
        executor.shutdown();
    }
    
    /**
     * 执行任务的辅助方法
     */
    private static void performTask(String taskName, int iterations) {
        for (int i = 0; i < iterations; i++) {
            System.out.println(Thread.currentThread().getName() + 
                " - " + taskName + " 执行步骤 " + (i + 1));
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}