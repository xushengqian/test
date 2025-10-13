import java.time.Duration;

public class RedisExample {
    
    // 修复后的Redis setEx调用示例
    public void setExecutingKeyWithExpiration() {
        String executingKey = "executing_task_key";
        String value = "1"; // 或者其他需要存储的值
        
        // 方法1: 使用setEx，参数顺序为 (key, seconds, value)
        // 30分钟 = 30 * 60 = 1800秒
        redis.setEx(executingKey, 1800, value);
        
        // 方法2: 如果您的Redis客户端支持Duration，可能需要这样写
        // redis.setEx(executingKey, Duration.ofMinutes(30).getSeconds(), value);
        
        // 方法3: 使用set方法配合过期时间
        // redis.set(executingKey, value, Duration.ofMinutes(30));
    }
    
    // 原始代码（有问题的版本）
    public void originalProblematicCode() {
        // 这行代码有问题：
        // redis.setEx(executingKey, 1, Duration.ofMinutes(30));
        // 
        // 问题：
        // 1. setEx的第二个参数应该是秒数（long类型），不是值
        // 2. 第三个参数应该是要存储的值，不是Duration对象
        // 3. Duration.ofMinutes(30)返回的是Duration对象，不是Redis能存储的值
    }
}