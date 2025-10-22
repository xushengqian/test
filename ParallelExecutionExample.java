import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Java主方法和子方法使用两个线程池并行执行的示例
 * 
 * 这个示例展示了如何在主方法和子方法中分别使用不同的线程池来并行执行任务
 */
public class ParallelExecutionExample {
    
    // 主方法使用的线程池 - 用于处理主要业务逻辑
    private static final ExecutorService mainThreadPool = Executors.newFixedThreadPool(4, 
        new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "MainPool-Thread-" + counter.getAndIncrement());
                t.setDaemon(false);
                return t;
            }
        });
    
    // 子方法使用的线程池 - 用于处理辅助任务
    private static final ExecutorService subThreadPool = Executors.newFixedThreadPool(3,
        new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "SubPool-Thread-" + counter.getAndIncrement());
                t.setDaemon(false);
                return t;
            }
        });
    
    public static void main(String[] args) {
        System.out.println("=== Java双线程池并行执行示例 ===");
        System.out.println("主线程: " + Thread.currentThread().getName());
        
        try {
            // 在主方法中使用主线程池执行任务
            List<Future<String>> mainResults = executeMainTasks();
            
            // 同时调用子方法，使用子线程池执行任务
            List<Future<Integer>> subResults = executeSubTasks();
            
            // 收集主线程池的结果
            System.out.println("\n=== 主线程池执行结果 ===");
            for (int i = 0; i < mainResults.size(); i++) {
                try {
                    String result = mainResults.get(i).get(5, TimeUnit.SECONDS);
                    System.out.println("主任务 " + (i + 1) + " 结果: " + result);
                } catch (TimeoutException e) {
                    System.out.println("主任务 " + (i + 1) + " 超时");
                }
            }
            
            // 收集子线程池的结果
            System.out.println("\n=== 子线程池执行结果 ===");
            for (int i = 0; i < subResults.size(); i++) {
                try {
                    Integer result = subResults.get(i).get(5, TimeUnit.SECONDS);
                    System.out.println("子任务 " + (i + 1) + " 结果: " + result);
                } catch (TimeoutException e) {
                    System.out.println("子任务 " + (i + 1) + " 超时");
                }
            }
            
            // 演示任务间的协调
            demonstrateTaskCoordination();
            
        } catch (Exception e) {
            System.err.println("执行过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 关闭线程池
            shutdownThreadPools();
        }
    }
    
    /**
     * 在主方法中使用主线程池执行任务
     */
    private static List<Future<String>> executeMainTasks() {
        System.out.println("\n--- 主线程池开始执行任务 ---");
        List<Future<String>> futures = new ArrayList<>();
        
        // 提交多个主任务到主线程池
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            Future<String> future = mainThreadPool.submit(() -> {
                String threadName = Thread.currentThread().getName();
                System.out.println("主任务 " + taskId + " 开始执行，线程: " + threadName);
                
                try {
                    // 模拟耗时操作
                    Thread.sleep(1000 + (taskId * 200));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "主任务 " + taskId + " 被中断";
                }
                
                String result = "主任务 " + taskId + " 完成 (线程: " + threadName + ")";
                System.out.println(result);
                return result;
            });
            futures.add(future);
        }
        
        return futures;
    }
    
    /**
     * 子方法：使用子线程池执行任务
     */
    private static List<Future<Integer>> executeSubTasks() {
        System.out.println("\n--- 子线程池开始执行任务 ---");
        List<Future<Integer>> futures = new ArrayList<>();
        
        // 提交多个子任务到子线程池
        List<Integer> numbers = Arrays.asList(10, 20, 30, 40, 50);
        
        for (int i = 0; i < numbers.size(); i++) {
            final int taskId = i + 1;
            final int number = numbers.get(i);
            
            Future<Integer> future = subThreadPool.submit(() -> {
                String threadName = Thread.currentThread().getName();
                System.out.println("子任务 " + taskId + " 开始处理数字 " + number + "，线程: " + threadName);
                
                try {
                    // 模拟计算密集型操作
                    Thread.sleep(800 + (taskId * 150));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
                
                int result = number * number; // 计算平方
                System.out.println("子任务 " + taskId + " 计算完成: " + number + "² = " + result + " (线程: " + threadName + ")");
                return result;
            });
            futures.add(future);
        }
        
        return futures;
    }
    
    /**
     * 演示任务间的协调 - 主线程池和子线程池协同工作
     */
    private static void demonstrateTaskCoordination() {
        System.out.println("\n=== 演示任务协调 ===");
        
        // 使用CountDownLatch来协调两个线程池的任务
        CountDownLatch latch = new CountDownLatch(2);
        
        // 主线程池执行协调任务
        Future<String> mainCoordTask = mainThreadPool.submit(() -> {
            try {
                String threadName = Thread.currentThread().getName();
                System.out.println("主线程池协调任务开始 (线程: " + threadName + ")");
                Thread.sleep(1500);
                System.out.println("主线程池协调任务完成 (线程: " + threadName + ")");
                return "主线程池协调完成";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "主线程池协调被中断";
            } finally {
                latch.countDown();
            }
        });
        
        // 子线程池执行协调任务
        Future<String> subCoordTask = subThreadPool.submit(() -> {
            try {
                String threadName = Thread.currentThread().getName();
                System.out.println("子线程池协调任务开始 (线程: " + threadName + ")");
                Thread.sleep(1200);
                System.out.println("子线程池协调任务完成 (线程: " + threadName + ")");
                return "子线程池协调完成";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "子线程池协调被中断";
            } finally {
                latch.countDown();
            }
        });
        
        try {
            // 等待两个协调任务都完成
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            if (completed) {
                System.out.println("协调任务结果:");
                System.out.println("- " + mainCoordTask.get());
                System.out.println("- " + subCoordTask.get());
                System.out.println("所有协调任务完成！");
            } else {
                System.out.println("协调任务超时！");
            }
        } catch (Exception e) {
            System.err.println("协调任务执行出错: " + e.getMessage());
        }
    }
    
    /**
     * 关闭线程池
     */
    private static void shutdownThreadPools() {
        System.out.println("\n=== 关闭线程池 ===");
        
        // 关闭主线程池
        mainThreadPool.shutdown();
        try {
            if (!mainThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                System.out.println("主线程池强制关闭");
                mainThreadPool.shutdownNow();
            } else {
                System.out.println("主线程池正常关闭");
            }
        } catch (InterruptedException e) {
            mainThreadPool.shutdownNow();
        }
        
        // 关闭子线程池
        subThreadPool.shutdown();
        try {
            if (!subThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                System.out.println("子线程池强制关闭");
                subThreadPool.shutdownNow();
            } else {
                System.out.println("子线程池正常关闭");
            }
        } catch (InterruptedException e) {
            subThreadPool.shutdownNow();
        }
        
        System.out.println("所有线程池已关闭");
    }
}