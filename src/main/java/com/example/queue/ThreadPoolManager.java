package com.example.queue;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 线程池管理器
 * 管理线程池和执行任务队列中的任务
 */
public class ThreadPoolManager {
    private final ThreadPoolExecutor executor;
    private final TaskQueue taskQueue;
    private final AtomicLong completedTaskCount;
    private final AtomicLong failedTaskCount;
    private volatile boolean isShutdown;
    
    /**
     * 创建线程池管理器（使用默认配置）
     * @param taskQueue 任务队列
     */
    public ThreadPoolManager(TaskQueue taskQueue) {
        this(taskQueue, Runtime.getRuntime().availableProcessors(), 
             Runtime.getRuntime().availableProcessors() * 2, 
             60L, TimeUnit.SECONDS);
    }
    
    /**
     * 创建线程池管理器
     * @param taskQueue 任务队列
     * @param corePoolSize 核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime 线程空闲时间
     * @param unit 时间单位
     */
    public ThreadPoolManager(TaskQueue taskQueue, int corePoolSize, int maximumPoolSize,
                             long keepAliveTime, TimeUnit unit) {
        if (taskQueue == null) {
            throw new IllegalArgumentException("任务队列不能为null");
        }
        this.taskQueue = taskQueue;
        this.completedTaskCount = new AtomicLong(0);
        this.failedTaskCount = new AtomicLong(0);
        this.isShutdown = false;
        
        // 创建线程池
        this.executor = new ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            keepAliveTime,
            unit,
            new LinkedBlockingQueue<>(),
            new CustomThreadFactory(),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // 启动工作线程
        startWorkers();
    }
    
    /**
     * 启动工作线程从队列中取任务执行
     */
    private void startWorkers() {
        int workerCount = executor.getCorePoolSize();
        for (int i = 0; i < workerCount; i++) {
            executor.submit(new Worker());
        }
    }
    
    /**
     * 工作线程，从队列中取任务并执行
     */
    private class Worker implements Runnable {
        @Override
        public void run() {
            while (!isShutdown || !taskQueue.isEmpty()) {
                try {
                    Task task = taskQueue.takeTask();
                    if (task != null) {
                        executeTask(task);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        private void executeTask(Task task) {
            try {
                task.execute();
                completedTaskCount.incrementAndGet();
            } catch (Exception e) {
                failedTaskCount.incrementAndGet();
                System.err.println("任务执行失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 自定义线程工厂
     */
    private static class CustomThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "TaskWorker-" + threadNumber.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
    
    /**
     * 提交任务到队列
     * @param task 任务
     * @throws InterruptedException 如果等待时被中断
     */
    public void submit(Task task) throws InterruptedException {
        if (isShutdown) {
            throw new IllegalStateException("线程池已关闭");
        }
        taskQueue.addTask(task);
    }
    
    /**
     * 提交任务到队列（非阻塞）
     * @param task 任务
     * @return 如果成功提交返回true
     */
    public boolean trySubmit(Task task) {
        if (isShutdown) {
            return false;
        }
        return taskQueue.offerTask(task);
    }
    
    /**
     * 获取已完成任务数
     * @return 已完成任务数
     */
    public long getCompletedTaskCount() {
        return completedTaskCount.get();
    }
    
    /**
     * 获取失败任务数
     * @return 失败任务数
     */
    public long getFailedTaskCount() {
        return failedTaskCount.get();
    }
    
    /**
     * 获取队列中等待的任务数
     * @return 等待任务数
     */
    public int getQueueSize() {
        return taskQueue.size();
    }
    
    /**
     * 获取活跃线程数
     * @return 活跃线程数
     */
    public int getActiveThreadCount() {
        return executor.getActiveCount();
    }
    
    /**
     * 获取线程池大小
     * @return 线程池大小
     */
    public int getPoolSize() {
        return executor.getPoolSize();
    }
    
    /**
     * 关闭线程池（优雅关闭）
     * @param timeout 等待时间
     * @param unit 时间单位
     * @throws InterruptedException 如果等待时被中断
     */
    public void shutdown(long timeout, TimeUnit unit) throws InterruptedException {
        isShutdown = true;
        executor.shutdown();
        if (!executor.awaitTermination(timeout, unit)) {
            executor.shutdownNow();
            if (!executor.awaitTermination(timeout, unit)) {
                System.err.println("线程池未能正常关闭");
            }
        }
    }
    
    /**
     * 立即关闭线程池
     */
    public void shutdownNow() {
        isShutdown = true;
        executor.shutdownNow();
    }
    
    /**
     * 检查线程池是否已关闭
     * @return 如果已关闭返回true
     */
    public boolean isShutdown() {
        return isShutdown;
    }
}
