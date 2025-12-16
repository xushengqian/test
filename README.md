# Java 多线程任务队列

一个功能完整的 Java 多线程任务队列实现，提供了线程安全的任务队列管理和线程池执行功能。

## 功能特性

- ✅ 线程安全的任务队列（支持有界和无界队列）
- ✅ 灵活的线程池配置（核心线程数、最大线程数、空闲时间等）
- ✅ 多种任务提交方式（阻塞、非阻塞、带超时）
- ✅ 任务执行状态监控（已完成、失败、队列大小等）
- ✅ 优雅的线程池关闭机制
- ✅ 自定义线程工厂，便于线程管理

## 项目结构

```
src/main/java/com/example/queue/
├── Task.java                 # 任务接口
├── TaskQueue.java            # 任务队列管理器
├── ThreadPoolManager.java    # 线程池管理器
└── TaskQueueExample.java     # 使用示例
```

## 核心类说明

### Task 接口
所有需要在线程池中执行的任务都应该实现此接口。

```java
public interface Task {
    void execute() throws Exception;
}
```

### TaskQueue 类
提供线程安全的任务队列操作，支持：
- 有界/无界队列
- 阻塞/非阻塞添加任务
- 带超时的任务操作
- 队列状态查询

### ThreadPoolManager 类
管理线程池和执行任务队列中的任务，提供：
- 线程池配置管理
- 任务提交接口
- 执行状态监控
- 优雅关闭机制

## 使用方法

### 1. 创建任务队列和线程池管理器

```java
// 创建任务队列（最大容量100）
TaskQueue taskQueue = new TaskQueue(100);

// 创建线程池管理器
ThreadPoolManager manager = new ThreadPoolManager(
    taskQueue, 
    4,  // 核心线程数
    8,  // 最大线程数
    60L, 
    TimeUnit.SECONDS
);
```

### 2. 提交任务

```java
// 阻塞提交
manager.submit(() -> {
    // 任务逻辑
    System.out.println("执行任务");
});

// 非阻塞提交
boolean success = manager.trySubmit(() -> {
    // 任务逻辑
});
```

### 3. 监控状态

```java
// 获取队列中等待的任务数
int queueSize = manager.getQueueSize();

// 获取已完成任务数
long completed = manager.getCompletedTaskCount();

// 获取失败任务数
long failed = manager.getFailedTaskCount();

// 获取活跃线程数
int active = manager.getActiveThreadCount();
```

### 4. 关闭线程池

```java
// 优雅关闭（等待10秒）
manager.shutdown(10, TimeUnit.SECONDS);

// 立即关闭
manager.shutdownNow();
```

## 运行示例

使用 Maven 运行示例代码：

```bash
mvn compile exec:java
```

或者直接编译运行：

```bash
# 编译
mvn compile

# 运行
java -cp target/classes com.example.queue.TaskQueueExample
```

## 依赖

本项目仅使用 Java 标准库，无需额外依赖。

## 注意事项

1. 任务执行过程中的异常会被捕获并记录，不会影响其他任务的执行
2. 使用有界队列时，队列满的情况下提交任务会阻塞，建议使用 `trySubmit()` 或带超时的 `offerTask()` 方法
3. 关闭线程池前，确保所有重要任务已完成
4. 根据实际需求调整线程池大小，避免创建过多线程导致资源浪费

## 许可证

MIT License