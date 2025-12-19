package com.example;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ConcurrentHashMap putIfAbsent 常见陷阱和最佳实践
 */
public class PutIfAbsentPitfalls {

    public static void main(String[] args) throws InterruptedException {
        demonstrateWrongPattern();
        demonstrateCorrectPattern();
        demonstratePutIfAbsentVsComputeIfAbsent();
    }

    /**
     * 错误示例：使用普通 HashMap + synchronized 或 check-then-act
     * 
     * 这种模式在多线程环境下是不安全的！
     */
    private static void demonstrateWrongPattern() throws InterruptedException {
        System.out.println("=== 错误模式演示 (请勿在生产代码中使用) ===");
        
        // 错误做法 1: 使用普通 HashMap（非线程安全）
        Map<String, AtomicInteger> unsafeMap = new HashMap<>();
        
        // 错误做法 2: check-then-act 模式（有竞态条件）
        // if (!map.containsKey(key)) {
        //     map.put(key, new Value());  // 另一个线程可能在检查和插入之间插入了值
        // }
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                // 这是错误的做法！可能导致数据丢失或覆盖
                String key = "counter";
                synchronized (unsafeMap) {  // 即使加锁，也不是最佳实践
                    if (!unsafeMap.containsKey(key)) {
                        unsafeMap.put(key, new AtomicInteger(0));
                    }
                }
                unsafeMap.get(key).incrementAndGet();
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        System.out.println("使用 synchronized HashMap 的结果: " + unsafeMap.get("counter"));
        System.out.println("(虽然结果可能正确，但这种方式效率低下且容易出错)\n");
    }

    /**
     * 正确示例：使用 ConcurrentHashMap 的 putIfAbsent 或 computeIfAbsent
     */
    private static void demonstrateCorrectPattern() throws InterruptedException {
        System.out.println("=== 正确模式演示 ===");
        
        ConcurrentHashMap<String, AtomicInteger> safeMap = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                // 正确做法：使用 computeIfAbsent
                AtomicInteger counter = safeMap.computeIfAbsent("counter", k -> new AtomicInteger(0));
                counter.incrementAndGet();
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        
        System.out.println("使用 ConcurrentHashMap.computeIfAbsent 的结果: " + safeMap.get("counter"));
        System.out.println("(这是线程安全且高效的方式)\n");
    }

    /**
     * 对比 putIfAbsent 和 computeIfAbsent 的区别
     */
    private static void demonstratePutIfAbsentVsComputeIfAbsent() {
        System.out.println("=== putIfAbsent vs computeIfAbsent ===");
        
        ConcurrentHashMap<String, ExpensiveResource> map1 = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, ExpensiveResource> map2 = new ConcurrentHashMap<>();
        
        // 预先放入一个值
        map1.put("key", new ExpensiveResource("existing"));
        map2.put("key", new ExpensiveResource("existing"));
        
        System.out.println("\n使用 putIfAbsent:");
        // putIfAbsent 的问题：即使键已存在，也会创建新对象
        ExpensiveResource newResource1 = new ExpensiveResource("new-for-putIfAbsent");
        ExpensiveResource result1 = map1.putIfAbsent("key", newResource1);
        System.out.println("返回值: " + result1);
        System.out.println("注意：新对象已经被创建了，即使没有被使用！");
        
        System.out.println("\n使用 computeIfAbsent:");
        // computeIfAbsent 的优势：只有在键不存在时才会执行 lambda
        ExpensiveResource result2 = map2.computeIfAbsent("key", k -> {
            System.out.println("这条消息不会打印，因为键已存在");
            return new ExpensiveResource("new-for-computeIfAbsent");
        });
        System.out.println("返回值: " + result2);
        System.out.println("注意：lambda 没有被执行，没有创建新对象！");
        
        System.out.println("\n=== 总结 ===");
        System.out.println("1. putIfAbsent: 值必须预先创建，返回旧值（如果存在）或 null");
        System.out.println("2. computeIfAbsent: 延迟创建值，返回现有值或新创建的值");
        System.out.println("3. 当值的创建成本较高时，优先使用 computeIfAbsent");
        System.out.println("4. 当值已经存在或创建成本很低时，可以使用 putIfAbsent");
    }

    static class ExpensiveResource {
        private final String name;
        private static final AtomicInteger createCount = new AtomicInteger(0);
        
        ExpensiveResource(String name) {
            this.name = name;
            int count = createCount.incrementAndGet();
            System.out.println("  -> 创建 ExpensiveResource: " + name + " (总共创建了 " + count + " 个)");
        }
        
        @Override
        public String toString() {
            return "ExpensiveResource{" + name + "}";
        }
    }
}
