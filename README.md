# ConcurrentHashMap putIfAbsent 示例

本项目演示了 Java `ConcurrentHashMap.putIfAbsent()` 方法的用法和特点。

## putIfAbsent 方法说明

`putIfAbsent(K key, V value)` 是 `ConcurrentHashMap` 提供的一个原子操作方法：

- **功能**: 如果指定的键尚未与值关联，则将其与给定值关联
- **返回值**: 
  - 如果键不存在，返回 `null`，并将键值对插入
  - 如果键已存在，返回现有的值，**不会替换**现有值
- **线程安全**: 这是一个原子操作，在多线程环境下是线程安全的

## 与 put 方法的区别

| 方法 | 键不存在时 | 键存在时 | 返回值 |
|------|-----------|---------|--------|
| `put(key, value)` | 插入键值对 | **替换**现有值 | 返回旧值（如果存在）或 null |
| `putIfAbsent(key, value)` | 插入键值对 | **不替换**现有值 | 返回现有值（如果存在）或 null |

## 使用场景

1. **单例模式**: 确保某个资源只被初始化一次
2. **缓存初始化**: 避免重复创建昂贵的对象
3. **计数器初始化**: 确保计数器只初始化一次
4. **线程安全的初始化**: 在多线程环境下安全地初始化共享资源

## 运行示例

```bash
javac ConcurrentHashMapPutIfAbsentExample.java
java ConcurrentHashMapPutIfAbsentExample
```

## 示例代码说明

示例代码包含以下场景：

1. **基本用法**: 演示 putIfAbsent 的基本行为
2. **多线程安全性**: 展示在多线程环境下的线程安全特性
3. **与 put 的区别**: 对比 putIfAbsent 和 put 的不同行为
4. **实际应用场景**: 演示在缓存初始化中的应用

## 注意事项

- `putIfAbsent` 是原子操作，但在某些复杂场景下，如果需要检查-然后-操作的逻辑，可能需要额外的同步机制
- 如果值本身是可变对象，即使 putIfAbsent 保证了键的唯一性，仍需要注意对象本身的线程安全性