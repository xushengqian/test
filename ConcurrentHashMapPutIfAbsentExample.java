import java.util.concurrent.ConcurrentHashMap;

/**
 * ConcurrentHashMap putIfAbsent 方法示例
 * 
 * putIfAbsent 方法的特点：
 * 1. 如果指定的键尚未与值关联，则将其与给定值关联
 * 2. 如果键已存在，则返回现有值，不替换
 * 3. 这是一个原子操作，线程安全
 */
public class ConcurrentHashMapPutIfAbsentExample {
    
    public static void main(String[] args) {
        // 创建 ConcurrentHashMap
        ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
        
        // 示例 1: putIfAbsent 基本用法
        System.out.println("=== 示例 1: putIfAbsent 基本用法 ===");
        String result1 = map.putIfAbsent("key1", "value1");
        System.out.println("putIfAbsent(\"key1\", \"value1\") 返回: " + result1); // null，因为键不存在
        System.out.println("map.get(\"key1\"): " + map.get("key1")); // value1
        
        String result2 = map.putIfAbsent("key1", "value2");
        System.out.println("putIfAbsent(\"key1\", \"value2\") 返回: " + result2); // value1，返回现有值
        System.out.println("map.get(\"key1\"): " + map.get("key1")); // value1，值未被替换
        
        // 示例 2: 多线程场景下的线程安全性
        System.out.println("\n=== 示例 2: 多线程场景下的线程安全性 ===");
        ConcurrentHashMap<String, Integer> counterMap = new ConcurrentHashMap<>();
        
        // 模拟多个线程同时尝试初始化同一个键
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                Integer existing = counterMap.putIfAbsent("counter", 0);
                if (existing == null) {
                    System.out.println("线程 " + threadId + ": 成功初始化 counter");
                } else {
                    System.out.println("线程 " + threadId + ": counter 已存在，值为 " + existing);
                }
            });
        }
        
        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        System.out.println("最终 counter 的值: " + counterMap.get("counter"));
        
        // 示例 3: putIfAbsent vs put 的区别
        System.out.println("\n=== 示例 3: putIfAbsent vs put 的区别 ===");
        ConcurrentHashMap<String, String> map2 = new ConcurrentHashMap<>();
        
        // 使用 put
        map2.put("key2", "original");
        map2.put("key2", "new");
        System.out.println("使用 put 后: " + map2.get("key2")); // new，值被替换
        
        // 使用 putIfAbsent
        ConcurrentHashMap<String, String> map3 = new ConcurrentHashMap<>();
        map3.put("key3", "original");
        map3.putIfAbsent("key3", "new");
        System.out.println("使用 putIfAbsent 后: " + map3.get("key3")); // original，值未被替换
        
        // 示例 4: 实际应用场景 - 单例模式或缓存初始化
        System.out.println("\n=== 示例 4: 实际应用场景 - 缓存初始化 ===");
        ConcurrentHashMap<String, ExpensiveObject> cache = new ConcurrentHashMap<>();
        
        String cacheKey = "expensive-resource";
        
        // 多个线程尝试获取或创建昂贵的对象
        for (int i = 0; i < 5; i++) {
            final int id = i;
            new Thread(() -> {
                ExpensiveObject obj = cache.putIfAbsent(cacheKey, new ExpensiveObject("资源-" + id));
                if (obj == null) {
                    System.out.println("线程 " + id + ": 创建了新对象");
                } else {
                    System.out.println("线程 " + id + ": 使用已存在的对象: " + obj.name);
                }
            }).start();
        }
        
        // 等待一下让线程执行
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        System.out.println("缓存中的对象: " + cache.get(cacheKey).name);
    }
    
    // 模拟一个昂贵的对象
    static class ExpensiveObject {
        String name;
        
        ExpensiveObject(String name) {
            this.name = name;
            // 模拟昂贵的初始化操作
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
