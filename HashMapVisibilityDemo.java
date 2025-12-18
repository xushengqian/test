import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 演示HashMap在多线程环境下的可见性问题
 * 
 * 问题：一个线程put值后，另一个线程可能get不到值
 * 
 * 原因：
 * 1. HashMap不是线程安全的
 * 2. 内存可见性问题：一个线程的修改可能不会立即被其他线程看到
 * 3. 竞态条件：put和get操作不是原子性的
 */
public class HashMapVisibilityDemo {
    
    // 使用普通HashMap - 不安全
    private static Map<String, String> unsafeMap = new HashMap<>();
    
    // 使用ConcurrentHashMap - 线程安全
    private static Map<String, String> safeMap = new ConcurrentHashMap<>();
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 演示HashMap在多线程环境下的可见性问题 ===\n");
        
        // 测试1: 普通HashMap的可见性问题
        testHashMapVisibility();
        
        Thread.sleep(1000);
        
        // 测试2: ConcurrentHashMap的正确行为
        testConcurrentHashMap();
    }
    
    /**
     * 测试普通HashMap的可见性问题
     */
    private static void testHashMapVisibility() throws InterruptedException {
        System.out.println("【测试1】使用普通HashMap（线程不安全）");
        System.out.println("----------------------------------------");
        
        unsafeMap.clear();
        int threadCount = 10;
        int operationsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // 统计get不到值的次数
        final int[] missCount = {0};
        
        // 启动写线程
        for (int i = 0; i < threadCount / 2; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "key-" + threadId + "-" + j;
                        String value = "value-" + threadId + "-" + j;
                        unsafeMap.put(key, value);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 启动读线程
        for (int i = threadCount / 2; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        // 尝试读取其他线程可能写入的值
                        String key = "key-" + (threadId - threadCount / 2) + "-" + j;
                        String value = unsafeMap.get(key);
                        if (value == null) {
                            synchronized (HashMapVisibilityDemo.class) {
                                missCount[0]++;
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        System.out.println("操作完成！");
        System.out.println("读取时get不到值的次数: " + missCount[0]);
        System.out.println("（注意：这个数字可能为0，但HashMap仍然存在线程安全问题）\n");
    }
    
    /**
     * 测试ConcurrentHashMap的正确行为
     */
    private static void testConcurrentHashMap() throws InterruptedException {
        System.out.println("【测试2】使用ConcurrentHashMap（线程安全）");
        System.out.println("----------------------------------------");
        
        safeMap.clear();
        int threadCount = 10;
        int operationsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // 统计get不到值的次数
        final int[] missCount = {0};
        
        // 启动写线程
        for (int i = 0; i < threadCount / 2; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "key-" + threadId + "-" + j;
                        String value = "value-" + threadId + "-" + j;
                        safeMap.put(key, value);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 启动读线程
        for (int i = threadCount / 2; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "key-" + (threadId - threadCount / 2) + "-" + j;
                        String value = safeMap.get(key);
                        if (value == null) {
                            synchronized (HashMapVisibilityDemo.class) {
                                missCount[0]++;
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        System.out.println("操作完成！");
        System.out.println("读取时get不到值的次数: " + missCount[0]);
        System.out.println("（ConcurrentHashMap保证线程安全，但读线程可能在写线程写入之前读取）\n");
    }
}
