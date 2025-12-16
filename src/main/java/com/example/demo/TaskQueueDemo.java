package com.example.demo;

import com.example.queue.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

/**
 * 多线程任务队列演示程序
 */
public class TaskQueueDemo {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Java 多线程任务队列演示 ===\n");
        
        // 1. 基本任务队列管理器
        demoTaskQueueManager();
        
        // 2. 生产者-消费者队列
        demoProducerConsumerQueue();
        
        // 3. 优先级任务队列
        demoPriorityTaskQueue();
        
        // 4. 定时任务队列
        demoScheduledTaskQueue();
        
        // 5. CompletableFuture 异步任务
        demoCompletableFuture();
        
        System.out.println("\n=== 演示完成 ===");
    }
    
    /**
     * 演示基本任务队列管理器
     */
    private static void demoTaskQueueManager() throws Exception {
        System.out.println("--- 1. 基本任务队列管理器 ---");
        
        TaskQueueManager manager = new TaskQueueManager(2, 4, 100);
        
        // 提交多个任务
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            manager.submit(() -> {
                System.out.println("执行任务 " + taskId + " [" + Thread.currentThread().getName() + "]");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        // 提交带返回值的任务
        Future<String> future = manager.submit(() -> {
            Thread.sleep(200);
            return "任务计算结果";
        });
        
        System.out.println("等待结果: " + future.get());
        
        // 等待所有任务完成
        Thread.sleep(1000);
        System.out.println(manager.getStatistics());
        
        manager.shutdown();
        System.out.println();
    }
    
    /**
     * 演示生产者-消费者队列
     */
    private static void demoProducerConsumerQueue() throws Exception {
        System.out.println("--- 2. 生产者-消费者队列 ---");
        
        ProducerConsumerQueue<String> queue = new ProducerConsumerQueue<>(
                50,  // 队列容量
                3,   // 消费者线程数
                task -> {
                    System.out.println("消费任务: " + task + " [" + Thread.currentThread().getName() + "]");
                    Thread.sleep(50);
                }
        );
        
        // 生产者提交任务
        for (int i = 0; i < 15; i++) {
            queue.produce("任务-" + i);
        }
        
        System.out.println("队列大小: " + queue.getQueueSize());
        
        // 等待消费完成
        Thread.sleep(1000);
        
        queue.shutdown();
        System.out.println();
    }
    
    /**
     * 演示优先级任务队列
     */
    private static void demoPriorityTaskQueue() throws Exception {
        System.out.println("--- 3. 优先级任务队列 ---");
        
        PriorityTaskQueue priorityQueue = new PriorityTaskQueue(1, 2);
        
        // 提交不同优先级的任务
        priorityQueue.submitLowPriority(() -> {
            System.out.println("低优先级任务执行");
            return "低优先级结果";
        });
        
        priorityQueue.submit(() -> {
            System.out.println("普通优先级任务执行");
            return "普通优先级结果";
        });
        
        priorityQueue.submitHighPriority(() -> {
            System.out.println("高优先级任务执行");
            return "高优先级结果";
        });
        
        priorityQueue.submit(PriorityTaskQueue.Priority.URGENT, () -> {
            System.out.println("紧急任务执行");
            return "紧急任务结果";
        });
        
        priorityQueue.shutdown();
        priorityQueue.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println();
    }
    
    /**
     * 演示定时任务队列
     */
    private static void demoScheduledTaskQueue() throws Exception {
        System.out.println("--- 4. 定时任务队列 ---");
        
        ScheduledTaskQueue scheduler = new ScheduledTaskQueue(2);
        
        // 延迟执行
        scheduler.schedule(() -> {
            System.out.println("延迟1秒执行的任务");
        }, 1, TimeUnit.SECONDS);
        
        // 固定速率执行
        ScheduledFuture<?> periodicTask = scheduler.scheduleAtFixedRate(() -> {
            System.out.println("周期任务执行 - " + System.currentTimeMillis());
        }, 0, 500, TimeUnit.MILLISECONDS);
        
        // 等待几次执行
        Thread.sleep(2000);
        
        // 取消周期任务
        periodicTask.cancel(false);
        
        scheduler.shutdown();
        scheduler.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println();
    }
    
    /**
     * 演示 CompletableFuture 异步任务
     */
    private static void demoCompletableFuture() throws Exception {
        System.out.println("--- 5. CompletableFuture 异步任务 ---");
        
        CompletableFutureExample example = new CompletableFutureExample();
        
        // 基本异步任务
        String result1 = example.asyncTask("测试数据").get();
        System.out.println("基本异步任务: " + result1);
        
        // 链式任务
        String result2 = example.chainedTasks("输入").get();
        System.out.println("链式任务: " + result2);
        
        // 并行任务
        List<String> inputs = Arrays.asList("A", "B", "C", "D");
        List<String> results = example.parallelTasks(inputs).get();
        System.out.println("并行任务结果: " + results);
        
        // 组合任务
        String combined = example.combineTasks("任务1", "任务2").get();
        System.out.println("组合任务: " + combined);
        
        // 带异常处理的任务
        String handled = example.asyncTaskWithExceptionHandling("").get();
        System.out.println("异常处理后: " + handled);
        
        example.shutdown();
        System.out.println();
    }
}
