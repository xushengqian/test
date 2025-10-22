package com.example.parallel;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * CompletableFuture 高级异步并行执行示例
 */
public class CompletableFutureExample {

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        System.out.println("===== CompletableFuture 异步并行执行示例 =====\n");
        
        // 1. 基础异步执行
        System.out.println("1. 基础异步执行:");
        basicAsyncExample();
        
        // 2. 组合多个异步任务
        System.out.println("\n2. 组合多个异步任务:");
        combineAsyncTasks();
        
        // 3. 处理异步任务链
        System.out.println("\n3. 处理异步任务链:");
        asyncTaskChain();
        
        // 4. 并行处理多个任务并合并结果
        System.out.println("\n4. 并行处理多个任务并合并结果:");
        parallelTasksWithAllOf();
        
        // 5. 异常处理和超时控制
        System.out.println("\n5. 异常处理和超时控制:");
        exceptionHandlingExample();
        
        // 6. 实际应用场景 - 并行获取多个服务的数据
        System.out.println("\n6. 实际应用场景 - 并行获取多个服务的数据:");
        realWorldScenario();
    }
    
    /**
     * 基础异步执行示例
     */
    private static void basicAsyncExample() throws ExecutionException, InterruptedException {
        // 创建异步任务
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            System.out.println("异步任务执行中... 线程: " + Thread.currentThread().getName());
            sleep(1000);
            return "异步任务完成!";
        });
        
        // 主线程可以继续做其他事情
        System.out.println("主线程继续执行其他任务...");
        
        // 获取异步任务结果
        String result = future.get();
        System.out.println("异步任务结果: " + result);
    }
    
    /**
     * 组合多个异步任务
     */
    private static void combineAsyncTasks() throws ExecutionException, InterruptedException {
        CompletableFuture<Integer> future1 = CompletableFuture.supplyAsync(() -> {
            System.out.println("计算任务1...");
            sleep(500);
            return 10;
        });
        
        CompletableFuture<Integer> future2 = CompletableFuture.supplyAsync(() -> {
            System.out.println("计算任务2...");
            sleep(500);
            return 20;
        });
        
        // 组合两个异步任务的结果
        CompletableFuture<Integer> combinedFuture = future1.thenCombine(future2, (result1, result2) -> {
            System.out.println("组合结果: " + result1 + " + " + result2);
            return result1 + result2;
        });
        
        System.out.println("最终结果: " + combinedFuture.get());
    }
    
    /**
     * 处理异步任务链
     */
    private static void asyncTaskChain() throws ExecutionException, InterruptedException {
        CompletableFuture<String> chainedFuture = CompletableFuture
            .supplyAsync(() -> {
                System.out.println("步骤1: 获取用户ID");
                sleep(300);
                return "user123";
            })
            .thenCompose(userId -> CompletableFuture.supplyAsync(() -> {
                System.out.println("步骤2: 根据用户ID获取用户信息: " + userId);
                sleep(300);
                return "用户信息: " + userId;
            }))
            .thenApplyAsync(userInfo -> {
                System.out.println("步骤3: 处理用户信息");
                sleep(300);
                return userInfo + " [已处理]";
            });
        
        System.out.println("任务链结果: " + chainedFuture.get());
    }
    
    /**
     * 并行处理多个任务并合并结果
     */
    private static void parallelTasksWithAllOf() throws ExecutionException, InterruptedException {
        List<String> websites = Arrays.asList(
            "网站A", "网站B", "网站C", "网站D", "网站E"
        );
        
        // 为每个网站创建异步任务
        List<CompletableFuture<String>> futures = websites.stream()
            .map(site -> CompletableFuture.supplyAsync(() -> {
                System.out.println("正在获取 " + site + " 的数据... 线程: " + 
                    Thread.currentThread().getName());
                sleep((long) (Math.random() * 1000)); // 模拟不同的响应时间
                return site + " 的数据";
            }))
            .collect(Collectors.toList());
        
        // 等待所有任务完成
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        // 获取所有结果
        CompletableFuture<List<String>> allResults = allFutures.thenApply(v ->
            futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList())
        );
        
        List<String> results = allResults.get();
        System.out.println("所有结果: " + results);
    }
    
    /**
     * 异常处理和超时控制
     */
    private static void exceptionHandlingExample() {
        // 带异常处理的异步任务
        CompletableFuture<String> futureWithException = CompletableFuture
            .supplyAsync(() -> {
                if (Math.random() > 0.5) {
                    throw new RuntimeException("模拟异常!");
                }
                return "成功结果";
            })
            .exceptionally(ex -> {
                System.err.println("捕获异常: " + ex.getMessage());
                return "默认值";
            });
        
        // 带超时控制的异步任务
        CompletableFuture<String> futureWithTimeout = CompletableFuture
            .supplyAsync(() -> {
                sleep(2000); // 模拟长时间运行
                return "正常结果";
            })
            .completeOnTimeout("超时默认值", 1, TimeUnit.SECONDS);
        
        try {
            System.out.println("异常处理结果: " + futureWithException.get());
            System.out.println("超时控制结果: " + futureWithTimeout.get());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 实际应用场景 - 并行获取多个服务的数据
     */
    private static void realWorldScenario() throws ExecutionException, InterruptedException {
        long startTime = System.currentTimeMillis();
        
        // 模拟并行调用多个微服务
        CompletableFuture<UserInfo> userFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("获取用户信息...");
            sleep(800);
            return new UserInfo("张三", 25);
        });
        
        CompletableFuture<List<Order>> ordersFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("获取订单列表...");
            sleep(1000);
            return Arrays.asList(
                new Order("订单1", 100.0),
                new Order("订单2", 200.0)
            );
        });
        
        CompletableFuture<AccountBalance> balanceFuture = CompletableFuture.supplyAsync(() -> {
            System.out.println("获取账户余额...");
            sleep(600);
            return new AccountBalance(5000.0);
        });
        
        // 组合所有结果
        CompletableFuture<UserDashboard> dashboardFuture = CompletableFuture
            .allOf(userFuture, ordersFuture, balanceFuture)
            .thenApply(v -> {
                UserInfo user = userFuture.join();
                List<Order> orders = ordersFuture.join();
                AccountBalance balance = balanceFuture.join();
                return new UserDashboard(user, orders, balance);
            });
        
        UserDashboard dashboard = dashboardFuture.get();
        long endTime = System.currentTimeMillis();
        
        System.out.println("\n用户仪表板数据:");
        System.out.println("用户: " + dashboard.userInfo);
        System.out.println("订单: " + dashboard.orders);
        System.out.println("余额: " + dashboard.balance);
        System.out.println("总耗时: " + (endTime - startTime) + "ms (并行执行，而非串行的2400ms)");
    }
    
    /**
     * 辅助睡眠方法
     */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    // 数据模型类
    static class UserInfo {
        String name;
        int age;
        
        UserInfo(String name, int age) {
            this.name = name;
            this.age = age;
        }
        
        @Override
        public String toString() {
            return "UserInfo{name='" + name + "', age=" + age + "}";
        }
    }
    
    static class Order {
        String id;
        double amount;
        
        Order(String id, double amount) {
            this.id = id;
            this.amount = amount;
        }
        
        @Override
        public String toString() {
            return "Order{id='" + id + "', amount=" + amount + "}";
        }
    }
    
    static class AccountBalance {
        double balance;
        
        AccountBalance(double balance) {
            this.balance = balance;
        }
        
        @Override
        public String toString() {
            return "AccountBalance{balance=" + balance + "}";
        }
    }
    
    static class UserDashboard {
        UserInfo userInfo;
        List<Order> orders;
        AccountBalance balance;
        
        UserDashboard(UserInfo userInfo, List<Order> orders, AccountBalance balance) {
            this.userInfo = userInfo;
            this.orders = orders;
            this.balance = balance;
        }
    }
}