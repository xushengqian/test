package com.example.transaction.service;

import com.example.transaction.entity.User;
import com.example.transaction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 场景1: 非public方法使用@Transactional
 * 
 * Spring的事务是基于AOP代理实现的，而AOP代理只能拦截public方法。
 * 如果@Transactional注解应用在private、protected或package-visible方法上，
 * 事务将不会生效。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NonPublicMethodService {
    
    private final UserRepository userRepository;
    
    /**
     * 正确示例：public方法，事务会生效
     */
    @Transactional
    public void publicMethodWithTransaction(String username) {
        log.info("执行public方法，事务会生效");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        // 模拟异常，事务会回滚
        if ("error".equals(username)) {
            throw new RuntimeException("模拟异常 - public方法事务会回滚");
        }
    }
    
    /**
     * 错误示例：private方法，事务不会生效
     * 注意：这个方法需要通过public方法调用
     */
    @Transactional
    private void privateMethodWithTransaction(String username) {
        log.info("执行private方法，事务不会生效");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        // 即使抛出异常，由于事务不生效，数据也不会回滚
        if ("error".equals(username)) {
            throw new RuntimeException("模拟异常 - private方法事务不会回滚");
        }
    }
    
    /**
     * 错误示例：protected方法，事务不会生效
     */
    @Transactional
    protected void protectedMethodWithTransaction(String username) {
        log.info("执行protected方法，事务不会生效");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        if ("error".equals(username)) {
            throw new RuntimeException("模拟异常 - protected方法事务不会回滚");
        }
    }
    
    /**
     * 错误示例：package-visible方法，事务不会生效
     */
    @Transactional
    void packageMethodWithTransaction(String username) {
        log.info("执行package-visible方法，事务不会生效");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        if ("error".equals(username)) {
            throw new RuntimeException("模拟异常 - package方法事务不会回滚");
        }
    }
    
    /**
     * 用于测试的public方法，调用private方法
     */
    public void testPrivateMethod(String username) {
        try {
            privateMethodWithTransaction(username);
        } catch (Exception e) {
            log.error("捕获异常: {}", e.getMessage());
        }
    }
    
    /**
     * 用于测试的public方法，调用protected方法
     */
    public void testProtectedMethod(String username) {
        try {
            protectedMethodWithTransaction(username);
        } catch (Exception e) {
            log.error("捕获异常: {}", e.getMessage());
        }
    }
    
    /**
     * 用于测试的public方法，调用package方法
     */
    public void testPackageMethod(String username) {
        try {
            packageMethodWithTransaction(username);
        } catch (Exception e) {
            log.error("捕获异常: {}", e.getMessage());
        }
    }
}