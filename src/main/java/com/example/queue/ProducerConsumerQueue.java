package com.example.queue;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 生产者-消费者模式的任务队列
 * 使用 BlockingQueue 实现线程安全的任务传递
 */
public class ProducerConsumerQueue<T> {
    
    private final BlockingQueue<T> queue;
    private final ExecutorService consumerExecutor;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final int consumerCount;
    
    /**
     * 创建生产者-消费者队列
     * 
     * @param queueCapacity  队列容量
     * @param consumerCount  消费者线程数
     * @param taskHandler    任务处理器
     */
    public ProducerConsumerQueue(int queueCapacity, int consumerCount, TaskHandler<T> taskHandler) {
        this.queue = new LinkedBlockingQueue<>(queueCapacity);
        this.consumerCount = consumerCount;
        this.consumerExecutor = Executors.newFixedThreadPool(consumerCount);
        
        // 启动消费者线程
        for (int i = 0; i < consumerCount; i++) {
            final int consumerId = i;
            consumerExecutor.submit(() -> {
                Thread.currentThread().setName("Consumer-" + consumerId);
                consumeLoop(taskHandler);
            });
        }
    }
    
    /**
     * 消费者循环
     */
    private void consumeLoop(TaskHandler<T> taskHandler) {
        while (running.get() || !queue.isEmpty()) {
            try {
                // 使用 poll 带超时，以便能够响应关闭信号
                T task = queue.poll(100, TimeUnit.MILLISECONDS);
                if (task != null) {
                    try {
                        taskHandler.handle(task);
                    } catch (Exception e) {
                        System.err.println("任务处理失败: " + e.getMessage());
                        taskHandler.onError(task, e);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println(Thread.currentThread().getName() + " 已退出");
    }
    
    /**
     * 生产者提交任务（阻塞式）
     */
    public void produce(T task) throws InterruptedException {
        queue.put(task);
    }
    
    /**
     * 生产者提交任务（非阻塞式）
     * 
     * @return 如果队列已满返回 false
     */
    public boolean tryProduce(T task) {
        return queue.offer(task);
    }
    
    /**
     * 生产者提交任务（带超时）
     */
    public boolean produce(T task, long timeout, TimeUnit unit) throws InterruptedException {
        return queue.offer(task, timeout, unit);
    }
    
    /**
     * 获取当前队列大小
     */
    public int getQueueSize() {
        return queue.size();
    }
    
    /**
     * 获取消费者线程数
     */
    public int getConsumerCount() {
        return consumerCount;
    }
    
    /**
     * 关闭队列
     */
    public void shutdown() {
        System.out.println("正在关闭生产者-消费者队列...");
        running.set(false);
        consumerExecutor.shutdown();
        try {
            if (!consumerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                consumerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            consumerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("生产者-消费者队列已关闭");
    }
    
    /**
     * 任务处理器接口
     */
    @FunctionalInterface
    public interface TaskHandler<T> {
        void handle(T task) throws Exception;
        
        default void onError(T task, Exception e) {
            // 默认错误处理
        }
    }
}
