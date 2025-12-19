# ConcurrentHashMap putIfAbsent 示例

本项目演示了 Java `ConcurrentHashMap` 中 `putIfAbsent` 和 `computeIfAbsent` 方法的正确使用方式。

## 核心概念

### putIfAbsent 方法

```java
V putIfAbsent(K key, V value)
```

- **功能**：如果键不存在，则插入键值对
- **返回值**：如果键已存在，返回现有值；如果键不存在，返回 `null`
- **原子性**：整个操作是原子的，线程安全

### computeIfAbsent 方法

```java
V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction)
```

- **功能**：如果键不存在，则计算并插入新值
- **返回值**：返回现有值或新计算的值
- **优势**：延迟计算，只有在需要时才创建值

## 使用场景

### 1. 线程安全的缓存

```java
ConcurrentHashMap<String, ExpensiveObject> cache = new ConcurrentHashMap<>();

// 使用 computeIfAbsent 实现线程安全的缓存
ExpensiveObject obj = cache.computeIfAbsent(key, k -> loadFromDatabase(k));
```

### 2. 并发计数器

```java
ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

// 线程安全地获取或创建计数器
AtomicInteger counter = counters.computeIfAbsent("visits", k -> new AtomicInteger(0));
counter.incrementAndGet();
```

### 3. 单例模式

```java
ConcurrentHashMap<Class<?>, Object> singletons = new ConcurrentHashMap<>();

@SuppressWarnings("unchecked")
public static <T> T getSingleton(Class<T> clazz, Supplier<T> supplier) {
    return (T) singletons.computeIfAbsent(clazz, k -> supplier.get());
}
```

## 常见陷阱

### ❌ 错误：check-then-act 模式

```java
// 这是错误的！存在竞态条件
if (!map.containsKey(key)) {
    map.put(key, newValue);  // 另一个线程可能已经插入了值
}
```

### ✅ 正确：使用原子操作

```java
// 正确做法
map.putIfAbsent(key, newValue);
// 或
map.computeIfAbsent(key, k -> createValue(k));
```

### putIfAbsent vs computeIfAbsent

| 特性 | putIfAbsent | computeIfAbsent |
|------|-------------|-----------------|
| 值创建时机 | 调用前必须创建 | 延迟创建（键不存在时） |
| 返回值 | 旧值或 null | 现有值或新值 |
| 适用场景 | 值已存在或创建成本低 | 值创建成本高 |

## 运行示例

```bash
# 编译
javac -d target src/main/java/com/example/*.java

# 运行基础演示
java -cp target com.example.ConcurrentHashMapDemo

# 运行陷阱和最佳实践演示
java -cp target com.example.PutIfAbsentPitfalls
```

## 文件结构

```
src/main/java/com/example/
├── ConcurrentHashMapDemo.java    # 基础用法演示
└── PutIfAbsentPitfalls.java      # 常见陷阱和最佳实践
```
