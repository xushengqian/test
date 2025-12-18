import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * 更直观地演示HashMap的可见性问题
 * 
 * 场景：线程A put一个值，线程B立即get这个值
 * 问题：线程B可能get不到值（即使线程A已经put了）
 */
public class HashMapVisibilityProblem {
    
    private static Map<String, String> map = new HashMap<>();
    private static volatile boolean flag = false; // 用于同步
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 演示HashMap可见性问题 ===\n");
        
        // 重复测试多次，增加观察到问题的概率
        for (int test = 1; test <= 10; test++) {
            map.clear();
            flag = false;
            
            CountDownLatch latch = new CountDownLatch(2);
            
            // 线程A：写入值
            Thread writer = new Thread(() -> {
                try {
                    // 等待读取线程准备好
                    while (!flag) {
                        Thread.yield();
                    }
                    
                    // 写入值
                    map.put("test-key", "test-value");
                    System.out.println("线程A: 已put值 'test-value'");
                } finally {
                    latch.countDown();
                }
            });
            
            // 线程B：读取值
            Thread reader = new Thread(() -> {
                try {
                    flag = true; // 通知写入线程可以开始
                    
                    // 立即尝试读取
                    String value = map.get("test-key");
                    
                    if (value == null) {
                        System.out.println("线程B: get不到值！(可见性问题)");
                    } else {
                        System.out.println("线程B: 成功get到值: " + value);
                    }
                } finally {
                    latch.countDown();
                }
            });
            
            reader.start();
            writer.start();
            
            latch.await();
            System.out.println("---");
        }
        
        System.out.println("\n说明：");
        System.out.println("1. HashMap不是线程安全的，存在内存可见性问题");
        System.out.println("2. 一个线程的修改可能不会立即被其他线程看到");
        System.out.println("3. 即使线程A已经put了值，线程B也可能get不到");
        System.out.println("4. 解决方案：使用ConcurrentHashMap或Collections.synchronizedMap()");
    }
}
