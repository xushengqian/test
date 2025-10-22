package com.example.parallel;

import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 主应用程序 - 演示各种并行执行模式
 */
public class MainApplication {

    public static void main(String[] args) {
        System.out.println("=====================================");
        System.out.println("    Java 并行执行示例程序");
        System.out.println("=====================================");
        System.out.println("\n请选择要运行的示例：");
        System.out.println("1. 基础并行执行（Thread和线程池）");
        System.out.println("2. CompletableFuture异步并行执行");
        System.out.println("3. Fork/Join框架并行执行");
        System.out.println("4. 嵌套并行执行（主方法内部继续并行）");
        System.out.println("5. 运行所有示例（并行）");
        System.out.println("0. 退出");
        
        Scanner scanner = new Scanner(System.in);
        System.out.print("\n请输入选项 (0-5): ");
        
        try {
            int choice = scanner.nextInt();
            
            switch (choice) {
                case 1:
                    runBasicParallel();
                    break;
                case 2:
                    runCompletableFuture();
                    break;
                case 3:
                    runForkJoin();
                    break;
                case 4:
                    runNestedParallel();
                    break;
                case 5:
                    runAllExamplesInParallel();
                    break;
                case 0:
                    System.out.println("退出程序");
                    System.exit(0);
                    break;
                default:
                    System.out.println("无效选项");
            }
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
        
        scanner.close();
    }
    
    /**
     * 运行基础并行执行示例
     */
    private static void runBasicParallel() {
        try {
            System.out.println("\n正在运行基础并行执行示例...\n");
            BasicParallelExecution.main(new String[]{});
        } catch (Exception e) {
            System.err.println("运行基础并行示例时出错: " + e.getMessage());
        }
    }
    
    /**
     * 运行CompletableFuture示例
     */
    private static void runCompletableFuture() {
        try {
            System.out.println("\n正在运行CompletableFuture示例...\n");
            CompletableFutureExample.main(new String[]{});
        } catch (Exception e) {
            System.err.println("运行CompletableFuture示例时出错: " + e.getMessage());
        }
    }
    
    /**
     * 运行Fork/Join框架示例
     */
    private static void runForkJoin() {
        try {
            System.out.println("\n正在运行Fork/Join框架示例...\n");
            ForkJoinExample.main(new String[]{});
        } catch (Exception e) {
            System.err.println("运行Fork/Join示例时出错: " + e.getMessage());
        }
    }
    
    /**
     * 运行嵌套并行执行示例
     */
    private static void runNestedParallel() {
        try {
            System.out.println("\n正在运行嵌套并行执行示例...\n");
            NestedParallelExample.main(new String[]{});
        } catch (Exception e) {
            System.err.println("运行嵌套并行示例时出错: " + e.getMessage());
        }
    }
    
    /**
     * 并行运行所有示例
     * 演示主方法中的并行执行
     */
    private static void runAllExamplesInParallel() {
        System.out.println("\n=====================================");
        System.out.println("    并行运行所有示例");
        System.out.println("=====================================\n");
        
        ExecutorService executor = Executors.newFixedThreadPool(4);
        
        // 创建异步任务来并行运行各个示例
        CompletableFuture<Void> basicFuture = CompletableFuture.runAsync(() -> {
            System.out.println("开始运行: 基础并行执行示例");
            try {
                BasicParallelExecution.main(new String[]{});
            } catch (Exception e) {
                System.err.println("基础示例错误: " + e.getMessage());
            }
            System.out.println("完成: 基础并行执行示例\n");
        }, executor);
        
        CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
            System.out.println("开始运行: CompletableFuture示例");
            try {
                CompletableFutureExample.main(new String[]{});
            } catch (Exception e) {
                System.err.println("CompletableFuture示例错误: " + e.getMessage());
            }
            System.out.println("完成: CompletableFuture示例\n");
        }, executor);
        
        CompletableFuture<Void> forkJoinFuture = CompletableFuture.runAsync(() -> {
            System.out.println("开始运行: Fork/Join框架示例");
            try {
                ForkJoinExample.main(new String[]{});
            } catch (Exception e) {
                System.err.println("Fork/Join示例错误: " + e.getMessage());
            }
            System.out.println("完成: Fork/Join框架示例\n");
        }, executor);
        
        CompletableFuture<Void> nestedFuture = CompletableFuture.runAsync(() -> {
            System.out.println("开始运行: 嵌套并行执行示例");
            try {
                NestedParallelExample.main(new String[]{});
            } catch (Exception e) {
                System.err.println("嵌套并行示例错误: " + e.getMessage());
            }
            System.out.println("完成: 嵌套并行执行示例\n");
        }, executor);
        
        // 等待所有示例完成
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            basicFuture, completableFuture, forkJoinFuture, nestedFuture
        );
        
        try {
            allFutures.get();
            System.out.println("\n=====================================");
            System.out.println("    所有示例执行完成！");
            System.out.println("=====================================");
        } catch (Exception e) {
            System.err.println("执行过程中出错: " + e.getMessage());
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
    }
}