package com.example.transaction.service;

import com.example.transaction.entity.User;
import com.example.transaction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 场景5: 事务传播行为设置不当导致事务失效
 * 
 * Spring提供了7种事务传播行为，错误的配置可能导致事务不按预期工作。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PropagationService {
    
    private final UserRepository userRepository;
    
    @Autowired
    private PropagationService self;
    
    /**
     * REQUIRED（默认）：如果当前存在事务，则加入该事务；如果当前没有事务，则创建一个新的事务
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void requiredPropagation(String username) {
        log.info("REQUIRED: 默认传播行为");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        if ("error".equals(username)) {
            throw new RuntimeException("REQUIRED异常 - 会回滚");
        }
    }
    
    /**
     * SUPPORTS：如果当前存在事务，则加入该事务；如果当前没有事务，则以非事务方式执行
     * 错误场景：单独调用时不会开启事务
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public void supportsPropagation(String username) {
        log.info("SUPPORTS: 如果没有事务则以非事务方式执行");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        if ("error".equals(username)) {
            // 如果单独调用，没有事务，数据不会回滚
            throw new RuntimeException("SUPPORTS异常 - 单独调用时不会回滚");
        }
    }
    
    /**
     * NOT_SUPPORTED：以非事务方式执行，如果当前存在事务，则挂起当前事务
     * 错误场景：总是以非事务方式执行，数据不会回滚
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void notSupportedPropagation(String username) {
        log.info("NOT_SUPPORTED: 总是以非事务方式执行");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        if ("error".equals(username)) {
            // 非事务执行，数据不会回滚
            throw new RuntimeException("NOT_SUPPORTED异常 - 不会回滚");
        }
    }
    
    /**
     * NEVER：以非事务方式执行，如果当前存在事务，则抛出异常
     * 错误场景：在事务环境中调用会抛出异常
     */
    @Transactional(propagation = Propagation.NEVER)
    public void neverPropagation(String username) {
        log.info("NEVER: 不能在事务中执行");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        // 如果在事务中调用此方法，会直接抛出异常
    }
    
    /**
     * REQUIRES_NEW：创建一个新的事务，如果当前存在事务，则挂起当前事务
     * 场景：内部事务独立于外部事务
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requiresNewPropagation(String username) {
        log.info("REQUIRES_NEW: 创建新的独立事务");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        if ("error".equals(username)) {
            throw new RuntimeException("REQUIRES_NEW异常 - 只影响当前事务");
        }
    }
    
    /**
     * MANDATORY：如果当前存在事务，则加入该事务；如果当前没有事务，则抛出异常
     * 错误场景：单独调用会抛出异常
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void mandatoryPropagation(String username) {
        log.info("MANDATORY: 必须在事务中执行");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        // 如果单独调用（没有事务），会抛出异常
    }
    
    /**
     * NESTED：如果当前存在事务，则创建一个事务作为当前事务的嵌套事务来运行
     * 如果当前没有事务，则等价于REQUIRED
     */
    @Transactional(propagation = Propagation.NESTED)
    public void nestedPropagation(String username) {
        log.info("NESTED: 嵌套事务");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        if ("error".equals(username)) {
            throw new RuntimeException("NESTED异常");
        }
    }
    
    /**
     * 演示错误场景：外层方法无事务，内层MANDATORY会失败
     */
    public void callMandatoryWithoutTransaction(String username) {
        log.info("无事务调用MANDATORY方法");
        try {
            // 这里会抛出异常，因为MANDATORY要求必须在事务中执行
            self.mandatoryPropagation(username);
        } catch (Exception e) {
            log.error("MANDATORY失败: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 演示错误场景：外层方法有事务，内层NEVER会失败
     */
    @Transactional
    public void callNeverWithTransaction(String username) {
        log.info("在事务中调用NEVER方法");
        try {
            // 这里会抛出异常，因为NEVER不能在事务中执行
            self.neverPropagation(username);
        } catch (Exception e) {
            log.error("NEVER失败: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * 演示REQUIRES_NEW的独立性
     */
    @Transactional
    public void demonstrateRequiresNew(String username) {
        log.info("演示REQUIRES_NEW的独立性");
        
        // 外层事务保存用户1
        User user1 = User.builder()
                .username(username + "_outer")
                .email(username + "_outer@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user1);
        
        try {
            // 调用REQUIRES_NEW方法，创建独立事务
            self.requiresNewPropagation(username + "_inner");
        } catch (Exception e) {
            log.error("内部事务失败，但不影响外部事务: {}", e.getMessage());
        }
        
        // 外层事务继续
        User user2 = User.builder()
                .username(username + "_outer2")
                .email(username + "_outer2@example.com")
                .age(30)
                .balance(2000.0)
                .build();
        userRepository.save(user2);
        
        // 如果内部事务失败，外层事务仍然可以成功
    }
    
    /**
     * 演示NOT_SUPPORTED挂起事务
     */
    @Transactional
    public void demonstrateNotSupported(String username) {
        log.info("演示NOT_SUPPORTED挂起事务");
        
        // 外层事务保存用户1
        User user1 = User.builder()
                .username(username + "_before")
                .email(username + "_before@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user1);
        
        try {
            // 调用NOT_SUPPORTED方法，挂起当前事务
            self.notSupportedPropagation(username + "_not_supported");
        } catch (Exception e) {
            log.error("NOT_SUPPORTED方法失败，数据已保存: {}", e.getMessage());
        }
        
        // 外层事务继续
        User user2 = User.builder()
                .username(username + "_after")
                .email(username + "_after@example.com")
                .age(30)
                .balance(2000.0)
                .build();
        userRepository.save(user2);
        
        if ("error".equals(username)) {
            // 外层事务失败，但NOT_SUPPORTED中的数据不会回滚
            throw new RuntimeException("外层事务失败");
        }
    }
}