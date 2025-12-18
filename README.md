# HashMap多线程可见性问题演示

## 问题描述

在Java多线程环境下，使用`HashMap`进行`put`和`get`操作时，**一个线程put值后，另一个线程可能get不到值**。

## 原因分析

1. **HashMap不是线程安全的**
   - `HashMap`的设计没有考虑多线程并发访问
   - 内部实现没有同步机制

2. **内存可见性问题**
   - 一个线程对`HashMap`的修改可能不会立即被其他线程看到
   - 由于CPU缓存、指令重排序等原因，写操作可能对其他线程不可见

3. **竞态条件**
   - `put`和`get`操作不是原子性的
   - 多个线程同时操作可能导致数据不一致

4. **其他问题**
   - 可能导致`ConcurrentModificationException`
   - 可能导致数据丢失或损坏
   - 可能导致无限循环（在JDK 1.7及之前版本）

## 示例代码

### 1. HashMapVisibilityDemo.java
演示HashMap在多线程环境下的可见性问题，对比普通HashMap和ConcurrentHashMap的行为。

### 2. HashMapVisibilityProblem.java
更直观地演示问题：线程A put值后，线程B立即get可能获取不到。

### 3. SolutionExample.java
展示三种解决方案：
- **方案1（推荐）**: 使用`ConcurrentHashMap`
- **方案2**: 使用`Collections.synchronizedMap()`
- **方案3**: 使用`synchronized`关键字

## 运行示例

```bash
# 编译
javac *.java

# 运行演示
java HashMapVisibilityDemo

# 运行问题演示
java HashMapVisibilityProblem

# 运行解决方案示例
java SolutionExample
```

## 解决方案

### 推荐方案：使用ConcurrentHashMap

```java
Map<String, String> map = new ConcurrentHashMap<>();
```

**优点**：
- 线程安全
- 性能好，支持高并发
- 不需要额外的同步机制

### 备选方案：使用Collections.synchronizedMap()

```java
Map<String, String> map = Collections.synchronizedMap(new HashMap<>());
```

**优点**：
- 线程安全
- 适用于所有Map实现

**缺点**：
- 性能较差，所有操作都需要同步
- 可能导致线程阻塞

### 备选方案：使用synchronized关键字

```java
Map<String, String> map = new HashMap<>();
synchronized (lock) {
    map.put(key, value);
}
```

**优点**：
- 灵活，可以精确控制同步范围

**缺点**：
- 需要手动管理，容易出错
- 性能较差

## 总结

**是的，Java多线程环境下使用HashMap的put和get操作，确实会导致一个线程put后，另一个线程get不到值的问题。**

**建议**：在多线程环境下，始终使用`ConcurrentHashMap`替代`HashMap`。
