package com.example;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ConcurrentHashMap putIfAbsent 方法演示
 * 
 * putIfAbsent 是一个原子操作，用于在键不存在时插入值。
 * 这在多线程环境下非常有用，可以避免 check-then-act 竞态条件。
 */
public class ConcurrentHashMapDemo {

    public static void main(String[] args) throws InterruptedException {
        demonstratePutIfAbsent();
        demonstrateComputeIfAbsent();
        demonstrateRaceConditionPrevention();
    }

    /**
     * 演示 putIfAbsent 的基本用法
     */
    private static void demonstratePutIfAbsent() {
        System.out.println("=== putIfAbsent 基本用法 ===");
        
        ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
        
        // 键不存在时，插入成功，返回 null
        String oldValue1 = map.putIfAbsent("key1", "value1");
        System.out.println("第一次 putIfAbsent 返回值: " + oldValue1); // null
        System.out.println("map.get(\"key1\"): " + map.get("key1")); // value1
        
        // 键已存在时，不会更新，返回现有值
        String oldValue2 = map.putIfAbsent("key1", "newValue");
        System.out.println("第二次 putIfAbsent 返回值: " + oldValue2); // value1
        System.out.println("map.get(\"key1\"): " + map.get("key1")); // value1 (未改变)
        
        System.out.println();
    }

    /**
     * 演示 computeIfAbsent 的用法（延迟计算）
     * 
     * 与 putIfAbsent 不同，computeIfAbsent 只在键不存在时才计算值，
     * 这对于创建成本较高的对象很有用。
     */
    private static void demonstrateComputeIfAbsent() {
        System.out.println("=== computeIfAbsent 延迟计算 ===");
        
        ConcurrentHashMap<String, ExpensiveObject> cache = new ConcurrentHashMap<>();
        
        // 使用 computeIfAbsent 实现延迟初始化
        ExpensiveObject obj1 = cache.computeIfAbsent("key1", k -> {
            System.out.println("正在创建 ExpensiveObject for " + k);
            return new ExpensiveObject(k);
        });
        
        // 第二次调用不会重新创建对象
        ExpensiveObject obj2 = cache.computeIfAbsent("key1", k -> {
            System.out.println("这条消息不会打印");
            return new ExpensiveObject(k);
        });
        
        System.out.println("obj1 == obj2: " + (obj1 == obj2)); // true
        System.out.println();
    }

    /**
     * 演示如何使用 putIfAbsent 防止竞态条件
     * 
     * 错误做法：先检查再插入（check-then-act）
     * 正确做法：使用 putIfAbsent 原子操作
     */
    private static void demonstrateRaceConditionPrevention() throws InterruptedException {
        System.out.println("=== 多线程计数器示例 ===");
        
        ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        // 模拟多个线程同时增加计数器
        for (int i = 0; i < 1000; i++) {
            executor.submit(() -> {
                // 正确做法：使用 computeIfAbsent 确保原子性
                AtomicInteger counter = counters.computeIfAbsent("visits", k -> new AtomicInteger(0));
                counter.incrementAndGet();
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        System.out.println("最终计数: " + counters.get("visits").get()); // 应该是 1000
        System.out.println();
        
        // 演示 putIfAbsent 的另一种用法
        demonstratePutIfAbsentPattern();
    }

    /**
     * 演示 putIfAbsent 的惯用模式
     */
    private static void demonstratePutIfAbsentPattern() {
        System.out.println("=== putIfAbsent 惯用模式 ===");
        
        ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
        
        String key = "config";
        String newValue = "defaultConfig";
        
        // 惯用模式：获取现有值或使用新值
        String existingValue = map.putIfAbsent(key, newValue);
        String actualValue = (existingValue != null) ? existingValue : newValue;
        
        System.out.println("实际使用的值: " + actualValue);
        
        // 或者更简洁地使用 Java 8 的 getOrDefault + putIfAbsent
        String value = map.computeIfAbsent("anotherKey", k -> "computedValue");
        System.out.println("computeIfAbsent 返回值: " + value);
    }

    /**
     * 模拟一个创建成本较高的对象
     */
    static class ExpensiveObject {
        private final String name;
        
        ExpensiveObject(String name) {
            this.name = name;
            // 模拟耗时操作
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        @Override
        public String toString() {
            return "ExpensiveObject{name='" + name + "'}";
        }
    }
}
