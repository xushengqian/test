import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 演示 putIfAbsent 后 get 取不到值的问题
 */
public class ConcurrentMapIssue {
    
    /**
     * 问题示例：使用 HashMap（非线程安全）
     * 这会导致 putIfAbsent 后 get 可能取不到值
     */
    public static void demonstrateProblem() {
        System.out.println("=== 问题演示：使用 HashMap ===");
        Map<String, String> map = new HashMap<>();
        
        Thread thread1 = new Thread(() -> {
            String result = map.putIfAbsent("key1", "value1");
            System.out.println("Thread1 putIfAbsent 返回值: " + result);
            
            // 立即尝试获取
            String value = map.get("key1");
            System.out.println("Thread1 get 返回值: " + value);
        });
        
        Thread thread2 = new Thread(() -> {
            try {
                Thread.sleep(10); // 稍微延迟
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            String value = map.get("key1");
            System.out.println("Thread2 get 返回值: " + value);
        });
        
        thread1.start();
        thread2.start();
        
        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 解决方案：使用 ConcurrentHashMap（线程安全）
     */
    public static void demonstrateSolution() {
        System.out.println("\n=== 解决方案：使用 ConcurrentHashMap ===");
        Map<String, String> map = new ConcurrentHashMap<>();
        
        Thread thread1 = new Thread(() -> {
            String result = map.putIfAbsent("key1", "value1");
            System.out.println("Thread1 putIfAbsent 返回值: " + result);
            
            // 立即尝试获取
            String value = map.get("key1");
            System.out.println("Thread1 get 返回值: " + value);
        });
        
        Thread thread2 = new Thread(() -> {
            try {
                Thread.sleep(10); // 稍微延迟
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            String value = map.get("key1");
            System.out.println("Thread2 get 返回值: " + value);
        });
        
        thread1.start();
        thread2.start();
        
        try {
            thread1.join();
            thread2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 更详细的示例：展示 putIfAbsent 的正确用法
     */
    public static void demonstrateCorrectUsage() {
        System.out.println("\n=== 正确用法示例 ===");
        ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
        
        // putIfAbsent 的返回值说明：
        // - 如果 key 不存在，插入 value 并返回 null
        // - 如果 key 已存在，不插入，返回已存在的 value
        String result1 = map.putIfAbsent("key1", "value1");
        System.out.println("第一次 putIfAbsent('key1', 'value1') 返回: " + result1);
        
        String result2 = map.putIfAbsent("key1", "value2");
        System.out.println("第二次 putIfAbsent('key1', 'value2') 返回: " + result2);
        
        String value = map.get("key1");
        System.out.println("get('key1') 返回: " + value);
        
        // 多线程场景
        System.out.println("\n多线程场景：");
        Thread[] threads = new Thread[5];
        for (int i = 0; i < 5; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                String val = "value" + index;
                String prev = map.putIfAbsent("key2", val);
                if (prev == null) {
                    System.out.println("Thread " + index + " 成功插入: " + val);
                } else {
                    System.out.println("Thread " + index + " 键已存在，返回: " + prev);
                }
            });
        }
        
        for (Thread t : threads) {
            t.start();
        }
        
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        System.out.println("最终 get('key2') 返回: " + map.get("key2"));
    }
    
    public static void main(String[] args) {
        // 注意：HashMap 在多线程环境下可能不会每次都出现问题
        // 但这是不安全的，可能导致数据丢失或不一致
        demonstrateProblem();
        
        // 正确的做法
        demonstrateSolution();
        
        // 详细示例
        demonstrateCorrectUsage();
    }
}
