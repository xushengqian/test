# Java 并行执行示例

这个项目演示了Java中两种不同的并行执行方式：

## 1. 多个主方法并行执行

**文件**: `src/main/java/com/parallel/ParallelMainExecutor.java`

演示如何同时执行多个不同的主方法逻辑，每个主方法代表一个独立的业务流程：
- 数据处理主方法
- 网络服务主方法  
- 文件处理主方法
- 计算任务主方法

**特点**:
- 使用 `CompletableFuture` 实现异步并行执行
- 每个主方法在独立的线程中运行
- 支持超时控制和异常处理
- 可以等待所有主方法执行完成

## 2. 主方法内部逻辑并行执行

**文件**: `src/main/java/com/parallel/InternalParallelMain.java`

演示如何在单个主方法中实现多个任务的并行处理：

### 示例1: 并行处理数组数据
- 将大数组分块并行处理
- 使用 `CompletableFuture` 异步计算每个块
- 最后合并所有结果

### 示例2: 并行执行不同类型的任务
- 数据库查询模拟
- API调用模拟
- 文件处理模拟
- 缓存更新模拟

### 示例3: Fork/Join框架并行计算
- 使用 `RecursiveTask` 实现分治算法
- 自动任务分割和结果合并
- 适合CPU密集型计算任务

### 示例4: 流式并行处理
- 对比串行和并行流处理性能
- 演示 `parallelStream()` 的使用
- 展示性能提升效果

## 快速开始

### 编译项目
```bash
./compile.sh
```

### 运行示例

#### 1. 运行多个主方法并行执行示例
```bash
./run-parallel-main.sh
```

#### 2. 运行主方法内部逻辑并行执行示例
```bash
./run-internal-parallel.sh
```

### 手动运行
如果脚本无法执行，可以手动运行：

```bash
# 编译
javac -d out src/main/java/com/parallel/*.java

# 运行多个主方法并行执行
java -cp out com.parallel.ParallelMainExecutor

# 运行主方法内部逻辑并行执行
java -cp out com.parallel.InternalParallelMain
```

## 技术要点

### 并行执行技术
1. **CompletableFuture**: 异步编程和任务组合
2. **ExecutorService**: 线程池管理
3. **Fork/Join框架**: 分治算法并行化
4. **并行流**: 函数式并行处理

### 性能优化
- 合理设置线程池大小
- 避免过度并行化
- 注意任务粒度和开销
- 正确处理异常和超时

### 最佳实践
- 使用线程安全的数据结构
- 避免共享可变状态
- 合理使用同步机制
- 及时释放资源

## 项目结构
```
/workspace/
├── src/main/java/com/parallel/
│   ├── ParallelMainExecutor.java    # 多个主方法并行执行
│   └── InternalParallelMain.java    # 主方法内部逻辑并行执行
├── compile.sh                       # 编译脚本
├── run-parallel-main.sh             # 运行多主方法示例
├── run-internal-parallel.sh         # 运行内部并行示例
└── README.md                        # 项目说明
```

## 注意事项
- 确保Java版本支持所使用的并行API (建议Java 8+)
- 根据系统CPU核心数调整线程池大小
- 在生产环境中要考虑资源限制和监控