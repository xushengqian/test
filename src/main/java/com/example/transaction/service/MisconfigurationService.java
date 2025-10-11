package com.example.transaction.service;

import com.example.transaction.entity.User;
import com.example.transaction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 场景6: 其他配置问题导致事务失效
 * 
 * 包括：数据源配置、多线程、数据库引擎等问题
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MisconfigurationService {
    
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    
    /**
     * 错误场景1：多线程环境下事务失效
     * Spring事务是基于ThreadLocal实现的，在新线程中不会传递事务上下文
     */
    @Transactional
    public void multiThreadTransaction(String username) {
        log.info("多线程事务示例 - 主线程事务: {}", 
                TransactionSynchronizationManager.isActualTransactionActive());
        
        // 主线程保存用户1
        User user1 = User.builder()
                .username(username + "_main")
                .email(username + "_main@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user1);
        
        // 在新线程中执行操作
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            log.info("子线程事务状态: {}", 
                    TransactionSynchronizationManager.isActualTransactionActive());
            
            // 子线程中的操作不在事务中
            User user2 = User.builder()
                    .username(username + "_thread")
                    .email(username + "_thread@example.com")
                    .age(30)
                    .balance(2000.0)
                    .build();
            userRepository.save(user2);
            
            if ("error".equals(username)) {
                throw new RuntimeException("子线程异常 - 不会影响主线程事务");
            }
        }, executorService);
        
        try {
            future.join();
        } catch (Exception e) {
            log.error("子线程执行失败: {}", e.getMessage());
        }
        
        // 主线程继续
        User user3 = User.builder()
                .username(username + "_main2")
                .email(username + "_main2@example.com")
                .age(35)
                .balance(3000.0)
                .build();
        userRepository.save(user3);
        
        if ("main_error".equals(username)) {
            // 主线程异常只会回滚主线程的操作，子线程的操作不受影响
            throw new RuntimeException("主线程异常");
        }
    }
    
    /**
     * 错误场景2：使用final修饰类导致代理失败
     * 如果类被final修饰，CGLIB无法创建代理类
     */
    public static final class FinalClassService {
        
        @Autowired
        private UserRepository userRepository;
        
        @Transactional
        public void saveUser(String username) {
            // 如果类是final的，Spring无法创建代理，事务不会生效
            log.warn("Final类的方法，事务可能不生效");
            
            User user = User.builder()
                    .username(username)
                    .email(username + "@example.com")
                    .age(25)
                    .balance(1000.0)
                    .build();
            // userRepository.save(user);
        }
    }
    
    /**
     * 错误场景3：数据库不支持事务
     * 例如MySQL的MyISAM引擎不支持事务
     */
    @Transactional
    public void unsupportedDatabaseEngine(String username) {
        log.info("数据库引擎不支持事务的场景");
        
        // 如果使用MyISAM引擎，即使有@Transactional也不会有事务效果
        // 这里使用H2数据库，支持事务，仅作演示
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        if ("error".equals(username)) {
            throw new RuntimeException("即使异常，MyISAM引擎也不会回滚");
        }
    }
    
    /**
     * 错误场景4：错误的隔离级别导致问题
     */
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public void wrongIsolationLevel(String username) {
        log.info("使用READ_UNCOMMITTED隔离级别 - 可能读到脏数据");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        // READ_UNCOMMITTED可能会读到其他事务未提交的数据
    }
    
    /**
     * 错误场景5：超时设置不当
     */
    @Transactional(timeout = 1) // 1秒超时
    public void transactionTimeout(String username) throws InterruptedException {
        log.info("事务超时设置示例");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        // 模拟长时间操作
        if ("timeout".equals(username)) {
            Thread.sleep(2000); // 睡眠2秒，超过超时时间
            // 事务会因为超时而回滚
        }
    }
    
    /**
     * 错误场景6：使用原生SQL时没有正确配置事务
     */
    @Transactional
    public void nativeSqlWithoutProperTransaction(String username) {
        log.info("使用原生SQL操作");
        
        // 使用JdbcTemplate执行原生SQL
        String sql = "INSERT INTO users (username, email, age, balance, created_at) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, username, username + "@example.com", 25, 1000.0, java.time.LocalDateTime.now());
        
        if ("error".equals(username)) {
            throw new RuntimeException("原生SQL操作异常");
        }
    }
    
    /**
     * 错误场景7：只读事务中进行写操作
     */
    @Transactional(readOnly = true)
    public void readOnlyTransaction(String username) {
        log.info("只读事务中尝试写操作");
        
        // 在只读事务中进行写操作可能会抛出异常或被忽略
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        
        try {
            userRepository.save(user); // 可能会失败
            log.warn("只读事务中的写操作可能不会生效");
        } catch (Exception e) {
            log.error("只读事务中不能进行写操作: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 检查事务是否激活
     */
    public void checkTransactionStatus() {
        boolean isInTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        String transactionName = TransactionSynchronizationManager.getCurrentTransactionName();
        boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        Integer isolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
        
        log.info("事务状态检查:");
        log.info("  - 是否在事务中: {}", isInTransaction);
        log.info("  - 事务名称: {}", transactionName);
        log.info("  - 是否只读: {}", isReadOnly);
        log.info("  - 隔离级别: {}", isolationLevel);
    }
    
    /**
     * 演示事务状态
     */
    @Transactional
    public void demonstrateTransactionStatus(String username) {
        log.info("演示事务状态:");
        checkTransactionStatus();
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
    }
}