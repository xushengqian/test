import java.time.Duration;
import redis.clients.jedis.Jedis;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisCorrectUsage {
    
    private Jedis jedis;
    private RedisTemplate<String, String> redisTemplate;
    private StringRedisTemplate stringRedisTemplate;
    
    /**
     * 使用Jedis客户端的正确方式
     */
    public void usingJedis() {
        String executingKey = "task:executing:" + System.currentTimeMillis();
        String value = "1"; // 表示任务正在执行
        
        // 方法1: 使用setex (注意是小写的setex)
        // 参数顺序: key, seconds, value
        jedis.setex(executingKey, 30 * 60, value); // 30分钟 = 1800秒
        
        // 方法2: 使用set方法配合EX参数
        jedis.set(executingKey, value, "EX", 30 * 60);
    }
    
    /**
     * 使用Spring RedisTemplate的正确方式
     */
    public void usingRedisTemplate() {
        String executingKey = "task:executing:" + System.currentTimeMillis();
        String value = "1";
        
        // 使用RedisTemplate设置键值和过期时间
        redisTemplate.opsForValue().set(executingKey, value, Duration.ofMinutes(30));
        
        // 或者分两步操作
        // redisTemplate.opsForValue().set(executingKey, value);
        // redisTemplate.expire(executingKey, Duration.ofMinutes(30));
    }
    
    /**
     * 使用StringRedisTemplate的正确方式
     */
    public void usingStringRedisTemplate() {
        String executingKey = "task:executing:" + System.currentTimeMillis();
        String value = "1";
        
        // StringRedisTemplate也支持Duration
        stringRedisTemplate.opsForValue().set(executingKey, value, Duration.ofMinutes(30));
    }
    
    /**
     * 您原始代码的问题分析和修复
     */
    public void fixOriginalCode() {
        String executingKey = "task:executing";
        
        // ❌ 错误的写法（您的原始代码）:
        // redis.setEx(executingKey, 1, Duration.ofMinutes(30));
        // 
        // 问题分析：
        // 1. setEx方法签名通常是: setEx(String key, long seconds, String value)
        // 2. 第二个参数应该是过期时间（秒），您写的是1秒
        // 3. 第三个参数应该是要存储的值，您写的是Duration对象
        
        // ✅ 正确的写法：
        if (jedis != null) {
            jedis.setex(executingKey, Duration.ofMinutes(30).toSeconds(), "1");
        }
        
        if (stringRedisTemplate != null) {
            stringRedisTemplate.opsForValue().set(executingKey, "1", Duration.ofMinutes(30));
        }
    }
    
    /**
     * 实际使用场景示例：防止重复执行
     */
    public boolean tryLockExecution(String taskId) {
        String executingKey = "task:executing:" + taskId;
        
        // 尝试设置锁，如果键不存在则设置成功，存在则失败
        String result = jedis.set(executingKey, "1", "NX", "EX", 30 * 60);
        
        return "OK".equals(result); // 返回true表示获取锁成功
    }
    
    /**
     * 释放执行锁
     */
    public void unlockExecution(String taskId) {
        String executingKey = "task:executing:" + taskId;
        jedis.del(executingKey);
    }
}