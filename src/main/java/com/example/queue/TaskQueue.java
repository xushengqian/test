package com.example.queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务队列管理器
 * 提供线程安全的任务队列操作
 */
public class TaskQueue {
    private final BlockingQueue<Task> queue;
    private final AtomicInteger taskCount;
    private final int maxSize;
    
    /**
     * 创建无界任务队列
     */
    public TaskQueue() {
        this.queue = new LinkedBlockingQueue<>();
        this.taskCount = new AtomicInteger(0);
        this.maxSize = Integer.MAX_VALUE;
    }
    
    /**
     * 创建有界任务队列
     * @param maxSize 队列最大容量
     */
    public TaskQueue(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("队列大小必须大于0");
        }
        this.queue = new LinkedBlockingQueue<>(maxSize);
        this.taskCount = new AtomicInteger(0);
        this.maxSize = maxSize;
    }
    
    /**
     * 添加任务到队列（阻塞）
     * @param task 要添加的任务
     * @throws InterruptedException 如果等待时被中断
     */
    public void addTask(Task task) throws InterruptedException {
        if (task == null) {
            throw new IllegalArgumentException("任务不能为null");
        }
        queue.put(task);
        taskCount.incrementAndGet();
    }
    
    /**
     * 添加任务到队列（非阻塞，立即返回）
     * @param task 要添加的任务
     * @return 如果成功添加返回true，如果队列已满返回false
     */
    public boolean offerTask(Task task) {
        if (task == null) {
            throw new IllegalArgumentException("任务不能为null");
        }
        boolean result = queue.offer(task);
        if (result) {
            taskCount.incrementAndGet();
        }
        return result;
    }
    
    /**
     * 添加任务到队列（带超时）
     * @param task 要添加的任务
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 如果成功添加返回true，如果超时返回false
     * @throws InterruptedException 如果等待时被中断
     */
    public boolean offerTask(Task task, long timeout, TimeUnit unit) throws InterruptedException {
        if (task == null) {
            throw new IllegalArgumentException("任务不能为null");
        }
        boolean result = queue.offer(task, timeout, unit);
        if (result) {
            taskCount.incrementAndGet();
        }
        return result;
    }
    
    /**
     * 从队列中取出任务（阻塞）
     * @return 任务
     * @throws InterruptedException 如果等待时被中断
     */
    public Task takeTask() throws InterruptedException {
        Task task = queue.take();
        taskCount.decrementAndGet();
        return task;
    }
    
    /**
     * 从队列中取出任务（非阻塞）
     * @return 任务，如果队列为空返回null
     */
    public Task pollTask() {
        Task task = queue.poll();
        if (task != null) {
            taskCount.decrementAndGet();
        }
        return task;
    }
    
    /**
     * 从队列中取出任务（带超时）
     * @param timeout 超时时间
     * @param unit 时间单位
     * @return 任务，如果超时返回null
     * @throws InterruptedException 如果等待时被中断
     */
    public Task pollTask(long timeout, TimeUnit unit) throws InterruptedException {
        Task task = queue.poll(timeout, unit);
        if (task != null) {
            taskCount.decrementAndGet();
        }
        return task;
    }
    
    /**
     * 获取队列中当前任务数量
     * @return 任务数量
     */
    public int size() {
        return queue.size();
    }
    
    /**
     * 检查队列是否为空
     * @return 如果队列为空返回true
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }
    
    /**
     * 获取队列最大容量
     * @return 最大容量
     */
    public int getMaxSize() {
        return maxSize;
    }
    
    /**
     * 获取已处理的任务总数
     * @return 任务总数
     */
    public int getTaskCount() {
        return taskCount.get();
    }
    
    /**
     * 清空队列
     */
    public void clear() {
        queue.clear();
        taskCount.set(0);
    }
}
