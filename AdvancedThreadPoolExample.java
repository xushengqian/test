import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 高级示例：展示主方法和子方法使用两个线程池并行执行，包含数据传递和任务协调
 */
public class AdvancedThreadPoolExample {
    
    // 主线程池配置
    private static final int MAIN_CORE_THREADS = 4;
    private static final int MAIN_MAX_THREADS = 8;
    
    // 子线程池配置
    private static final int SUB_CORE_THREADS = 8;
    private static final int SUB_MAX_THREADS = 16;
    
    // 两个线程池
    private final ThreadPoolExecutor mainThreadPool;
    private final ThreadPoolExecutor subThreadPool;
    
    // 统计信息
    private final AtomicInteger mainTaskCounter = new AtomicInteger(0);
    private final AtomicInteger subTaskCounter = new AtomicInteger(0);
    
    public AdvancedThreadPoolExample() {
        // 创建主线程池
        this.mainThreadPool = createThreadPool("主线程池", 
            MAIN_CORE_THREADS, MAIN_MAX_THREADS, 100);
        
        // 创建子线程池
        this.subThreadPool = createThreadPool("子线程池", 
            SUB_CORE_THREADS, SUB_MAX_THREADS, 200);
    }
    
    /**
     * 创建线程池的通用方法
     */
    private ThreadPoolExecutor createThreadPool(String poolName, int coreSize, 
            int maxSize, int queueCapacity) {
        return new ThreadPoolExecutor(
            coreSize,
            maxSize,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(queueCapacity),
            new CustomThreadFactory(poolName),
            new CustomRejectedExecutionHandler(poolName)
        );
    }
    
    /**
     * 自定义线程工厂
     */
    private static class CustomThreadFactory implements ThreadFactory {
        private final String poolName;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        
        public CustomThreadFactory(String poolName) {
            this.poolName = poolName;
        }
        
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName(poolName + "-线程-" + threadNumber.getAndIncrement());
            thread.setDaemon(false);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
    
    /**
     * 自定义拒绝策略
     */
    private static class CustomRejectedExecutionHandler implements RejectedExecutionHandler {
        private final String poolName;
        
        public CustomRejectedExecutionHandler(String poolName) {
            this.poolName = poolName;
        }
        
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            System.err.println(String.format("[%s] 任务被拒绝: %s", poolName, r));
            // 在调用者线程中执行被拒绝的任务
            if (!executor.isShutdown()) {
                r.run();
            }
        }
    }
    
    /**
     * 数据处理任务
     */
    public static class DataProcessingTask {
        private final int id;
        private final List<Integer> data;
        
        public DataProcessingTask(int id, List<Integer> data) {
            this.id = id;
            this.data = data;
        }
        
        public int getId() { return id; }
        public List<Integer> getData() { return data; }
    }
    
    /**
     * 处理结果
     */
    public static class ProcessingResult {
        private final int taskId;
        private final long sum;
        private final double average;
        private final List<ProcessingResult> subResults;
        
        public ProcessingResult(int taskId, long sum, double average, 
                List<ProcessingResult> subResults) {
            this.taskId = taskId;
            this.sum = sum;
            this.average = average;
            this.subResults = subResults;
        }
        
        @Override
        public String toString() {
            return String.format("任务%d: 总和=%d, 平均值=%.2f, 子任务数=%d", 
                taskId, sum, average, subResults != null ? subResults.size() : 0);
        }
    }
    
    /**
     * 主方法：并行处理多个数据集
     */
    public List<ProcessingResult> processDataSets(List<DataProcessingTask> tasks) 
            throws InterruptedException, ExecutionException {
        
        System.out.println("\n开始并行处理数据集...");
        System.out.println("主任务数: " + tasks.size());
        
        // 使用CountDownLatch等待所有任务完成
        CountDownLatch mainLatch = new CountDownLatch(tasks.size());
        
        // 存储所有主任务的Future
        List<Future<ProcessingResult>> mainFutures = new ArrayList<>();
        
        // 提交主任务到主线程池
        for (DataProcessingTask task : tasks) {
            Future<ProcessingResult> future = mainThreadPool.submit(() -> {
                try {
                    int taskNum = mainTaskCounter.incrementAndGet();
                    String threadName = Thread.currentThread().getName();
                    System.out.println(String.format("[%s] 开始处理主任务 #%d (ID=%d)", 
                        threadName, taskNum, task.getId()));
                    
                    // 将数据分割成多个子任务
                    List<List<Integer>> chunks = splitData(task.getData(), 3);
                    
                    // 在子线程池中并行处理子任务
                    List<ProcessingResult> subResults = processSubTasks(task.getId(), chunks);
                    
                    // 汇总子任务结果
                    long totalSum = subResults.stream()
                        .mapToLong(r -> r.sum)
                        .sum();
                    
                    double average = task.getData().isEmpty() ? 0 : 
                        (double) totalSum / task.getData().size();
                    
                    ProcessingResult result = new ProcessingResult(
                        task.getId(), totalSum, average, subResults);
                    
                    System.out.println(String.format("[%s] 完成主任务 #%d: %s", 
                        threadName, taskNum, result));
                    
                    return result;
                    
                } finally {
                    mainLatch.countDown();
                }
            });
            
            mainFutures.add(future);
        }
        
        // 等待所有主任务完成
        boolean completed = mainLatch.await(30, TimeUnit.SECONDS);
        if (!completed) {
            System.err.println("警告: 部分任务未在30秒内完成");
        }
        
        // 收集结果
        List<ProcessingResult> results = new ArrayList<>();
        for (Future<ProcessingResult> future : mainFutures) {
            try {
                results.add(future.get());
            } catch (Exception e) {
                System.err.println("获取任务结果失败: " + e.getMessage());
            }
        }
        
        return results;
    }
    
    /**
     * 子方法：在子线程池中处理数据块
     */
    private List<ProcessingResult> processSubTasks(int mainTaskId, List<List<Integer>> chunks) 
            throws InterruptedException, ExecutionException {
        
        // 使用CyclicBarrier实现子任务之间的同步
        CyclicBarrier barrier = new CyclicBarrier(chunks.size(), () -> {
            System.out.println(String.format("  主任务%d的所有子任务已到达屏障点", mainTaskId));
        });
        
        List<Future<ProcessingResult>> subFutures = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            final int subTaskId = i + 1;
            final List<Integer> chunk = chunks.get(i);
            
            Future<ProcessingResult> future = subThreadPool.submit(() -> {
                int taskNum = subTaskCounter.incrementAndGet();
                String threadName = Thread.currentThread().getName();
                
                try {
                    System.out.println(String.format("    [%s] 开始子任务 #%d (主任务%d-子任务%d)", 
                        threadName, taskNum, mainTaskId, subTaskId));
                    
                    // 模拟数据处理
                    Thread.sleep(100 + new Random().nextInt(400));
                    
                    // 计算数据块的统计信息
                    long sum = chunk.stream().mapToInt(Integer::intValue).sum();
                    double avg = chunk.isEmpty() ? 0 : (double) sum / chunk.size();
                    
                    // 等待其他子任务
                    barrier.await(5, TimeUnit.SECONDS);
                    
                    ProcessingResult result = new ProcessingResult(subTaskId, sum, avg, null);
                    
                    System.out.println(String.format("    [%s] 完成子任务 #%d: %s", 
                        threadName, taskNum, result));
                    
                    return result;
                    
                } catch (Exception e) {
                    System.err.println(String.format("    子任务 #%d 失败: %s", 
                        taskNum, e.getMessage()));
                    return new ProcessingResult(subTaskId, 0, 0, null);
                }
            });
            
            subFutures.add(future);
        }
        
        // 收集子任务结果
        List<ProcessingResult> subResults = new ArrayList<>();
        for (Future<ProcessingResult> future : subFutures) {
            subResults.add(future.get());
        }
        
        return subResults;
    }
    
    /**
     * 将数据分割成多个块
     */
    private List<List<Integer>> splitData(List<Integer> data, int numChunks) {
        List<List<Integer>> chunks = new ArrayList<>();
        
        if (data.isEmpty()) {
            for (int i = 0; i < numChunks; i++) {
                chunks.add(new ArrayList<>());
            }
            return chunks;
        }
        
        int chunkSize = Math.max(1, data.size() / numChunks);
        
        for (int i = 0; i < data.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, data.size());
            chunks.add(new ArrayList<>(data.subList(i, end)));
            
            if (chunks.size() >= numChunks - 1 && end < data.size()) {
                // 将剩余元素添加到最后一个块
                chunks.add(new ArrayList<>(data.subList(end, data.size())));
                break;
            }
        }
        
        return chunks;
    }
    
    /**
     * 监控线程池状态
     */
    public void monitorThreadPools() {
        System.out.println("\n=== 线程池监控信息 ===");
        
        printPoolStatus("主线程池", mainThreadPool);
        printPoolStatus("子线程池", subThreadPool);
        
        System.out.println("\n任务统计:");
        System.out.println("  已执行的主任务数: " + mainTaskCounter.get());
        System.out.println("  已执行的子任务数: " + subTaskCounter.get());
    }
    
    /**
     * 打印线程池状态
     */
    private void printPoolStatus(String poolName, ThreadPoolExecutor pool) {
        System.out.println("\n" + poolName + " 状态:");
        System.out.println("  核心线程数: " + pool.getCorePoolSize());
        System.out.println("  最大线程数: " + pool.getMaximumPoolSize());
        System.out.println("  当前线程数: " + pool.getPoolSize());
        System.out.println("  活动线程数: " + pool.getActiveCount());
        System.out.println("  已完成任务数: " + pool.getCompletedTaskCount());
        System.out.println("  队列中的任务数: " + pool.getQueue().size());
    }
    
    /**
     * 优雅关闭线程池
     */
    public void shutdown() {
        System.out.println("\n开始关闭线程池...");
        
        // 停止接受新任务
        mainThreadPool.shutdown();
        subThreadPool.shutdown();
        
        try {
            // 等待现有任务完成
            if (!mainThreadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                System.out.println("主线程池未能在10秒内完成，强制关闭");
                List<Runnable> pending = mainThreadPool.shutdownNow();
                System.out.println("主线程池待处理任务数: " + pending.size());
            }
            
            if (!subThreadPool.awaitTermination(10, TimeUnit.SECONDS)) {
                System.out.println("子线程池未能在10秒内完成，强制关闭");
                List<Runnable> pending = subThreadPool.shutdownNow();
                System.out.println("子线程池待处理任务数: " + pending.size());
            }
            
        } catch (InterruptedException e) {
            System.err.println("关闭线程池被中断");
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
        AdvancedThreadPoolExample example = new AdvancedThreadPoolExample();
        
        try {
            // 准备测试数据
            List<DataProcessingTask> tasks = new ArrayList<>();
            Random random = new Random();
            
            // 创建5个主任务，每个包含不同大小的数据集
            for (int i = 1; i <= 5; i++) {
                List<Integer> data = new ArrayList<>();
                int dataSize = 100 + random.nextInt(900);  // 100-1000个数据
                
                for (int j = 0; j < dataSize; j++) {
                    data.add(random.nextInt(1000));
                }
                
                tasks.add(new DataProcessingTask(i, data));
                System.out.println(String.format("创建任务%d，数据量: %d", i, dataSize));
            }
            
            // 执行并行处理
            long startTime = System.currentTimeMillis();
            List<ProcessingResult> results = example.processDataSets(tasks);
            long endTime = System.currentTimeMillis();
            
            // 打印结果
            System.out.println("\n=== 处理结果汇总 ===");
            for (ProcessingResult result : results) {
                System.out.println(result);
            }
            
            System.out.println(String.format("\n总耗时: %d ms", endTime - startTime));
            
            // 显示线程池监控信息
            example.monitorThreadPools();
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 关闭线程池
            example.shutdown();
        }
    }
}