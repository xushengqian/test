package com.example.transaction.service;

import com.example.transaction.entity.User;
import com.example.transaction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * 场景3: 异常被捕获导致事务不回滚
 * 
 * Spring事务管理器只有在捕获到异常时才会回滚事务。
 * 如果异常在方法内部被try-catch捕获且没有重新抛出，事务将不会回滚。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExceptionHandlingService {
    
    private final UserRepository userRepository;
    
    /**
     * 错误示例：异常被捕获，事务不会回滚
     */
    @Transactional
    public void incorrectExceptionHandling(String username) {
        log.info("错误示例：异常被捕获，事务不会回滚");
        
        try {
            User user = User.builder()
                    .username(username)
                    .email(username + "@example.com")
                    .age(25)
                    .balance(1000.0)
                    .build();
            userRepository.save(user);
            
            // 业务逻辑异常
            if ("error".equals(username)) {
                throw new RuntimeException("业务异常");
            }
            
            // 继续其他操作
            user.setBalance(2000.0);
            userRepository.save(user);
            
        } catch (Exception e) {
            // 异常被捕获，没有重新抛出，事务不会回滚
            log.error("捕获异常但不抛出: {}", e.getMessage());
            // 这里数据已经保存，不会回滚
        }
    }
    
    /**
     * 正确示例1：捕获异常后重新抛出
     */
    @Transactional
    public void correctExceptionHandlingByRethrow(String username) {
        log.info("正确示例1：捕获异常后重新抛出");
        
        try {
            User user = User.builder()
                    .username(username)
                    .email(username + "@example.com")
                    .age(25)
                    .balance(1000.0)
                    .build();
            userRepository.save(user);
            
            if ("error".equals(username)) {
                throw new RuntimeException("业务异常");
            }
            
        } catch (Exception e) {
            log.error("捕获异常并重新抛出: {}", e.getMessage());
            // 重新抛出异常，让事务管理器感知到异常
            throw e;
        }
    }
    
    /**
     * 正确示例2：捕获异常后抛出新的运行时异常
     */
    @Transactional
    public void correctExceptionHandlingByNewException(String username) {
        log.info("正确示例2：捕获异常后抛出新的运行时异常");
        
        try {
            User user = User.builder()
                    .username(username)
                    .email(username + "@example.com")
                    .age(25)
                    .balance(1000.0)
                    .build();
            userRepository.save(user);
            
            if ("error".equals(username)) {
                throw new RuntimeException("原始业务异常");
            }
            
        } catch (Exception e) {
            log.error("捕获异常并抛出新异常: {}", e.getMessage());
            // 抛出新的运行时异常
            throw new RuntimeException("处理失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 正确示例3：手动标记事务回滚
     */
    @Transactional
    public void correctExceptionHandlingByManualRollback(String username) {
        log.info("正确示例3：手动标记事务回滚");
        
        try {
            User user = User.builder()
                    .username(username)
                    .email(username + "@example.com")
                    .age(25)
                    .balance(1000.0)
                    .build();
            userRepository.save(user);
            
            if ("error".equals(username)) {
                throw new RuntimeException("业务异常");
            }
            
        } catch (Exception e) {
            log.error("捕获异常并手动标记回滚: {}", e.getMessage());
            // 手动标记当前事务回滚
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            // 可以选择是否继续抛出异常
        }
    }
    
    /**
     * 错误示例：部分异常被捕获
     */
    @Transactional
    public void partialExceptionHandling(String username) {
        log.info("部分异常处理示例");
        
        // 第一次保存
        User user1 = User.builder()
                .username(username + "_1")
                .email(username + "_1@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user1);
        
        try {
            // 第二次保存（可能失败）
            User user2 = User.builder()
                    .username(username + "_2")
                    .email(username + "_2@example.com")
                    .age(30)
                    .balance(1500.0)
                    .build();
            userRepository.save(user2);
            
            if ("error".equals(username)) {
                throw new RuntimeException("第二次保存失败");
            }
        } catch (Exception e) {
            // 异常被捕获，第一次保存不会回滚
            log.error("第二次保存失败，但第一次保存已提交: {}", e.getMessage());
        }
        
        // 第三次保存
        User user3 = User.builder()
                .username(username + "_3")
                .email(username + "_3@example.com")
                .age(35)
                .balance(2000.0)
                .build();
        userRepository.save(user3);
        
        // 结果：user1和user3会保存成功，user2根据是否出错决定
    }
    
    /**
     * 嵌套try-catch的问题
     */
    @Transactional
    public void nestedTryCatch(String username) {
        log.info("嵌套try-catch示例");
        
        try {
            User user = User.builder()
                    .username(username)
                    .email(username + "@example.com")
                    .age(25)
                    .balance(1000.0)
                    .build();
            userRepository.save(user);
            
            try {
                // 内层try-catch
                if ("error".equals(username)) {
                    throw new RuntimeException("内层异常");
                }
            } catch (Exception innerEx) {
                // 内层异常被捕获
                log.error("内层异常被捕获: {}", innerEx.getMessage());
                // 如果这里不重新抛出，事务不会回滚
            }
            
            // 继续执行其他操作
            user.setAge(30);
            userRepository.save(user);
            
        } catch (Exception e) {
            // 外层异常处理
            log.error("外层异常: {}", e.getMessage());
            throw e;
        }
    }
}