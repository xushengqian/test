# Java 多线程任务队列

本项目提供了多种 Java 多线程任务队列的实现方式，适用于不同的业务场景。

## 项目结构

```
src/main/java/com/example/
├── queue/
│   ├── TaskQueueManager.java        # 基本任务队列管理器
│   ├── ProducerConsumerQueue.java   # 生产者-消费者模式队列
│   ├── PriorityTaskQueue.java       # 优先级任务队列
│   ├── ScheduledTaskQueue.java      # 定时任务队列
│   └── CompletableFutureExample.java # CompletableFuture 异步示例
└── demo/
    └── TaskQueueDemo.java           # 演示程序
```

## 功能特性

### 1. TaskQueueManager - 基本任务队列管理器

- 可配置的核心线程数、最大线程数和队列容量
- 自定义线程工厂和拒绝策略
- 支持无返回值任务和有返回值任务
- 任务统计（完成数、失败数）
- 优雅关闭

```java
TaskQueueManager manager = new TaskQueueManager(4, 8, 1000);

// 提交无返回值任务
manager.submit(() -> {
    System.out.println("执行任务");
});

// 提交有返回值任务
Future<String> future = manager.submit(() -> {
    return "计算结果";
});
String result = future.get();

// 获取统计信息
System.out.println(manager.getStatistics());

// 关闭
manager.shutdown();
```

### 2. ProducerConsumerQueue - 生产者-消费者模式

- 基于 BlockingQueue 的线程安全实现
- 支持多消费者
- 支持阻塞式和非阻塞式提交
- 可自定义任务处理器和错误处理

```java
ProducerConsumerQueue<String> queue = new ProducerConsumerQueue<>(
    100,  // 队列容量
    4,    // 消费者线程数
    task -> {
        System.out.println("处理: " + task);
    }
);

// 生产者提交任务
queue.produce("任务数据");
queue.tryProduce("非阻塞提交");
queue.produce("超时提交", 5, TimeUnit.SECONDS);

queue.shutdown();
```

### 3. PriorityTaskQueue - 优先级任务队列

- 按优先级执行任务
- 预定义优先级常量
- 高优先级任务优先执行

```java
PriorityTaskQueue priorityQueue = new PriorityTaskQueue(2, 4);

priorityQueue.submitLowPriority(() -> "低优先级");
priorityQueue.submit(() -> "普通优先级");
priorityQueue.submitHighPriority(() -> "高优先级");
priorityQueue.submit(PriorityTaskQueue.Priority.URGENT, () -> "紧急");

priorityQueue.shutdown();
```

### 4. ScheduledTaskQueue - 定时任务队列

- 延迟执行任务
- 固定速率周期执行
- 固定延迟周期执行

```java
ScheduledTaskQueue scheduler = new ScheduledTaskQueue(2);

// 延迟5秒执行
scheduler.schedule(() -> System.out.println("延迟任务"), 5, TimeUnit.SECONDS);

// 每隔1秒执行
ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
    () -> System.out.println("周期任务"),
    0,    // 初始延迟
    1,    // 周期
    TimeUnit.SECONDS
);

// 取消周期任务
task.cancel(false);

scheduler.shutdown();
```

### 5. CompletableFuture 异步任务

- 链式异步任务
- 并行执行多个任务
- 组合任务结果
- 超时和异常处理

```java
CompletableFutureExample example = new CompletableFutureExample();

// 链式任务
example.chainedTasks("输入")
    .thenAccept(result -> System.out.println(result));

// 并行任务
example.parallelTasks(Arrays.asList("A", "B", "C"))
    .thenAccept(results -> System.out.println(results));

// 带超时
example.asyncTaskWithTimeout("数据", 5, TimeUnit.SECONDS);

// 异常处理
example.asyncTaskWithExceptionHandling("输入")
    .exceptionally(ex -> "默认值");

example.shutdown();
```

## 运行演示

### 使用 Maven

```bash
# 编译
mvn compile

# 运行演示
mvn exec:java
```

### 直接编译运行

```bash
# 编译
javac -d target/classes src/main/java/com/example/**/*.java

# 运行
java -cp target/classes com.example.demo.TaskQueueDemo
```

## 线程池参数配置建议

### CPU 密集型任务
- 核心线程数 = CPU 核心数 + 1
- 最大线程数 = CPU 核心数 + 1

### IO 密集型任务
- 核心线程数 = CPU 核心数 * 2
- 最大线程数 = CPU 核心数 * 2 或更多

### 混合型任务
- 根据 CPU 计算时间和 IO 等待时间的比例调整

## 注意事项

1. **线程安全**: 任务中共享的数据需要注意线程安全
2. **资源释放**: 使用完毕后务必调用 `shutdown()` 关闭线程池
3. **异常处理**: 任务中的异常需要妥善处理，避免影响其他任务
4. **队列容量**: 合理设置队列容量，避免内存溢出
5. **拒绝策略**: 根据业务需求选择合适的拒绝策略

## 许可证

MIT License
