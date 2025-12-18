import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HashMap多线程问题的解决方案示例
 */
public class SolutionExample {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== HashMap多线程问题的解决方案 ===\n");
        
        // 方案1: 使用ConcurrentHashMap（推荐）
        solution1_ConcurrentHashMap();
        
        Thread.sleep(500);
        
        // 方案2: 使用Collections.synchronizedMap()
        solution2_SynchronizedMap();
        
        Thread.sleep(500);
        
        // 方案3: 使用synchronized关键字
        solution3_Synchronized();
    }
    
    /**
     * 方案1: 使用ConcurrentHashMap（推荐）
     * 优点：性能好，支持高并发
     * 缺点：无
     */
    private static void solution1_ConcurrentHashMap() throws InterruptedException {
        System.out.println("【方案1】使用ConcurrentHashMap");
        System.out.println("----------------------------------------");
        
        Map<String, String> map = new ConcurrentHashMap<>();
        testMap(map, "ConcurrentHashMap");
    }
    
    /**
     * 方案2: 使用Collections.synchronizedMap()
     * 优点：简单，适用于所有Map实现
     * 缺点：性能较差，所有操作都需要同步
     */
    private static void solution2_SynchronizedMap() throws InterruptedException {
        System.out.println("\n【方案2】使用Collections.synchronizedMap()");
        System.out.println("----------------------------------------");
        
        Map<String, String> map = Collections.synchronizedMap(new HashMap<>());
        testMap(map, "SynchronizedMap");
    }
    
    /**
     * 方案3: 使用synchronized关键字
     * 优点：灵活，可以精确控制同步范围
     * 缺点：需要手动管理，容易出错
     */
    private static void solution3_Synchronized() throws InterruptedException {
        System.out.println("\n【方案3】使用synchronized关键字");
        System.out.println("----------------------------------------");
        
        Map<String, String> map = new HashMap<>();
        final Object lock = new Object();
        
        int threadCount = 10;
        int operationsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "key-" + threadId + "-" + j;
                        String value = "value-" + threadId + "-" + j;
                        
                        // 使用synchronized保护put操作
                        synchronized (lock) {
                            map.put(key, value);
                        }
                        
                        // 使用synchronized保护get操作
                        synchronized (lock) {
                            String retrieved = map.get(key);
                            if (retrieved == null) {
                                System.out.println("警告: get不到值！");
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
        
        System.out.println("操作完成！map大小: " + map.size());
    }
    
    /**
     * 测试Map的线程安全性
     */
    private static void testMap(Map<String, String> map, String mapType) throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 1000;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        final int[] errorCount = {0};
        
        // 启动写线程
        for (int i = 0; i < threadCount / 2; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "key-" + threadId + "-" + j;
                        String value = "value-" + threadId + "-" + j;
                        map.put(key, value);
                    }
                } catch (Exception e) {
                    synchronized (SolutionExample.class) {
                        errorCount[0]++;
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
                        String value = map.get(key);
                        // 注意：即使线程安全，读线程也可能在写线程之前执行
                        // 所以value可能为null，这不一定是问题
                    }
                } catch (Exception e) {
                    synchronized (SolutionExample.class) {
                        errorCount[0]++;
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        System.out.println("操作完成！");
        System.out.println("异常次数: " + errorCount[0]);
        System.out.println("最终map大小: " + map.size());
    }
}
