# Java双线程池并行执行示例

这个项目演示了如何在Java中的主方法和子方法中同时使用两个不同的线程池进行并行执行。

## 项目概述

`ParallelExecutionExample.java` 展示了以下核心概念：

1. **双线程池架构**: 使用两个独立的线程池来处理不同类型的任务
2. **并行执行**: 主方法和子方法同时使用各自的线程池执行任务
3. **任务协调**: 使用 `CountDownLatch` 等机制协调不同线程池的任务
4. **结果收集**: 使用 `Future` 收集并处理异步任务的结果

## 核心特性

### 线程池配置

- **主线程池** (`mainThreadPool`): 4个线程，用于处理主要业务逻辑
- **子线程池** (`subThreadPool`): 3个线程，用于处理辅助任务

### 任务类型

1. **主任务**: 在主方法中执行的业务逻辑任务
2. **子任务**: 在子方法中执行的计算密集型任务
3. **协调任务**: 演示两个线程池之间的协调机制

## 运行示例

### 编译和运行

```bash
# 编译
javac ParallelExecutionExample.java

# 运行
java ParallelExecutionExample
```

### 预期输出

程序将显示：
- 两个线程池的任务执行情况
- 每个任务的线程分配信息
- 任务执行结果
- 线程池协调演示
- 线程池关闭过程

## 代码结构说明

### 主要方法

1. **`main(String[] args)`**: 程序入口，协调整个执行流程
2. **`executeMainTasks()`**: 使用主线程池执行主要任务
3. **`executeSubTasks()`**: 使用子线程池执行辅助任务
4. **`demonstrateTaskCoordination()`**: 演示任务间协调机制
5. **`shutdownThreadPools()`**: 优雅关闭线程池

### 关键技术点

#### 1. 线程池创建
```java
// 主线程池 - 固定4个线程
private static final ExecutorService mainThreadPool = Executors.newFixedThreadPool(4, customThreadFactory);

// 子线程池 - 固定3个线程  
private static final ExecutorService subThreadPool = Executors.newFixedThreadPool(3, customThreadFactory);
```

#### 2. 任务提交和结果收集
```java
// 提交任务
Future<String> future = mainThreadPool.submit(() -> {
    // 任务逻辑
    return "任务结果";
});

// 收集结果
String result = future.get(5, TimeUnit.SECONDS);
```

#### 3. 任务协调
```java
// 使用CountDownLatch协调多个线程池
CountDownLatch latch = new CountDownLatch(2);
// ... 任务执行
latch.await(5, TimeUnit.SECONDS);
```

## 实际应用场景

这种双线程池模式适用于：

1. **Web应用**: 主线程池处理HTTP请求，子线程池处理后台任务
2. **数据处理**: 主线程池处理数据读取，子线程池处理数据计算
3. **微服务**: 主线程池处理业务逻辑，子线程池处理外部服务调用
4. **批处理**: 主线程池处理文件读写，子线程池处理数据转换

## 最佳实践

1. **线程池大小**: 根据CPU核心数和任务类型合理配置线程数
2. **任务分离**: 将不同类型的任务分配给不同的线程池
3. **异常处理**: 妥善处理任务执行中的异常
4. **资源管理**: 确保线程池的正确关闭
5. **监控**: 添加线程池状态监控和日志记录

## 扩展建议

1. 添加线程池监控指标
2. 实现动态线程池大小调整
3. 添加任务优先级支持
4. 集成Spring框架的异步支持
5. 添加分布式任务协调机制