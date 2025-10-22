# Java 并行执行示例项目

本项目演示了Java中的多种并行执行方式，包括主方法中的并行执行以及嵌套并行执行。

## 项目结构

```
src/main/java/com/example/parallel/
├── MainApplication.java           # 主入口程序
├── BasicParallelExecution.java    # 基础并行执行示例
├── CompletableFutureExample.java  # CompletableFuture异步并行示例
├── ForkJoinExample.java          # Fork/Join框架示例
└── NestedParallelExample.java    # 嵌套并行执行示例
```

## 功能特性

### 1. 基础并行执行 (BasicParallelExecution.java)
- 使用Thread直接创建线程
- ExecutorService线程池
- FixedThreadPool固定线程池
- Callable和Future获取异步结果

### 2. CompletableFuture异步并行 (CompletableFutureExample.java)
- 基础异步执行
- 组合多个异步任务
- 异步任务链
- 并行任务合并
- 异常处理和超时控制
- 实际应用场景示例

### 3. Fork/Join框架 (ForkJoinExample.java)
- RecursiveTask递归任务
- RecursiveAction递归动作
- 并行流处理
- 自定义ForkJoinPool
- 并行归并排序实现

### 4. 嵌套并行执行 (NestedParallelExample.java)
- 两层并行执行
- 并行任务中的并行流
- 复杂嵌套并行场景
- 实际应用：多数据源并行处理

## 环境要求

- Java 17 或更高版本
- Maven 3.6 或更高版本

## 构建和运行

### 1. 编译项目
```bash
mvn clean compile
```

### 2. 打包项目
```bash
mvn clean package
```

### 3. 运行示例

#### 方式1：使用Maven运行主程序
```bash
mvn exec:java -Dexec.mainClass="com.example.parallel.MainApplication"
```

#### 方式2：运行特定示例
```bash
# 运行基础并行执行示例
mvn exec:java -Dexec.mainClass="com.example.parallel.BasicParallelExecution"

# 运行CompletableFuture示例
mvn exec:java -Dexec.mainClass="com.example.parallel.CompletableFutureExample"

# 运行Fork/Join示例
mvn exec:java -Dexec.mainClass="com.example.parallel.ForkJoinExample"

# 运行嵌套并行示例
mvn exec:java -Dexec.mainClass="com.example.parallel.NestedParallelExample"
```

#### 方式3：运行JAR文件
```bash
java -jar target/java-parallel-execution-1.0-SNAPSHOT.jar
```

## 关键概念

### 主方法并行执行
主方法中可以通过以下方式实现并行执行：
1. **多线程**：创建多个Thread或使用线程池
2. **CompletableFuture**：异步编程模型
3. **Fork/Join**：分治算法框架
4. **并行流**：Stream API的并行处理

### 嵌套并行执行
在并行任务内部继续创建并行任务：
- 主任务并行执行
- 每个主任务内部有子任务并行执行
- 可以多层嵌套，但需要注意资源管理

### 最佳实践
1. **合理设置线程池大小**：根据CPU核心数和任务特性调整
2. **避免过度并行**：过多线程会导致上下文切换开销
3. **正确处理异常**：使用try-catch或CompletableFuture的异常处理机制
4. **资源管理**：确保线程池正确关闭
5. **选择合适的并行方式**：
   - CPU密集型任务：Fork/Join或固定线程池
   - I/O密集型任务：CachedThreadPool或CompletableFuture
   - 简单并行：并行流

## 性能考虑

1. **线程池配置**
   - CPU密集型：线程数 = CPU核心数
   - I/O密集型：线程数 = CPU核心数 * 2 或更多

2. **任务粒度**
   - 任务太小：并行开销大于收益
   - 任务太大：无法充分利用并行

3. **内存消耗**
   - 注意线程栈内存
   - 避免共享可变状态

## 注意事项

- 并行执行输出可能交错，这是正常现象
- 实际性能提升取决于硬件和任务特性
- 某些示例包含随机延迟以模拟真实场景

## 扩展阅读

- [Java Concurrency in Practice](https://jcip.net/)
- [Java并发编程实战](https://docs.oracle.com/javase/tutorial/essential/concurrency/)
- [Fork/Join框架](https://docs.oracle.com/javase/tutorial/essential/concurrency/forkjoin.html)
- [CompletableFuture指南](https://www.baeldung.com/java-completablefuture)