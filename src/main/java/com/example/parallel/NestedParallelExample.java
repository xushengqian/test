package com.example.parallel;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 嵌套并行执行示例 - 主方法内部逻辑继续并行执行
 */
public class NestedParallelExample {

    private static final ExecutorService mainExecutor = Executors.newFixedThreadPool(3);
    private static final ExecutorService subExecutor = Executors.newFixedThreadPool(10);

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        System.out.println("===== 嵌套并行执行示例 =====\n");
        System.out.println("主线程: " + Thread.currentThread().getName() + "\n");
        
        // 1. 两层并行执行
        System.out.println("1. 两层并行执行示例:");
        twoLevelParallelExecution();
        
        // 2. 并行任务中的并行流处理
        System.out.println("\n2. 并行任务中的并行流处理:");
        parallelStreamInParallelTask();
        
        // 3. 复杂的嵌套并行场景
        System.out.println("\n3. 复杂的嵌套并行场景:");
        complexNestedParallel();
        
        // 4. 实际应用 - 并行处理多个数据源
        System.out.println("\n4. 实际应用 - 并行处理多个数据源:");
        realWorldNestedParallel();
        
        // 关闭线程池
        mainExecutor.shutdown();
        subExecutor.shutdown();
        mainExecutor.awaitTermination(5, TimeUnit.SECONDS);
        subExecutor.awaitTermination(5, TimeUnit.SECONDS);
    }
    
    /**
     * 两层并行执行
     */
    private static void twoLevelParallelExecution() throws ExecutionException, InterruptedException {
        // 第一层：主方法中并行执行3个大任务
        List<Future<String>> mainFutures = new ArrayList<>();
        
        for (int i = 1; i <= 3; i++) {
            final int taskId = i;
            Future<String> future = mainExecutor.submit(() -> {
                System.out.println("主任务 " + taskId + " 开始 - 线程: " + 
                    Thread.currentThread().getName());
                
                // 第二层：每个主任务内部并行执行子任务
                List<Future<Integer>> subFutures = new ArrayList<>();
                for (int j = 1; j <= 3; j++) {
                    final int subTaskId = j;
                    Future<Integer> subFuture = subExecutor.submit(() -> {
                        System.out.println("  子任务 " + taskId + "-" + subTaskId + 
                            " 执行中 - 线程: " + Thread.currentThread().getName());
                        sleep(500);
                        return taskId * 10 + subTaskId;
                    });
                    subFutures.add(subFuture);
                }
                
                // 收集子任务结果
                int sum = 0;
                for (Future<Integer> subFuture : subFutures) {
                    try {
                        sum += subFuture.get();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                
                return "主任务 " + taskId + " 完成，子任务总和: " + sum;
            });
            mainFutures.add(future);
        }
        
        // 收集主任务结果
        for (Future<String> future : mainFutures) {
            System.out.println(future.get());
        }
    }
    
    /**
     * 并行任务中的并行流处理
     */
    private static void parallelStreamInParallelTask() throws ExecutionException, InterruptedException {
        List<CompletableFuture<List<Integer>>> futures = new ArrayList<>();
        
        // 创建多个并行任务，每个任务内部使用并行流
        for (int i = 1; i <= 3; i++) {
            final int batchId = i;
            CompletableFuture<List<Integer>> future = CompletableFuture.supplyAsync(() -> {
                System.out.println("批次 " + batchId + " 开始处理");
                
                // 在并行任务内部使用并行流
                List<Integer> result = IntStream.rangeClosed(1, 1000)
                    .parallel()
                    .filter(n -> n % 2 == 0)
                    .map(n -> {
                        // 模拟复杂计算
                        return n * batchId;
                    })
                    .boxed()
                    .collect(Collectors.toList());
                
                System.out.println("批次 " + batchId + " 处理完成，结果数量: " + result.size());
                return result;
            }, mainExecutor);
            
            futures.add(future);
        }
        
        // 等待所有批次完成并合并结果
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        allOf.thenRun(() -> {
            int totalCount = futures.stream()
                .map(CompletableFuture::join)
                .mapToInt(List::size)
                .sum();
            System.out.println("所有批次处理完成，总结果数: " + totalCount);
        }).get();
    }
    
    /**
     * 复杂的嵌套并行场景
     */
    private static void complexNestedParallel() throws ExecutionException, InterruptedException {
        // 模拟多个部门的数据处理
        List<Department> departments = Arrays.asList(
            new Department("技术部", 100),
            new Department("销售部", 80),
            new Department("市场部", 60)
        );
        
        // 第一层并行：处理各个部门
        List<CompletableFuture<DepartmentResult>> departmentFutures = departments.stream()
            .map(dept -> CompletableFuture.supplyAsync(() -> {
                System.out.println("处理部门: " + dept.name);
                
                // 第二层并行：处理部门内的员工
                List<CompletableFuture<EmployeeResult>> employeeFutures = 
                    IntStream.rangeClosed(1, dept.employeeCount)
                        .mapToObj(empId -> CompletableFuture.supplyAsync(() -> {
                            // 第三层并行：处理员工的多项任务
                            CompletableFuture<Double> salaryFuture = 
                                CompletableFuture.supplyAsync(() -> calculateSalary(empId));
                            CompletableFuture<Double> bonusFuture = 
                                CompletableFuture.supplyAsync(() -> calculateBonus(empId));
                            CompletableFuture<Double> taxFuture = 
                                CompletableFuture.supplyAsync(() -> calculateTax(empId));
                            
                            try {
                                double salary = salaryFuture.get();
                                double bonus = bonusFuture.get();
                                double tax = taxFuture.get();
                                
                                return new EmployeeResult(empId, salary + bonus - tax);
                            } catch (Exception e) {
                                return new EmployeeResult(empId, 0);
                            }
                        }, subExecutor))
                        .collect(Collectors.toList());
                
                // 汇总部门结果
                List<EmployeeResult> employeeResults = employeeFutures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
                
                double totalCompensation = employeeResults.stream()
                    .mapToDouble(e -> e.totalCompensation)
                    .sum();
                
                return new DepartmentResult(dept.name, totalCompensation);
            }, mainExecutor))
            .collect(Collectors.toList());
        
        // 等待所有部门处理完成
        CompletableFuture.allOf(departmentFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                System.out.println("\n部门处理结果:");
                departmentFutures.forEach(future -> {
                    DepartmentResult result = future.join();
                    System.out.println(result.departmentName + ": 总薪酬 = " + 
                        String.format("%.2f", result.totalCompensation));
                });
            }).get();
    }
    
    /**
     * 实际应用 - 并行处理多个数据源
     */
    private static void realWorldNestedParallel() throws ExecutionException, InterruptedException {
        // 模拟从多个数据源获取数据并处理
        List<String> dataSources = Arrays.asList("数据库A", "数据库B", "API服务C");
        
        Map<String, CompletableFuture<ProcessedData>> dataFutures = new HashMap<>();
        
        // 并行从各个数据源获取数据
        for (String source : dataSources) {
            CompletableFuture<ProcessedData> future = CompletableFuture
                .supplyAsync(() -> {
                    System.out.println("从 " + source + " 获取数据...");
                    sleep(500);
                    // 模拟获取原始数据
                    List<Integer> rawData = IntStream.rangeClosed(1, 100)
                        .boxed()
                        .collect(Collectors.toList());
                    return rawData;
                })
                .thenCompose(rawData -> {
                    // 并行处理获取到的数据
                    return CompletableFuture.supplyAsync(() -> {
                        System.out.println("并行处理 " + source + " 的数据...");
                        
                        // 使用并行流进行数据转换
                        List<Integer> processed = rawData.parallelStream()
                            .map(n -> n * 2)
                            .filter(n -> n > 50)
                            .collect(Collectors.toList());
                        
                        // 并行执行多个聚合操作
                        CompletableFuture<Integer> sumFuture = CompletableFuture
                            .supplyAsync(() -> processed.stream().mapToInt(Integer::intValue).sum());
                        CompletableFuture<Double> avgFuture = CompletableFuture
                            .supplyAsync(() -> processed.stream().mapToInt(Integer::intValue).average().orElse(0));
                        CompletableFuture<Integer> maxFuture = CompletableFuture
                            .supplyAsync(() -> processed.stream().mapToInt(Integer::intValue).max().orElse(0));
                        
                        try {
                            return new ProcessedData(
                                source,
                                processed.size(),
                                sumFuture.get(),
                                avgFuture.get(),
                                maxFuture.get()
                            );
                        } catch (Exception e) {
                            return new ProcessedData(source, 0, 0, 0, 0);
                        }
                    });
                });
            
            dataFutures.put(source, future);
        }
        
        // 等待所有数据处理完成
        CompletableFuture<Void> allDataProcessed = CompletableFuture.allOf(
            dataFutures.values().toArray(new CompletableFuture[0])
        );
        
        allDataProcessed.thenRun(() -> {
            System.out.println("\n数据处理结果汇总:");
            dataFutures.forEach((source, future) -> {
                ProcessedData data = future.join();
                System.out.println(String.format("%s - 数量: %d, 总和: %d, 平均: %.2f, 最大: %d",
                    data.source, data.count, data.sum, data.average, data.max));
            });
        }).get();
    }
    
    // 辅助方法
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private static double calculateSalary(int empId) {
        sleep(10);
        return 5000 + empId * 100;
    }
    
    private static double calculateBonus(int empId) {
        sleep(10);
        return empId * 50;
    }
    
    private static double calculateTax(int empId) {
        sleep(10);
        return (5000 + empId * 100) * 0.2;
    }
    
    // 数据类
    static class Department {
        String name;
        int employeeCount;
        
        Department(String name, int employeeCount) {
            this.name = name;
            this.employeeCount = employeeCount;
        }
    }
    
    static class DepartmentResult {
        String departmentName;
        double totalCompensation;
        
        DepartmentResult(String departmentName, double totalCompensation) {
            this.departmentName = departmentName;
            this.totalCompensation = totalCompensation;
        }
    }
    
    static class EmployeeResult {
        int employeeId;
        double totalCompensation;
        
        EmployeeResult(int employeeId, double totalCompensation) {
            this.employeeId = employeeId;
            this.totalCompensation = totalCompensation;
        }
    }
    
    static class ProcessedData {
        String source;
        int count;
        int sum;
        double average;
        int max;
        
        ProcessedData(String source, int count, int sum, double average, int max) {
            this.source = source;
            this.count = count;
            this.sum = sum;
            this.average = average;
            this.max = max;
        }
    }
}