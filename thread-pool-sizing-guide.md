# 线程池线程数设置指南

## 1. 基本原则

线程池大小的设置需要根据任务类型来决定：

### 1.1 CPU 密集型任务

对于计算密集型任务（如加密、压缩、计算等），线程数应接近 CPU 核心数：

```
线程数 = CPU核心数 + 1
```

额外的一个线程是为了防止偶发的页缺失或其他原因导致的线程暂停。

### 1.2 IO 密集型任务

对于 IO 密集型任务（如网络请求、数据库操作、文件读写等），可以设置更多的线程：

```
线程数 = CPU核心数 × 2
```

或者使用更精确的公式：

```
线程数 = CPU核心数 × (1 + 等待时间/计算时间)
```

### 1.3 混合型任务

如果任务既有 CPU 密集又有 IO 操作，建议：
- 将任务拆分为 CPU 密集和 IO 密集两部分
- 分别使用不同的线程池处理

## 2. Java 代码示例

### 2.1 获取 CPU 核心数

```java
int cpuCores = Runtime.getRuntime().availableProcessors();
```

### 2.2 CPU 密集型线程池

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CpuIntensiveThreadPool {
    
    public static ExecutorService createThreadPool() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int poolSize = cpuCores + 1;
        
        return Executors.newFixedThreadPool(poolSize);
    }
}
```

### 2.3 IO 密集型线程池

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IoIntensiveThreadPool {
    
    public static ExecutorService createThreadPool() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int poolSize = cpuCores * 2;
        
        return Executors.newFixedThreadPool(poolSize);
    }
    
    /**
     * 使用等待时间/计算时间比率来计算
     * @param waitTime 平均等待时间（毫秒）
     * @param computeTime 平均计算时间（毫秒）
     */
    public static ExecutorService createThreadPool(long waitTime, long computeTime) {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int poolSize = (int) (cpuCores * (1 + (double) waitTime / computeTime));
        
        return Executors.newFixedThreadPool(poolSize);
    }
}
```

### 2.4 自定义线程池（推荐）

```java
import java.util.concurrent.*;

public class CustomThreadPool {
    
    /**
     * 创建自定义线程池
     * 
     * @param corePoolSize    核心线程数
     * @param maxPoolSize     最大线程数
     * @param keepAliveTime   空闲线程存活时间
     * @param queueCapacity   任务队列容量
     */
    public static ThreadPoolExecutor createThreadPool(
            int corePoolSize,
            int maxPoolSize,
            long keepAliveTime,
            int queueCapacity) {
        
        return new ThreadPoolExecutor(
            corePoolSize,                          // 核心线程数
            maxPoolSize,                           // 最大线程数
            keepAliveTime,                         // 空闲线程存活时间
            TimeUnit.SECONDS,                      // 时间单位
            new LinkedBlockingQueue<>(queueCapacity),  // 任务队列
            Executors.defaultThreadFactory(),      // 线程工厂
            new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
        );
    }
    
    /**
     * CPU密集型任务线程池
     */
    public static ThreadPoolExecutor forCpuIntensive() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        return createThreadPool(
            cpuCores,           // 核心线程数
            cpuCores + 1,       // 最大线程数
            60L,                // 存活时间60秒
            100                 // 队列容量
        );
    }
    
    /**
     * IO密集型任务线程池
     */
    public static ThreadPoolExecutor forIoIntensive() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        return createThreadPool(
            cpuCores,           // 核心线程数
            cpuCores * 2,       // 最大线程数
            60L,                // 存活时间60秒
            200                 // 队列容量
        );
    }
}
```

## 3. Spring Boot 配置

### 3.1 application.yml 配置

```yaml
# 线程池配置
thread-pool:
  cpu-intensive:
    core-size: 4        # 或使用 ${AVAILABLE_PROCESSORS:4}
    max-size: 5
    queue-capacity: 100
    keep-alive: 60
  io-intensive:
    core-size: 8
    max-size: 16
    queue-capacity: 200
    keep-alive: 60
```

### 3.2 配置类

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {
    
    @Bean("cpuIntensiveExecutor")
    public Executor cpuIntensiveExecutor() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cpuCores);
        executor.setMaxPoolSize(cpuCores + 1);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("cpu-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        
        return executor;
    }
    
    @Bean("ioIntensiveExecutor")
    public Executor ioIntensiveExecutor() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cpuCores);
        executor.setMaxPoolSize(cpuCores * 2);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("io-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        
        return executor;
    }
}
```

## 4. 拒绝策略

当线程池和队列都满时，需要选择合适的拒绝策略：

| 策略 | 说明 |
|------|------|
| `AbortPolicy` | 默认策略，抛出 `RejectedExecutionException` |
| `CallerRunsPolicy` | 由调用线程执行任务，会降低提交速度 |
| `DiscardPolicy` | 静默丢弃任务 |
| `DiscardOldestPolicy` | 丢弃队列中最旧的任务 |

## 5. 监控与调优

### 5.1 监控指标

```java
ThreadPoolExecutor executor = (ThreadPoolExecutor) threadPool;

// 当前线程池中的线程数
int poolSize = executor.getPoolSize();

// 核心线程数
int corePoolSize = executor.getCorePoolSize();

// 活动线程数
int activeCount = executor.getActiveCount();

// 已完成任务数
long completedTaskCount = executor.getCompletedTaskCount();

// 队列中等待的任务数
int queueSize = executor.getQueue().size();
```

### 5.2 动态调整

```java
// 动态调整核心线程数
executor.setCorePoolSize(newCoreSize);

// 动态调整最大线程数
executor.setMaximumPoolSize(newMaxSize);
```

## 6. 最佳实践

1. **避免使用无界队列**：可能导致内存溢出
2. **合理设置拒绝策略**：根据业务需求选择
3. **给线程命名**：便于排查问题
4. **监控线程池状态**：及时发现问题
5. **优雅关闭线程池**：使用 `shutdown()` 而非 `shutdownNow()`
6. **分离不同类型的任务**：使用不同的线程池处理不同类型的任务

## 7. 经验公式总结

| 任务类型 | 推荐线程数 |
|----------|-----------|
| CPU 密集型 | N + 1 |
| IO 密集型 | 2N |
| IO 密集型（精确） | N × (1 + W/C) |
| 混合型 | 根据实际情况测试调整 |

> N = CPU 核心数  
> W = 等待时间  
> C = 计算时间
