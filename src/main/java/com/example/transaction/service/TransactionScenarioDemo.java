package com.example.transaction.service;

import com.example.transaction.entity.UserAccount;
import com.example.transaction.repository.UserAccountRepository;
import com.example.transaction.scenario.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 事务失效场景演示
 * 应用启动后自动执行各种场景的测试
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionScenarioDemo implements CommandLineRunner {
    
    private final UserAccountRepository userAccountRepository;
    private final InternalMethodCallScenario internalMethodCallScenario;
    private final ExceptionCatchScenario exceptionCatchScenario;
    private final NonPublicMethodScenario nonPublicMethodScenario;
    private final PropagationScenario propagationScenario;
    private final DatabaseEngineScenario databaseEngineScenario;
    private final AsyncCallScenario asyncCallScenario;
    private final NonManagedBeanScenario nonManagedBeanScenario;
    
    @Override
    public void run(String... args) throws Exception {
        log.info("=== Spring 事务失效场景演示开始 ===");
        
        printInitialBalance();
        
        // 演示各种失效场景
        demonstrateScenarios();
        
        log.info("=== Spring 事务失效场景演示结束 ===");
    }
    
    private void demonstrateScenarios() {
        log.info("\n" + "=".repeat(80));
        log.info("开始演示各种事务失效场景");
        log.info("=".repeat(80));
        
        // 场景1：类内部方法调用
        demonstrateInternalMethodCall();
        
        // 场景2：异常被捕获
        demonstrateExceptionCatch();
        
        // 场景3：方法不是 public
        demonstrateNonPublicMethod();
        
        // 场景4：事务传播行为
        demonstratePropagation();
        
        // 场景5：数据库引擎
        demonstrateDatabaseEngine();
        
        // 场景6：异步调用
        demonstrateAsyncCall();
        
        // 场景7：非 Spring 管理
        demonstrateNonManagedBean();
        
        log.info("\n" + "=".repeat(80));
        log.info("所有场景演示完成，请查看上面的日志分析事务行为");
        log.info("=".repeat(80));
    }
    
    private void demonstrateInternalMethodCall() {
        log.info("\n--- 场景1：类内部方法调用导致事务失效 ---");
        resetBalance();
        
        try {
            // 错误示例：类内部调用，事务失效
            internalMethodCallScenario.transfer(1L, 2L, new BigDecimal("150"));
        } catch (Exception e) {
            log.error("类内部调用异常: {}", e.getMessage());
        }
        
        log.info("转账后余额（事务应该回滚，但可能没有回滚）:");
        printBalance(1L, 2L);
        
        resetBalance();
        
        try {
            // 正确示例：在调用方法上添加事务注解
            internalMethodCallScenario.transferCorrect2(1L, 2L, new BigDecimal("150"));
        } catch (Exception e) {
            log.error("正确方法异常: {}", e.getMessage());
        }
        
        log.info("正确方法转账后余额（事务应该回滚）:");
        printBalance(1L, 2L);
    }
    
    private void demonstrateExceptionCatch() {
        log.info("\n--- 场景2：异常被捕获导致事务不回滚 ---");
        resetBalance();
        
        try {
            // 错误示例：异常被捕获，事务不回滚
            exceptionCatchScenario.transferWithCatch(1L, 2L, new BigDecimal("150"));
        } catch (Exception e) {
            log.error("捕获异常方法异常: {}", e.getMessage());
        }
        
        log.info("异常被捕获后余额（事务可能没有回滚）:");
        printBalance(1L, 2L);
        
        resetBalance();
        
        try {
            // 正确示例：重新抛出异常
            exceptionCatchScenario.transferCorrect1(1L, 2L, new BigDecimal("150"));
        } catch (Exception e) {
            log.error("重新抛出异常方法异常: {}", e.getMessage());
        }
        
        log.info("重新抛出异常后余额（事务应该回滚）:");
        printBalance(1L, 2L);
    }
    
    private void demonstrateNonPublicMethod() {
        log.info("\n--- 场景3：非 public 方法导致事务失效 ---");
        resetBalance();
        
        try {
            // 通过 public 方法调用 private 方法
            nonPublicMethodScenario.callPrivateMethod(1L, 2L, new BigDecimal("150"));
        } catch (Exception e) {
            log.error("private 方法异常: {}", e.getMessage());
        }
        
        log.info("private 方法转账后余额（事务可能没有回滚）:");
        printBalance(1L, 2L);
        
        resetBalance();
        
        try {
            // 正确示例：public 方法
            nonPublicMethodScenario.transferCorrect(1L, 2L, new BigDecimal("150"));
        } catch (Exception e) {
            log.error("public 方法异常: {}", e.getMessage());
        }
        
        log.info("public 方法转账后余额（事务应该回滚）:");
        printBalance(1L, 2L);
    }
    
    private void demonstratePropagation() {
        log.info("\n--- 场景4：事务传播行为配置错误 ---");
        resetBalance();
        
        try {
            // 错误示例：NOT_SUPPORTED 传播行为
            propagationScenario.transferNotSupported(1L, 2L, new BigDecimal("150"));
        } catch (Exception e) {
            log.error("NOT_SUPPORTED 方法异常: {}", e.getMessage());
        }
        
        log.info("NOT_SUPPORTED 后余额（事务被挂起，不会回滚）:");
        printBalance(1L, 2L);
        
        resetBalance();
        
        try {
            // 正确示例：默认 REQUIRED 传播行为
            propagationScenario.transferCorrect1(1L, 2L, new BigDecimal("150"));
        } catch (Exception e) {
            log.error("REQUIRED 方法异常: {}", e.getMessage());
        }
        
        log.info("REQUIRED 后余额（事务应该回滚）:");
        printBalance(1L, 2L);
    }
    
    private void demonstrateDatabaseEngine() {
        log.info("\n--- 场景5：数据库引擎支持情况 ---");
        resetBalance();
        
        try {
            // 在支持事务的引擎中正常工作
            databaseEngineScenario.transferCorrect(1L, 2L, new BigDecimal("150"));
        } catch (Exception e) {
            log.error("数据库引擎方法异常: {}", e.getMessage());
        }
        
        log.info("支持事务的引擎转账后余额（事务应该回滚）:");
        printBalance(1L, 2L);
    }
    
    private void demonstrateAsyncCall() {
        log.info("\n--- 场景6：异步调用导致事务失效 ---");
        resetBalance();
        
        try {
            // 错误示例：异步调用
            asyncCallScenario.transferWithAsync(1L, 2L, new BigDecimal("150"));
            // 等待异步任务执行
            Thread.sleep(1000);
        } catch (Exception e) {
            log.error("异步调用方法异常: {}", e.getMessage());
        }
        
        log.info("异步调用后余额（异步部分可能不会回滚）:");
        printBalance(1L, 2L);
        
        resetBalance();
        
        try {
            // 正确示例：同步执行
            asyncCallScenario.transferCorrect1(1L, 2L, new BigDecimal("150"));
        } catch (Exception e) {
            log.error("同步执行方法异常: {}", e.getMessage());
        }
        
        log.info("同步执行后余额（事务应该回滚）:");
        printBalance(1L, 2L);
    }
    
    private void demonstrateNonManagedBean() {
        log.info("\n--- 场景7：非 Spring 管理的类 ---");
        resetBalance();
        
        try {
            // 错误示例：new 对象
            nonManagedBeanScenario.transferWithNewObject(1L, 2L, new BigDecimal("150"));
        } catch (Exception e) {
            log.error("new 对象方法异常: {}", e.getMessage());
        }
        
        log.info("new 对象转账后余额（事务可能没有回滚）:");
        printBalance(1L, 2L);
        
        resetBalance();
        
        try {
            // 正确示例：Spring 管理的 Bean
            nonManagedBeanScenario.transferCorrect1(1L, 2L, new BigDecimal("150"));
        } catch (Exception e) {
            log.error("Spring Bean 方法异常: {}", e.getMessage());
        }
        
        log.info("Spring Bean 转账后余额（事务应该回滚）:");
        printBalance(1L, 2L);
    }
    
    private void printInitialBalance() {
        log.info("初始账户余额:");
        userAccountRepository.findAll().forEach(account -> 
            log.info("账户 {} ({}): {} 元", account.getId(), account.getUsername(), account.getBalance())
        );
    }
    
    private void printBalance(Long account1Id, Long account2Id) {
        UserAccount account1 = userAccountRepository.findById(account1Id).orElse(null);
        UserAccount account2 = userAccountRepository.findById(account2Id).orElse(null);
        
        if (account1 != null && account2 != null) {
            log.info("账户 {} 余额: {} 元", account1Id, account1.getBalance());
            log.info("账户 {} 余额: {} 元", account2Id, account2.getBalance());
        }
    }
    
    private void resetBalance() {
        log.info("重置账户余额...");
        UserAccount account1 = userAccountRepository.findById(1L).orElse(null);
        UserAccount account2 = userAccountRepository.findById(2L).orElse(null);
        
        if (account1 != null) {
            account1.setBalance(new BigDecimal("1000.00"));
            userAccountRepository.save(account1);
        }
        
        if (account2 != null) {
            account2.setBalance(new BigDecimal("500.00"));
            userAccountRepository.save(account2);
        }
    }
}