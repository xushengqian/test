package com.example.queue;

import java.util.concurrent.*;

/**
 * 定时任务队列
 * 支持延迟执行和周期性执行任务
 */
public class ScheduledTaskQueue {
    
    private final ScheduledThreadPoolExecutor scheduler;
    
    /**
     * 创建定时任务队列
     * 
     * @param corePoolSize 核心线程数
     */
    public ScheduledTaskQueue(int corePoolSize) {
        this.scheduler = new ScheduledThreadPoolExecutor(corePoolSize);
        
        // 配置：在关闭时取消周期任务
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        // 配置：在关闭时执行已延迟的任务
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(true);
        // 配置：移除已取消的任务
        scheduler.setRemoveOnCancelPolicy(true);
    }
    
    /**
     * 延迟执行任务
     * 
     * @param task  任务
     * @param delay 延迟时间
     * @param unit  时间单位
     */
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return scheduler.schedule(task, delay, unit);
    }
    
    /**
     * 延迟执行带返回值的任务
     */
    public <T> ScheduledFuture<T> schedule(Callable<T> task, long delay, TimeUnit unit) {
        return scheduler.schedule(task, delay, unit);
    }
    
    /**
     * 固定速率周期执行任务
     * 任务会在 initialDelay 后首次执行，之后每隔 period 时间执行一次
     * 注意：如果任务执行时间超过 period，下次执行会立即开始
     * 
     * @param task         任务
     * @param initialDelay 首次执行延迟
     * @param period       执行周期
     * @param unit         时间单位
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return scheduler.scheduleAtFixedRate(task, initialDelay, period, unit);
    }
    
    /**
     * 固定延迟周期执行任务
     * 任务在上一次执行完成后，等待 delay 时间后再次执行
     * 
     * @param task         任务
     * @param initialDelay 首次执行延迟
     * @param delay        两次执行之间的延迟
     * @param unit         时间单位
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        return scheduler.scheduleWithFixedDelay(task, initialDelay, delay, unit);
    }
    
    /**
     * 获取当前队列中的任务数
     */
    public int getQueueSize() {
        return scheduler.getQueue().size();
    }
    
    /**
     * 获取活跃线程数
     */
    public int getActiveCount() {
        return scheduler.getActiveCount();
    }
    
    /**
     * 关闭调度器
     */
    public void shutdown() {
        scheduler.shutdown();
    }
    
    /**
     * 等待所有任务完成
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return scheduler.awaitTermination(timeout, unit);
    }
    
    /**
     * 立即关闭
     */
    public void shutdownNow() {
        scheduler.shutdownNow();
    }
}
