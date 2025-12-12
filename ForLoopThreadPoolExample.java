import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Java for循环中使用线程池的示例
 * 演示了多种常见场景
 */
public class ForLoopThreadPoolExample {

    public static void main(String[] args) {
        System.out.println("=== Java for循环中使用线程池示例 ===\n");
        
        // 示例1: 基本用法 - 提交任务不等待结果
        example1_BasicUsage();
        
        // 示例2: 使用Future获取结果
        example2_WithFuture();
        
        // 示例3: 等待所有任务完成
        example3_WaitForCompletion();
        
        // 示例4: 使用CountDownLatch等待所有任务完成
        example4_WithCountDownLatch();
        
        // 示例5: 并行处理集合数据
        example5_ParallelProcessCollection();
    }

    /**
     * 示例1: 基本用法 - 在for循环中提交任务到线程池
     * 适用于不需要等待结果的场景
     */
    private static void example1_BasicUsage() {
        System.out.println("示例1: 基本用法 - 提交任务到线程池");
        
        // 创建线程池
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        // 在for循环中提交任务
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            executor.submit(() -> {
                System.out.println("任务 " + taskId + " 在线程 " + Thread.currentThread().getName() + " 中执行");
                try {
                    Thread.sleep(100); // 模拟任务执行
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        // 关闭线程池（不再接受新任务，等待已提交任务完成）
        executor.shutdown();
        try {
            // 等待所有任务完成，最多等待1分钟
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow(); // 强制关闭
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("示例1完成\n");
    }

    /**
     * 示例2: 使用Future获取任务执行结果
     * 适用于需要收集每个任务返回值的场景
     */
    private static void example2_WithFuture() {
        System.out.println("示例2: 使用Future获取结果");
        
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<Integer>> futures = new ArrayList<>();
        
        // 在for循环中提交任务并保存Future
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            Future<Integer> future = executor.submit(() -> {
                // 模拟计算任务
                int result = taskId * taskId;
                Thread.sleep(100);
                return result;
            });
            futures.add(future);
        }
        
        // 获取所有任务的结果
        for (int i = 0; i < futures.size(); i++) {
            try {
                Integer result = futures.get(i).get(); // 阻塞等待结果
                System.out.println("任务 " + i + " 的结果: " + result);
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("任务 " + i + " 执行失败: " + e.getMessage());
            }
        }
        
        executor.shutdown();
        System.out.println("示例2完成\n");
    }

    /**
     * 示例3: 等待所有任务完成后再继续
     * 使用ExecutorService的shutdown和awaitTermination
     */
    private static void example3_WaitForCompletion() {
        System.out.println("示例3: 等待所有任务完成");
        
        ExecutorService executor = Executors.newFixedThreadPool(5);
        AtomicInteger completedTasks = new AtomicInteger(0);
        
        // 提交任务
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    Thread.sleep(200);
                    int completed = completedTasks.incrementAndGet();
                    System.out.println("任务 " + taskId + " 完成 (已完成: " + completed + "/10)");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        // 关闭线程池并等待所有任务完成
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
            System.out.println("所有任务已完成！");
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("示例3完成\n");
    }

    /**
     * 示例4: 使用CountDownLatch等待所有任务完成
     * 更精确的控制，适用于需要知道确切完成时间的场景
     */
    private static void example4_WithCountDownLatch() {
        System.out.println("示例4: 使用CountDownLatch等待所有任务完成");
        
        ExecutorService executor = Executors.newFixedThreadPool(5);
        int taskCount = 10;
        CountDownLatch latch = new CountDownLatch(taskCount);
        
        long startTime = System.currentTimeMillis();
        
        // 提交任务
        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    // 模拟任务执行
                    Thread.sleep(100);
                    System.out.println("任务 " + taskId + " 完成");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown(); // 任务完成，计数器减1
                }
            });
        }
        
        try {
            latch.await(); // 等待所有任务完成
            long endTime = System.currentTimeMillis();
            System.out.println("所有任务完成！总耗时: " + (endTime - startTime) + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
        System.out.println("示例4完成\n");
    }

    /**
     * 示例5: 并行处理集合数据
     * 实际应用场景：批量处理数据
     */
    private static void example5_ParallelProcessCollection() {
        System.out.println("示例5: 并行处理集合数据");
        
        // 模拟数据集合
        List<String> dataList = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            dataList.add("数据项-" + i);
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<String>> results = new ArrayList<>();
        
        // 在for循环中处理每个数据项
        for (String data : dataList) {
            Future<String> future = executor.submit(() -> {
                // 模拟数据处理
                Thread.sleep(50);
                return "处理完成: " + data;
            });
            results.add(future);
        }
        
        // 收集处理结果
        List<String> processedResults = new ArrayList<>();
        for (Future<String> future : results) {
            try {
                processedResults.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("处理失败: " + e.getMessage());
            }
        }
        
        // 输出结果
        System.out.println("处理了 " + processedResults.size() + " 条数据");
        processedResults.forEach(System.out::println);
        
        executor.shutdown();
        System.out.println("示例5完成\n");
    }
}
