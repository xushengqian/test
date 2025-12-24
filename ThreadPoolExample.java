import java.util.concurrent.*;

/**
 * 线程池线程数设置示例
 * 
 * 线程池大小的设置原则：
 * 1. CPU密集型任务：线程数 = CPU核心数 + 1
 * 2. IO密集型任务：线程数 = CPU核心数 * (1 + IO等待时间/CPU计算时间)
 * 3. 混合型任务：需要根据实际情况调整
 */
public class ThreadPoolExample {
    
    // 获取CPU核心数
    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();
    
    /**
     * CPU密集型任务示例
     * 适合计算、加密、压缩等CPU占用高的任务
     */
    public static void cpuIntensiveExample() {
        // CPU密集型：线程数 = CPU核心数 + 1
        int threadPoolSize = CPU_CORES + 1;
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            threadPoolSize,           // 核心线程数
            threadPoolSize,           // 最大线程数
            60L,                      // 空闲线程存活时间
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),  // 工作队列
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "CPU-Task-" + (++counter));
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
        );
        
        System.out.println("CPU密集型线程池大小: " + threadPoolSize);
        System.out.println("CPU核心数: " + CPU_CORES);
        
        // 提交CPU密集型任务
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            executor.submit(() -> {
                // CPU密集型计算
                long sum = 0;
                for (int j = 0; j < 1000000; j++) {
                    sum += j * j;
                }
                System.out.println("任务 " + taskId + " 完成，结果: " + sum);
            });
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * IO密集型任务示例
     * 适合网络请求、文件读写、数据库查询等IO操作
     */
    public static void ioIntensiveExample() {
        // IO密集型：线程数 = CPU核心数 * (1 + IO等待时间/CPU计算时间)
        // 通常IO等待时间远大于CPU计算时间，可以设置为 CPU核心数 * 2 到 CPU核心数 * 4
        int threadPoolSize = CPU_CORES * 2;
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            threadPoolSize,           // 核心线程数
            threadPoolSize * 2,       // 最大线程数（IO密集型可以更大）
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "IO-Task-" + (++counter));
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        System.out.println("IO密集型线程池大小: " + threadPoolSize);
        System.out.println("CPU核心数: " + CPU_CORES);
        
        // 提交IO密集型任务
        for (int i = 0; i < 20; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    // 模拟IO操作（网络请求、文件读写等）
                    Thread.sleep(1000);  // 模拟IO等待
                    System.out.println("IO任务 " + taskId + " 完成");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 使用Executors工具类创建线程池（不推荐，但简单）
     * 注意：Executors.newFixedThreadPool() 创建的线程池队列是无界的，可能导致OOM
     */
    public static void simpleExample() {
        // 固定大小线程池
        ExecutorService fixedPool = Executors.newFixedThreadPool(CPU_CORES);
        
        // 缓存线程池（适合大量短时任务）
        ExecutorService cachedPool = Executors.newCachedThreadPool();
        
        // 单线程池（保证任务顺序执行）
        ExecutorService singlePool = Executors.newSingleThreadExecutor();
        
        // 定时任务线程池
        ScheduledExecutorService scheduledPool = Executors.newScheduledThreadPool(CPU_CORES);
        
        // 使用示例
        fixedPool.submit(() -> System.out.println("固定线程池任务"));
        
        fixedPool.shutdown();
        cachedPool.shutdown();
        singlePool.shutdown();
        scheduledPool.shutdown();
    }
    
    /**
     * 动态调整线程池大小示例
     */
    public static void dynamicSizeExample() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2,                        // 最小核心线程数
            10,                       // 最大线程数
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(50),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // 根据系统负载动态调整
        // 监控队列长度和活跃线程数
        executor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                int queueSize = executor.getQueue().size();
                int activeCount = executor.getActiveCount();
                int poolSize = executor.getPoolSize();
                
                System.out.printf("队列大小: %d, 活跃线程: %d, 线程池大小: %d%n", 
                    queueSize, activeCount, poolSize);
                
                // 如果队列积压严重，可以考虑增加线程数
                if (queueSize > 40 && poolSize < executor.getMaximumPoolSize()) {
                    executor.setCorePoolSize(Math.min(poolSize + 2, executor.getMaximumPoolSize()));
                }
                
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        
        executor.shutdown();
    }
    
    public static void main(String[] args) {
        System.out.println("=== 线程池线程数设置示例 ===\n");
        
        System.out.println("1. CPU密集型任务示例:");
        cpuIntensiveExample();
        
        System.out.println("\n2. IO密集型任务示例:");
        ioIntensiveExample();
        
        System.out.println("\n3. 简单示例:");
        simpleExample();
        
        System.out.println("\n4. 动态调整示例:");
        dynamicSizeExample();
    }
}
