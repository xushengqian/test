package com.example.queue;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * CompletableFuture 异步任务示例
 * 展示现代 Java 异步编程的最佳实践
 */
public class CompletableFutureExample {
    
    private final ExecutorService executor;
    
    public CompletableFutureExample() {
        this.executor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors()
        );
    }
    
    public CompletableFutureExample(ExecutorService executor) {
        this.executor = executor;
    }
    
    /**
     * 基本异步任务
     */
    public CompletableFuture<String> asyncTask(String input) {
        return CompletableFuture.supplyAsync(() -> {
            // 模拟耗时操作
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "处理结果: " + input;
        }, executor);
    }
    
    /**
     * 链式异步任务 - 顺序执行
     */
    public CompletableFuture<String> chainedTasks(String input) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("第一步: 获取数据");
            return "数据-" + input;
        }, executor)
        .thenApplyAsync(data -> {
            System.out.println("第二步: 处理数据");
            return "处理后-" + data;
        }, executor)
        .thenApplyAsync(result -> {
            System.out.println("第三步: 格式化结果");
            return "最终结果: " + result;
        }, executor);
    }
    
    /**
     * 并行执行多个任务，等待所有完成
     */
    public CompletableFuture<List<String>> parallelTasks(List<String> inputs) {
        List<CompletableFuture<String>> futures = inputs.stream()
                .map(this::asyncTask)
                .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }
    
    /**
     * 并行执行多个任务，返回最先完成的结果
     */
    public CompletableFuture<String> firstCompletedTask(List<String> inputs) {
        List<CompletableFuture<String>> futures = inputs.stream()
                .map(this::asyncTask)
                .collect(Collectors.toList());
        
        return CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(result -> (String) result);
    }
    
    /**
     * 组合两个独立任务的结果
     */
    public CompletableFuture<String> combineTasks(String input1, String input2) {
        CompletableFuture<String> task1 = asyncTask(input1);
        CompletableFuture<String> task2 = asyncTask(input2);
        
        return task1.thenCombineAsync(task2, (result1, result2) -> {
            return "组合结果: " + result1 + " + " + result2;
        }, executor);
    }
    
    /**
     * 带超时的异步任务
     */
    public CompletableFuture<String> asyncTaskWithTimeout(String input, long timeout, TimeUnit unit) {
        return asyncTask(input).orTimeout(timeout, unit);
    }
    
    /**
     * 带默认值的异步任务（超时时返回默认值）
     */
    public CompletableFuture<String> asyncTaskWithDefault(String input, String defaultValue, 
                                                          long timeout, TimeUnit unit) {
        return asyncTask(input)
                .completeOnTimeout(defaultValue, timeout, unit);
    }
    
    /**
     * 带异常处理的异步任务
     */
    public CompletableFuture<String> asyncTaskWithExceptionHandling(String input) {
        return CompletableFuture.supplyAsync(() -> {
            if (input == null || input.isEmpty()) {
                throw new IllegalArgumentException("输入不能为空");
            }
            return "处理结果: " + input;
        }, executor)
        .exceptionally(ex -> {
            System.err.println("任务失败: " + ex.getMessage());
            return "默认值";
        });
    }
    
    /**
     * 使用 handle 同时处理成功和失败
     */
    public CompletableFuture<String> asyncTaskWithHandle(String input) {
        return CompletableFuture.supplyAsync(() -> {
            if ("error".equals(input)) {
                throw new RuntimeException("模拟错误");
            }
            return "处理结果: " + input;
        }, executor)
        .handle((result, ex) -> {
            if (ex != null) {
                System.err.println("处理异常: " + ex.getMessage());
                return "错误处理后的默认值";
            }
            return result;
        });
    }
    
    /**
     * 关闭执行器
     */
    public void shutdown() {
        executor.shutdown();
    }
}
