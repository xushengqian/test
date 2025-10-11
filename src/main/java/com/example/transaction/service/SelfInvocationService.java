package com.example.transaction.service;

import com.example.transaction.entity.User;
import com.example.transaction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 场景2: 类内部方法调用导致事务失效
 * 
 * Spring的事务是基于AOP代理实现的。当一个类的方法调用同一个类的另一个方法时，
 * 不会经过代理，而是直接调用目标方法，导致事务注解失效。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SelfInvocationService {
    
    private final UserRepository userRepository;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired(required = false)
    private SelfInvocationService selfProxy;
    
    /**
     * 错误示例：直接调用本类的事务方法，事务不会生效
     */
    public void incorrectSelfInvocation(String username) {
        log.info("错误示例：直接调用本类事务方法");
        
        // 直接调用本类的事务方法，不会经过代理，事务不生效
        saveUserWithTransaction(username);
        
        // 即使saveUserWithTransaction方法标注了@Transactional，
        // 但因为是内部调用，事务不会生效
    }
    
    /**
     * 被内部调用的事务方法
     */
    @Transactional
    public void saveUserWithTransaction(String username) {
        log.info("执行事务方法 - saveUserWithTransaction");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        // 模拟异常
        if ("error".equals(username)) {
            throw new RuntimeException("模拟异常 - 内部调用时事务不会回滚");
        }
    }
    
    /**
     * 解决方案1：通过注入自身代理来调用
     */
    @Autowired
    public void setSelfProxy(SelfInvocationService selfProxy) {
        this.selfProxy = selfProxy;
    }
    
    public void correctSelfInvocationByProxy(String username) {
        log.info("正确示例1：通过自身代理调用事务方法");
        
        // 通过代理调用，事务会生效
        selfProxy.saveUserWithTransaction(username);
    }
    
    /**
     * 解决方案2：通过ApplicationContext获取代理对象
     */
    public void correctSelfInvocationByContext(String username) {
        log.info("正确示例2：通过ApplicationContext获取代理调用事务方法");
        
        // 从容器中获取代理对象
        SelfInvocationService proxy = applicationContext.getBean(SelfInvocationService.class);
        proxy.saveUserWithTransaction(username);
    }
    
    /**
     * 解决方案3：通过AopContext获取当前代理（需要开启exposeProxy=true）
     * 注意：需要在启动类或配置类上添加 @EnableAspectJAutoProxy(exposeProxy = true)
     */
    public void correctSelfInvocationByAopContext(String username) {
        log.info("正确示例3：通过AopContext获取当前代理调用事务方法");
        
        try {
            // 获取当前代理对象
            SelfInvocationService proxy = (SelfInvocationService) AopContext.currentProxy();
            proxy.saveUserWithTransaction(username);
        } catch (IllegalStateException e) {
            log.error("需要开启 @EnableAspectJAutoProxy(exposeProxy = true)");
            throw e;
        }
    }
    
    /**
     * 解决方案4：将事务逻辑放在当前方法中
     */
    @Transactional
    public void correctByDirectTransaction(String username) {
        log.info("正确示例4：直接在当前方法上添加事务注解");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        if ("error".equals(username)) {
            throw new RuntimeException("模拟异常 - 直接事务会回滚");
        }
    }
    
    /**
     * 演示嵌套调用的问题
     */
    public void nestedCallExample() {
        log.info("嵌套调用示例");
        methodA();
    }
    
    public void methodA() {
        log.info("执行methodA - 无事务");
        methodB(); // 直接调用，事务不生效
    }
    
    @Transactional
    public void methodB() {
        log.info("执行methodB - 标注了@Transactional但不会生效");
        
        User user = User.builder()
                .username("nested_user")
                .email("nested@example.com")
                .age(30)
                .balance(2000.0)
                .build();
        userRepository.save(user);
        
        throw new RuntimeException("嵌套调用中的异常 - 事务不会回滚");
    }
}