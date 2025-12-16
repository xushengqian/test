package com.example.queue;

import java.util.concurrent.*;

/**
 * 优先级任务队列
 * 支持按优先级执行任务，高优先级任务优先执行
 */
public class PriorityTaskQueue {
    
    private final ThreadPoolExecutor executor;
    
    /**
     * 创建优先级任务队列
     * 
     * @param corePoolSize 核心线程数
     * @param maxPoolSize  最大线程数
     */
    public PriorityTaskQueue(int corePoolSize, int maxPoolSize) {
        // 使用优先级阻塞队列
        PriorityBlockingQueue<Runnable> priorityQueue = new PriorityBlockingQueue<>(
                100,
                (r1, r2) -> {
                    if (r1 instanceof PriorityTask && r2 instanceof PriorityTask) {
                        return Integer.compare(
                                ((PriorityTask<?>) r2).getPriority(),
                                ((PriorityTask<?>) r1).getPriority()
                        );
                    }
                    return 0;
                }
        );
        
        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,
                priorityQueue
        );
    }
    
    /**
     * 提交优先级任务
     * 
     * @param priority 优先级（数字越大优先级越高）
     * @param task     任务
     */
    public <T> Future<T> submit(int priority, Callable<T> task) {
        PriorityTask<T> priorityTask = new PriorityTask<>(priority, task);
        executor.execute(priorityTask);
        return priorityTask;
    }
    
    /**
     * 提交普通优先级任务
     */
    public <T> Future<T> submit(Callable<T> task) {
        return submit(Priority.NORMAL, task);
    }
    
    /**
     * 提交高优先级任务
     */
    public <T> Future<T> submitHighPriority(Callable<T> task) {
        return submit(Priority.HIGH, task);
    }
    
    /**
     * 提交低优先级任务
     */
    public <T> Future<T> submitLowPriority(Callable<T> task) {
        return submit(Priority.LOW, task);
    }
    
    /**
     * 关闭队列
     */
    public void shutdown() {
        executor.shutdown();
    }
    
    /**
     * 等待所有任务完成
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }
    
    /**
     * 优先级常量
     */
    public static class Priority {
        public static final int LOW = 1;
        public static final int NORMAL = 5;
        public static final int HIGH = 10;
        public static final int URGENT = 100;
    }
    
    /**
     * 优先级任务包装类
     */
    private static class PriorityTask<T> extends FutureTask<T> implements Comparable<PriorityTask<T>> {
        private final int priority;
        
        public PriorityTask(int priority, Callable<T> callable) {
            super(callable);
            this.priority = priority;
        }
        
        public int getPriority() {
            return priority;
        }
        
        @Override
        public int compareTo(PriorityTask<T> other) {
            return Integer.compare(other.priority, this.priority);
        }
    }
}
