# JAVA 多线程任务队列（ThreadPoolExecutor + 有界队列）

这个仓库提供一个可直接运行的“多线程任务队列”示例：用 `ThreadPoolExecutor` + **有界** `BlockingQueue` 来实现排队与并发执行，并封装常用“队列设置/参数”。

## 如何运行

```bash
mvn -q -DskipTests package
java -jar target/java-multi-thread-queue-1.0.0.jar
```

## 你真正要设置的参数（经验值/取舍）

- **coreThreads / maxThreads**：并发度。
  - **CPU 密集**：`≈ CPU 核心数`（或 `核心数 + 1`）。
  - **IO 密集**：可以更大（比如 `2~8 * 核心数`），但要结合下游（DB/HTTP）限流。
- **queueCapacity（队列容量）**：积压上限。
  - 一定要有界，避免无限堆积导致 OOM。
  - 容量越大：抗瞬时峰值越强，但延迟更高、故障恢复更慢。
- **rejectionPolicy（拒绝策略/背压策略）**：队列满时怎么办。
  - `CALLER_RUNS`：推荐默认。把压力“反向传回提交方”，系统更稳定。
  - `ABORT`：直接失败，适合上游能重试/降级。
  - `DISCARD/DISCARD_OLDEST`：允许丢任务才用。
  - `BLOCK`：**不丢任务**且想强背压时用（提交线程会被阻塞）。
- **keepAlive**：非核心线程空闲回收时间（`maxThreads > coreThreads` 才更有意义）。
- **threadNamePrefix**：强烈建议设置，排查问题非常有用。

## 代码入口

- `com.example.taskqueue.TaskQueue`：队列封装与参数设置
- `com.example.taskqueue.Main`：提交任务/观察 backlog/优雅停机演示
