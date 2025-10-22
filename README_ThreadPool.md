# Java 主方法和子方法并行执行 - 双线程池示例

本项目演示了如何在Java中使用两个独立的线程池来并行执行主方法和子方法的任务。

## 🎯 核心概念

### 1. 双线程池架构

- **主线程池**：负责执行主要的业务逻辑任务
- **子线程池**：负责执行由主任务派生的子任务

这种设计的优势：
- 任务隔离：主任务和子任务互不干扰
- 资源控制：可以为不同类型的任务分配不同的资源
- 性能优化：避免线程池饱和，提高并发处理能力

## 📁 项目文件说明

1. **DualThreadPoolExample.java** - 基础示例
   - 展示了使用Future的传统方式
   - 展示了使用CompletableFuture的现代方式
   - 适合学习基本概念

2. **AdvancedThreadPoolExample.java** - 高级示例
   - 包含自定义线程工厂和拒绝策略
   - 演示任务间的数据传递和协调
   - 使用CountDownLatch和CyclicBarrier进行同步
   - 包含线程池监控功能

## 🚀 快速开始

### 编译和运行基础示例

```bash
# 编译
javac DualThreadPoolExample.java

# 运行
java DualThreadPoolExample
```

### 编译和运行高级示例

```bash
# 编译
javac AdvancedThreadPoolExample.java

# 运行
java AdvancedThreadPoolExample
```

## 💡 关键实现技术

### 1. 线程池创建

```java
// 主线程池配置
ThreadPoolExecutor mainThreadPool = new ThreadPoolExecutor(
    4,                              // 核心线程数
    8,                              // 最大线程数
    60L, TimeUnit.SECONDS,          // 空闲线程存活时间
    new LinkedBlockingQueue<>(100), // 任务队列
    new CustomThreadFactory("主"),   // 自定义线程工厂
    new CallerRunsPolicy()          // 拒绝策略
);

// 子线程池配置（更多线程处理细粒度任务）
ThreadPoolExecutor subThreadPool = new ThreadPoolExecutor(
    8,                              // 核心线程数（更多）
    16,                             // 最大线程数（更多）
    60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(200),
    new CustomThreadFactory("子"),
    new CallerRunsPolicy()
);
```

### 2. 任务执行流程

```
主线程
  │
  ├─> 主线程池
  │     ├─> 主任务1 ──┐
  │     ├─> 主任务2 ──┤
  │     └─> 主任务3 ──┤
  │                    │
  │                    └─> 子线程池
  │                          ├─> 子任务1-1
  │                          ├─> 子任务1-2
  │                          ├─> 子任务2-1
  │                          ├─> 子任务2-2
  │                          └─> ...
  │
  └─> 收集结果
```

### 3. 并发控制机制

#### Future方式
```java
// 提交主任务
Future<String> mainFuture = mainThreadPool.submit(() -> {
    // 在主任务中提交子任务到子线程池
    List<Future<String>> subFutures = executeSubTasks();
    
    // 等待子任务完成
    for (Future<String> subFuture : subFutures) {
        String result = subFuture.get();
    }
    
    return "主任务完成";
});
```

#### CompletableFuture方式
```java
CompletableFuture<String> mainTask = CompletableFuture
    .supplyAsync(() -> {
        // 主任务逻辑
        return "主任务结果";
    }, mainThreadPool)
    .thenCompose(mainResult -> {
        // 链式调用子任务
        return CompletableFuture.supplyAsync(() -> {
            return "子任务结果";
        }, subThreadPool);
    });
```

### 4. 同步机制

#### CountDownLatch（等待多个任务完成）
```java
CountDownLatch latch = new CountDownLatch(taskCount);

for (int i = 0; i < taskCount; i++) {
    executor.submit(() -> {
        try {
            // 执行任务
        } finally {
            latch.countDown();
        }
    });
}

latch.await(); // 等待所有任务完成
```

#### CyclicBarrier（任务间同步点）
```java
CyclicBarrier barrier = new CyclicBarrier(3, () -> {
    System.out.println("所有任务到达屏障点");
});

// 每个任务中
barrier.await(); // 等待其他任务到达
```

## 📊 性能优化建议

### 1. 线程池大小设置

- **CPU密集型任务**：线程数 = CPU核心数 + 1
- **IO密集型任务**：线程数 = CPU核心数 * 2
- **混合型任务**：根据实际测试调整

### 2. 队列选择

- **LinkedBlockingQueue**：无界队列，适合任务数量不确定的场景
- **ArrayBlockingQueue**：有界队列，内存占用可控
- **SynchronousQueue**：直接传递，适合高响应要求

### 3. 拒绝策略选择

- **CallerRunsPolicy**：调用者线程执行，不丢失任务
- **AbortPolicy**：直接拒绝，抛出异常
- **DiscardPolicy**：静默丢弃
- **DiscardOldestPolicy**：丢弃最老的任务

## 🔍 监控和调试

### 线程池监控指标
```java
// 活动线程数
int activeCount = threadPool.getActiveCount();

// 已完成任务数
long completedTasks = threadPool.getCompletedTaskCount();

// 队列中等待的任务数
int queueSize = threadPool.getQueue().size();
```

### 日志最佳实践
```java
// 使用线程名称便于调试
Thread.currentThread().setName("主任务-" + taskId);

// 记录关键执行点
logger.info("[{}] 任务开始执行", Thread.currentThread().getName());
```

## ⚠️ 注意事项

1. **资源释放**：始终在finally块中关闭线程池
2. **异常处理**：子任务异常不应影响主任务执行
3. **超时控制**：使用Future.get(timeout)避免无限等待
4. **避免死锁**：主任务不应等待自己提交的子任务在同一线程池中执行

## 📈 使用场景

1. **批量数据处理**
   - 主任务：读取和分割数据
   - 子任务：并行处理数据块

2. **Web爬虫**
   - 主任务：解析主页面
   - 子任务：并行爬取子页面

3. **文件处理**
   - 主任务：扫描目录
   - 子任务：并行处理文件

4. **分布式计算**
   - 主任务：任务分发
   - 子任务：并行计算

## 🔧 扩展建议

1. **动态调整线程池**
   ```java
   threadPool.setCorePoolSize(newSize);
   threadPool.setMaximumPoolSize(newMaxSize);
   ```

2. **使用调度线程池**
   ```java
   ScheduledExecutorService scheduler = 
       Executors.newScheduledThreadPool(2);
   ```

3. **集成监控系统**
   - 使用JMX暴露线程池指标
   - 集成Metrics库进行指标收集
   - 接入APM工具进行性能分析

## 📚 参考资源

- [Java并发编程实战](https://docs.oracle.com/javase/tutorial/essential/concurrency/)
- [ThreadPoolExecutor文档](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ThreadPoolExecutor.html)
- [CompletableFuture指南](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html)

## 📝 总结

使用双线程池架构可以有效地：
- 提高系统的并发处理能力
- 实现任务的优先级控制
- 避免不同类型任务的相互影响
- 提供更好的资源管理和监控能力

根据实际业务需求选择合适的配置和同步机制，可以构建高效、稳定的并发处理系统。