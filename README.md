# 线程池线程数设置指南

本仓库提供线程池大小设置的最佳实践和代码示例。

## 内容

- [线程池线程数设置指南](./thread-pool-sizing-guide.md) - 详细的设置原则、Java代码示例和Spring Boot配置

## 快速参考

| 任务类型 | 推荐线程数 |
|----------|-----------|
| CPU 密集型 | CPU核心数 + 1 |
| IO 密集型 | CPU核心数 × 2 |
| IO 密集型（精确） | CPU核心数 × (1 + 等待时间/计算时间) |

## 获取CPU核心数

```java
int cpuCores = Runtime.getRuntime().availableProcessors();
```