# Java 多线程 putIfAbsent 后 get 取不到值的问题

## 问题描述

在多线程环境下，一个线程使用 `putIfAbsent` 后，立即 `get` 可能取不到值。

## 问题原因

1. **使用了非线程安全的 Map**：如果使用 `HashMap` 而不是 `ConcurrentHashMap`，在多线程环境下会出现：
   - 数据不一致
   - 内存可见性问题
   - 可能导致数据丢失

2. **内存可见性问题**：即使在同一线程中，如果没有正确的同步机制，其他线程可能看不到更新后的值。

## 解决方案

### 1. 使用 ConcurrentHashMap（推荐）

```java
import java.util.concurrent.ConcurrentHashMap;

ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();

// 线程安全
String result = map.putIfAbsent("key", "value");
String value = map.get("key"); // 一定能取到值
```

### 2. 使用同步机制

如果必须使用 HashMap，需要手动同步：

```java
Map<String, String> map = new HashMap<>();
synchronized(map) {
    map.putIfAbsent("key", "value");
    String value = map.get("key");
}
```

**注意**：这种方式性能较差，不推荐。

## putIfAbsent 方法说明

`putIfAbsent` 方法的行为：
- **如果 key 不存在**：插入 value，返回 `null`
- **如果 key 已存在**：不插入，返回已存在的 value

```java
ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();

String result1 = map.putIfAbsent("key", "value1");  // 返回 null（插入成功）
String result2 = map.putIfAbsent("key", "value2");  // 返回 "value1"（已存在，不插入）
String value = map.get("key");                      // 返回 "value1"
```

## 最佳实践

1. **多线程环境必须使用 ConcurrentHashMap**
2. **不要使用 HashMap 或 Hashtable**（Hashtable 虽然线程安全，但性能差）
3. **理解 putIfAbsent 的返回值**：null 表示插入成功，非 null 表示键已存在

## 运行示例

编译并运行 `ConcurrentMapIssue.java` 查看问题演示和解决方案：

```bash
javac ConcurrentMapIssue.java
java ConcurrentMapIssue
```
