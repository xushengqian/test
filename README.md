# Java for循环中使用线程池示例

本项目演示了在Java的for循环场景中如何使用线程池的多种常见方式。

## 示例说明

### 示例1: 基本用法
在for循环中提交任务到线程池，不需要等待结果。适用于异步执行任务的场景。

### 示例2: 使用Future获取结果
在for循环中提交任务并保存Future对象，后续可以通过Future获取每个任务的执行结果。

### 示例3: 等待所有任务完成
使用`ExecutorService.shutdown()`和`awaitTermination()`方法等待所有任务完成。

### 示例4: 使用CountDownLatch
使用`CountDownLatch`来精确控制等待所有任务完成的时机，可以统计总耗时。

### 示例5: 并行处理集合数据
实际应用场景：在for循环中并行处理集合中的每个元素。

## 编译和运行

```bash
javac ForLoopThreadPoolExample.java
java ForLoopThreadPoolExample
```

## 关键要点

1. **创建线程池**: 使用`Executors.newFixedThreadPool(n)`创建固定大小的线程池
2. **提交任务**: 在for循环中使用`executor.submit()`或`executor.execute()`提交任务
3. **获取结果**: 使用`Future.get()`获取任务执行结果（会阻塞）
4. **等待完成**: 
   - 使用`executor.shutdown()` + `awaitTermination()`等待所有任务完成
   - 或使用`CountDownLatch`进行更精确的控制
5. **关闭线程池**: 任务完成后记得关闭线程池，释放资源

## 注意事项

- 线程池大小应根据实际需求设置，不要过大或过小
- 记得处理异常情况（InterruptedException, ExecutionException）
- 任务完成后要关闭线程池，避免资源泄漏
- 如果任务可能抛出异常，要在任务内部处理或通过Future.get()捕获
