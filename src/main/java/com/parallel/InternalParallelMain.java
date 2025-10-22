package com.parallel;

import java.util.concurrent.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * 演示主方法内部逻辑并行执行的类
 * 在单个主方法中实现多个任务的并行处理
 */
public class InternalParallelMain {
    
    private static final ExecutorService executor = Executors.newFixedThreadPool(8);
    
    public static void main(String[] args) {
        System.out.println("=== 主方法内部逻辑并行执行演示 ===");
        
        try {
            // 示例1：并行处理数组数据
            demonstrateParallelArrayProcessing();
            
            System.out.println("\n" + "=".repeat(50) + "\n");
            
            // 示例2：并行执行不同类型的任务
            demonstrateParallelTaskExecution();
            
            System.out.println("\n" + "=".repeat(50) + "\n");
            
            // 示例3：使用Fork/Join框架进行并行计算
            demonstrateForkJoinParallelism();
            
            System.out.println("\n" + "=".repeat(50) + "\n");
            
            // 示例4：流式并行处理
            demonstrateStreamParallelProcessing();
            
        } catch (Exception e) {
            System.err.println("执行过程中出现异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
        
        System.out.println("\n主方法内部并行逻辑执行完成！");
    }
    
    /**
     * 示例1：并行处理数组数据
     */
    private static void demonstrateParallelArrayProcessing() throws InterruptedException, ExecutionException {
        System.out.println("【示例1】并行处理数组数据");
        
        // 创建大数组
        int[] data = IntStream.range(1, 1001).toArray();
        int chunkSize = 250;
        
        List<CompletableFuture<Long>> futures = new ArrayList<>();
        
        // 将数组分块并行处理
        for (int i = 0; i < data.length; i += chunkSize) {
            final int start = i;
            final int end = Math.min(i + chunkSize, data.length);
            
            CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                long sum = 0;
                for (int j = start; j < end; j++) {
                    sum += data[j] * data[j]; // 计算平方和
                }
                System.out.println("线程 " + Thread.currentThread().getName() + 
                                 " 处理范围 [" + start + "-" + (end-1) + "], 结果: " + sum);
                return sum;
            }, executor);
            
            futures.add(future);
        }
        
        // 合并所有结果
        CompletableFuture<Long> totalSum = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream().mapToLong(f -> f.join()).sum());
        
        long result = totalSum.get();
        System.out.println("并行计算总和: " + result);
    }
    
    /**
     * 示例2：并行执行不同类型的任务
     */
    private static void demonstrateParallelTaskExecution() throws InterruptedException, ExecutionException {
        System.out.println("【示例2】并行执行不同类型的任务");
        
        // 任务1：数据库查询模拟
        CompletableFuture<String> dbTask = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("执行数据库查询... 线程: " + Thread.currentThread().getName());
                Thread.sleep(2000);
                return "数据库查询结果: 100条记录";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "数据库查询失败";
            }
        }, executor);
        
        // 任务2：API调用模拟
        CompletableFuture<String> apiTask = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("调用外部API... 线程: " + Thread.currentThread().getName());
                Thread.sleep(1500);
                return "API调用结果: 成功获取数据";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "API调用失败";
            }
        }, executor);
        
        // 任务3：文件处理模拟
        CompletableFuture<String> fileTask = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("处理文件... 线程: " + Thread.currentThread().getName());
                Thread.sleep(1000);
                return "文件处理结果: 处理了50个文件";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "文件处理失败";
            }
        }, executor);
        
        // 任务4：缓存更新模拟
        CompletableFuture<String> cacheTask = CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("更新缓存... 线程: " + Thread.currentThread().getName());
                Thread.sleep(800);
                return "缓存更新结果: 更新了20个缓存项";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "缓存更新失败";
            }
        }, executor);
        
        // 等待所有任务完成并收集结果
        CompletableFuture<Void> allTasks = CompletableFuture.allOf(dbTask, apiTask, fileTask, cacheTask);
        
        allTasks.thenRun(() -> {
            System.out.println("所有并行任务完成:");
            System.out.println("- " + dbTask.join());
            System.out.println("- " + apiTask.join());
            System.out.println("- " + fileTask.join());
            System.out.println("- " + cacheTask.join());
        }).get();
    }
    
    /**
     * 示例3：使用Fork/Join框架进行并行计算
     */
    private static void demonstrateForkJoinParallelism() {
        System.out.println("【示例3】Fork/Join框架并行计算");
        
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        
        try {
            // 计算大数组的和
            int[] array = IntStream.range(1, 10001).toArray();
            SumTask task = new SumTask(array, 0, array.length);
            
            long startTime = System.currentTimeMillis();
            Long result = forkJoinPool.invoke(task);
            long endTime = System.currentTimeMillis();
            
            System.out.println("Fork/Join并行计算结果: " + result);
            System.out.println("计算耗时: " + (endTime - startTime) + "ms");
            
        } finally {
            forkJoinPool.shutdown();
        }
    }
    
    /**
     * 示例4：流式并行处理
     */
    private static void demonstrateStreamParallelProcessing() {
        System.out.println("【示例4】流式并行处理");
        
        List<Integer> numbers = IntStream.range(1, 1001).boxed().toList();
        
        // 串行处理
        long startTime = System.currentTimeMillis();
        long serialSum = numbers.stream()
            .filter(n -> n % 2 == 0)
            .mapToLong(n -> (long) n * n)
            .sum();
        long serialTime = System.currentTimeMillis() - startTime;
        
        // 并行处理
        startTime = System.currentTimeMillis();
        long parallelSum = numbers.parallelStream()
            .filter(n -> n % 2 == 0)
            .mapToLong(n -> {
                // 模拟复杂计算
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return (long) n * n;
            })
            .sum();
        long parallelTime = System.currentTimeMillis() - startTime;
        
        System.out.println("串行处理结果: " + serialSum + ", 耗时: " + serialTime + "ms");
        System.out.println("并行处理结果: " + parallelSum + ", 耗时: " + parallelTime + "ms");
        System.out.println("性能提升: " + String.format("%.2f", (double) serialTime / parallelTime) + "倍");
    }
    
    /**
     * Fork/Join任务实现
     */
    static class SumTask extends RecursiveTask<Long> {
        private static final int THRESHOLD = 1000;
        private final int[] array;
        private final int start;
        private final int end;
        
        public SumTask(int[] array, int start, int end) {
            this.array = array;
            this.start = start;
            this.end = end;
        }
        
        @Override
        protected Long compute() {
            if (end - start <= THRESHOLD) {
                // 直接计算
                long sum = 0;
                for (int i = start; i < end; i++) {
                    sum += array[i];
                }
                System.out.println("线程 " + Thread.currentThread().getName() + 
                                 " 计算范围 [" + start + "-" + (end-1) + "]");
                return sum;
            } else {
                // 分割任务
                int middle = (start + end) / 2;
                SumTask leftTask = new SumTask(array, start, middle);
                SumTask rightTask = new SumTask(array, middle, end);
                
                leftTask.fork();
                Long rightResult = rightTask.compute();
                Long leftResult = leftTask.join();
                
                return leftResult + rightResult;
            }
        }
    }
}