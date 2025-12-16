package com.example.queue;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多线程任务队列管理器
 * 提供灵活的线程池配置和任务调度功能
 */
public class TaskQueueManager {
    
    private final ThreadPoolExecutor executor;
    private final BlockingQueue<Runnable> taskQueue;
    private final AtomicInteger completedTasks = new AtomicInteger(0);
    private final AtomicInteger failedTasks = new AtomicInteger(0);
    
    /**
     * 使用默认配置创建任务队列管理器
     */
    public TaskQueueManager() {
        this(Runtime.getRuntime().availableProcessors(), 
             Runtime.getRuntime().availableProcessors() * 2, 
             1000);
    }
    
    /**
     * 创建自定义配置的任务队列管理器
     * 
     * @param corePoolSize    核心线程数
     * @param maxPoolSize     最大线程数
     * @param queueCapacity   队列容量
     */
    public TaskQueueManager(int corePoolSize, int maxPoolSize, int queueCapacity) {
        this.taskQueue = new LinkedBlockingQueue<>(queueCapacity);
        
        // 自定义线程工厂，便于调试和监控
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "TaskQueue-Worker-" + threadNumber.getAndIncrement());
                thread.setDaemon(false);
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            }
        };
        
        // 自定义拒绝策略
        RejectedExecutionHandler rejectionHandler = (r, executor) -> {
            System.err.println("任务被拒绝: " + r.toString());
            failedTasks.incrementAndGet();
            // 可以选择：
            // 1. 抛出异常
            // 2. 在调用线程中执行
            // 3. 丢弃最旧的任务
            // 4. 静默丢弃
            throw new RejectedExecutionException("任务队列已满，任务被拒绝");
        };
        
        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,  // 空闲线程存活时间
                taskQueue,
                threadFactory,
                rejectionHandler
        );
        
        // 允许核心线程超时
        executor.allowCoreThreadTimeOut(true);
    }
    
    /**
     * 提交一个无返回值的任务
     */
    public void submit(Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
                completedTasks.incrementAndGet();
            } catch (Exception e) {
                failedTasks.incrementAndGet();
                System.err.println("任务执行失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 提交一个有返回值的任务
     */
    public <T> Future<T> submit(Callable<T> task) {
        return executor.submit(() -> {
            try {
                T result = task.call();
                completedTasks.incrementAndGet();
                return result;
            } catch (Exception e) {
                failedTasks.incrementAndGet();
                throw e;
            }
        });
    }
    
    /**
     * 提交一个带超时的任务
     */
    public <T> T submitWithTimeout(Callable<T> task, long timeout, TimeUnit unit) 
            throws InterruptedException, ExecutionException, TimeoutException {
        Future<T> future = submit(task);
        return future.get(timeout, unit);
    }
    
    /**
     * 获取队列中等待的任务数
     */
    public int getPendingTaskCount() {
        return taskQueue.size();
    }
    
    /**
     * 获取当前活跃的工作线程数
     */
    public int getActiveThreadCount() {
        return executor.getActiveCount();
    }
    
    /**
     * 获取已完成的任务数
     */
    public int getCompletedTaskCount() {
        return completedTasks.get();
    }
    
    /**
     * 获取失败的任务数
     */
    public int getFailedTaskCount() {
        return failedTasks.get();
    }
    
    /**
     * 获取线程池统计信息
     */
    public String getStatistics() {
        return String.format(
            "线程池统计信息:\n" +
            "  核心线程数: %d\n" +
            "  最大线程数: %d\n" +
            "  当前线程数: %d\n" +
            "  活跃线程数: %d\n" +
            "  队列等待任务数: %d\n" +
            "  已完成任务数: %d\n" +
            "  失败任务数: %d",
            executor.getCorePoolSize(),
            executor.getMaximumPoolSize(),
            executor.getPoolSize(),
            executor.getActiveCount(),
            taskQueue.size(),
            completedTasks.get(),
            failedTasks.get()
        );
    }
    
    /**
     * 优雅关闭任务队列
     */
    public void shutdown() {
        System.out.println("正在关闭任务队列...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                System.out.println("等待超时，强制关闭...");
                executor.shutdownNow();
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("线程池未能正常关闭");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("任务队列已关闭");
    }
    
    /**
     * 立即关闭任务队列
     */
    public void shutdownNow() {
        executor.shutdownNow();
    }
    
    /**
     * 检查任务队列是否已关闭
     */
    public boolean isShutdown() {
        return executor.isShutdown();
    }
    
    /**
     * 检查所有任务是否已完成
     */
    public boolean isTerminated() {
        return executor.isTerminated();
    }
}
