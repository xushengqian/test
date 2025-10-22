import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

/**
 * 演示使用两个线程池并行执行主方法和子方法的示例
 */
public class DualThreadPoolExample {
    
    // 主线程池：用于执行主要任务
    private final ExecutorService mainThreadPool;
    
    // 子线程池：用于执行子任务
    private final ExecutorService subThreadPool;
    
    public DualThreadPoolExample() {
        // 创建主线程池，核心线程数为4
        this.mainThreadPool = new ThreadPoolExecutor(
            4,                      // 核心线程数
            8,                      // 最大线程数
            60L,                    // 空闲线程存活时间
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),  // 任务队列
            new ThreadFactory() {
                int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("主线程池-" + (++counter));
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
        );
        
        // 创建子线程池，核心线程数为8
        this.subThreadPool = new ThreadPoolExecutor(
            8,                      // 核心线程数
            16,                     // 最大线程数
            60L,                    // 空闲线程存活时间
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),  // 任务队列
            new ThreadFactory() {
                int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("子线程池-" + (++counter));
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
        );
    }
    
    /**
     * 主方法：使用主线程池执行任务
     */
    public void executeMainTasks() throws InterruptedException, ExecutionException {
        System.out.println("开始执行主任务...\n");
        
        // 创建5个主任务
        List<Future<String>> mainFutures = new ArrayList<>();
        
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            
            // 提交主任务到主线程池
            Future<String> future = mainThreadPool.submit(() -> {
                try {
                    String threadName = Thread.currentThread().getName();
                    System.out.println(String.format("[%s] 主任务%d开始执行", threadName, taskId));
                    
                    // 在主任务中调用子方法（使用子线程池）
                    List<Future<String>> subResults = executeSubTasks(taskId);
                    
                    // 等待所有子任务完成
                    StringBuilder results = new StringBuilder();
                    for (Future<String> subFuture : subResults) {
                        results.append(subFuture.get()).append("; ");
                    }
                    
                    // 模拟主任务的其他处理
                    Thread.sleep(1000);
                    
                    String result = String.format("主任务%d完成，子任务结果: [%s]", taskId, results.toString());
                    System.out.println(String.format("[%s] %s", threadName, result));
                    
                    return result;
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    return "主任务" + taskId + "失败: " + e.getMessage();
                }
            });
            
            mainFutures.add(future);
        }
        
        // 收集所有主任务的结果
        System.out.println("\n等待所有主任务完成...");
        for (Future<String> future : mainFutures) {
            System.out.println("主任务结果: " + future.get());
        }
    }
    
    /**
     * 子方法：使用子线程池执行子任务
     */
    private List<Future<String>> executeSubTasks(int mainTaskId) {
        List<Future<String>> subFutures = new ArrayList<>();
        
        // 为每个主任务创建3个子任务
        for (int i = 1; i <= 3; i++) {
            final int subTaskId = i;
            
            // 提交子任务到子线程池
            Future<String> future = subThreadPool.submit(() -> {
                try {
                    String threadName = Thread.currentThread().getName();
                    System.out.println(String.format("    [%s] 子任务%d-%d开始执行", 
                        threadName, mainTaskId, subTaskId));
                    
                    // 模拟子任务处理
                    Thread.sleep(500 + (int)(Math.random() * 1000));
                    
                    String result = String.format("子任务%d-%d完成", mainTaskId, subTaskId);
                    System.out.println(String.format("    [%s] %s", threadName, result));
                    
                    return result;
                    
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return String.format("子任务%d-%d失败", mainTaskId, subTaskId);
                }
            });
            
            subFutures.add(future);
        }
        
        return subFutures;
    }
    
    /**
     * 演示使用CompletableFuture实现更优雅的并行执行
     */
    public void executeWithCompletableFuture() {
        System.out.println("\n=== 使用CompletableFuture实现并行执行 ===\n");
        
        // 创建5个主任务的CompletableFuture
        List<CompletableFuture<String>> mainTasks = IntStream.rangeClosed(1, 5)
            .mapToObj(taskId -> 
                CompletableFuture.supplyAsync(() -> {
                    String threadName = Thread.currentThread().getName();
                    System.out.println(String.format("[%s] CompletableFuture主任务%d开始", 
                        threadName, taskId));
                    
                    // 创建子任务的CompletableFuture
                    List<CompletableFuture<String>> subTasks = IntStream.rangeClosed(1, 3)
                        .mapToObj(subId -> 
                            CompletableFuture.supplyAsync(() -> {
                                try {
                                    String subThreadName = Thread.currentThread().getName();
                                    System.out.println(String.format("    [%s] CF子任务%d-%d开始", 
                                        subThreadName, taskId, subId));
                                    
                                    Thread.sleep(500 + (int)(Math.random() * 500));
                                    
                                    return String.format("CF子任务%d-%d完成", taskId, subId);
                                } catch (InterruptedException e) {
                                    return String.format("CF子任务%d-%d失败", taskId, subId);
                                }
                            }, subThreadPool)
                        )
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
                    
                    // 等待所有子任务完成
                    CompletableFuture<Void> allSubTasks = CompletableFuture.allOf(
                        subTasks.toArray(new CompletableFuture[0])
                    );
                    
                    // 收集子任务结果
                    String subResults = allSubTasks
                        .thenApply(v -> subTasks.stream()
                            .map(CompletableFuture::join)
                            .reduce("", (a, b) -> a + b + "; "))
                        .join();
                    
                    return String.format("CF主任务%d完成，子任务: [%s]", taskId, subResults);
                }, mainThreadPool)
            )
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        // 等待所有主任务完成
        CompletableFuture<Void> allMainTasks = CompletableFuture.allOf(
            mainTasks.toArray(new CompletableFuture[0])
        );
        
        // 打印所有结果
        allMainTasks.thenRun(() -> {
            System.out.println("\n所有CompletableFuture任务完成！结果：");
            mainTasks.forEach(task -> System.out.println(task.join()));
        }).join();
    }
    
    /**
     * 关闭线程池
     */
    public void shutdown() {
        System.out.println("\n关闭线程池...");
        
        // 优雅关闭线程池
        mainThreadPool.shutdown();
        subThreadPool.shutdown();
        
        try {
            // 等待线程池终止
            if (!mainThreadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                mainThreadPool.shutdownNow();
            }
            if (!subThreadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                subThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            mainThreadPool.shutdownNow();
            subThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("线程池已关闭");
    }
    
    /**
     * 主入口
     */
    public static void main(String[] args) {
        DualThreadPoolExample example = new DualThreadPoolExample();
        
        try {
            // 方式1：使用传统的Future方式
            System.out.println("=== 使用Future实现并行执行 ===\n");
            example.executeMainTasks();
            
            // 等待一下，让输出更清晰
            Thread.sleep(2000);
            
            // 方式2：使用CompletableFuture方式
            example.executeWithCompletableFuture();
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 关闭线程池
            example.shutdown();
        }
    }
}