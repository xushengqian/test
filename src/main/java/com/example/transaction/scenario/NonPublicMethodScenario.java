package com.example.transaction.scenario;

import com.example.transaction.entity.TransactionLog;
import com.example.transaction.entity.UserAccount;
import com.example.transaction.repository.TransactionLogRepository;
import com.example.transaction.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 场景3: 方法不是 public 导致事务失效
 * 
 * 问题：@Transactional 注解只能作用在 public 方法上
 * 原因：Spring AOP 默认只能代理 public 方法，private、protected、package-private 方法无法被代理
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NonPublicMethodScenario {
    
    private final UserAccountRepository userAccountRepository;
    private final TransactionLogRepository transactionLogRepository;
    
    /**
     * 错误示例：private 方法上的事务注解不会生效
     */
    @Transactional
    private void transferPrivate(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("private 方法，事务不会生效");
        
        UserAccount fromAccount = userAccountRepository.findById(fromAccountId).orElse(null);
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        userAccountRepository.save(fromAccount);
        
        // 模拟异常，验证事务是否回滚
        if (amount.compareTo(new BigDecimal("100")) > 0) {
            throw new RuntimeException("转账金额过大");
        }
        
        UserAccount toAccount = userAccountRepository.findById(toAccountId).orElse(null);
        toAccount.setBalance(toAccount.getBalance().add(amount));
        userAccountRepository.save(toAccount);
    }
    
    /**
     * 错误示例：protected 方法上的事务注解不会生效
     */
    @Transactional
    protected void transferProtected(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("protected 方法，事务不会生效");
        
        UserAccount fromAccount = userAccountRepository.findById(fromAccountId).orElse(null);
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        userAccountRepository.save(fromAccount);
        
        if (amount.compareTo(new BigDecimal("100")) > 0) {
            throw new RuntimeException("转账金额过大");
        }
        
        UserAccount toAccount = userAccountRepository.findById(toAccountId).orElse(null);
        toAccount.setBalance(toAccount.getBalance().add(amount));
        userAccountRepository.save(toAccount);
    }
    
    /**
     * 错误示例：package-private 方法上的事务注解不会生效
     */
    @Transactional
    void transferPackagePrivate(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("package-private 方法，事务不会生效");
        
        UserAccount fromAccount = userAccountRepository.findById(fromAccountId).orElse(null);
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        userAccountRepository.save(fromAccount);
        
        if (amount.compareTo(new BigDecimal("100")) > 0) {
            throw new RuntimeException("转账金额过大");
        }
        
        UserAccount toAccount = userAccountRepository.findById(toAccountId).orElse(null);
        toAccount.setBalance(toAccount.getBalance().add(amount));
        userAccountRepository.save(toAccount);
    }
    
    /**
     * 正确解决方案：使用 public 方法
     */
    @Transactional
    public void transferCorrect(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("正确方案：public 方法，事务生效");
        
        UserAccount fromAccount = userAccountRepository.findById(fromAccountId).orElse(null);
        if (fromAccount == null) {
            throw new RuntimeException("转出账户不存在");
        }
        
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        userAccountRepository.save(fromAccount);
        
        if (amount.compareTo(new BigDecimal("100")) > 0) {
            throw new RuntimeException("转账金额过大");
        }
        
        UserAccount toAccount = userAccountRepository.findById(toAccountId).orElse(null);
        if (toAccount == null) {
            throw new RuntimeException("转入账户不存在");
        }
        
        toAccount.setBalance(toAccount.getBalance().add(amount));
        userAccountRepository.save(toAccount);
        
        // 记录交易日志
        TransactionLog transactionLog = new TransactionLog();
        transactionLog.setFromAccountId(fromAccountId);
        transactionLog.setToAccountId(toAccountId);
        transactionLog.setAmount(amount);
        transactionLog.setDescription("转账");
        transactionLogRepository.save(transactionLog);
    }
    
    /**
     * 提供公共接口调用私有方法（仍然无效）
     */
    public void callPrivateMethod(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("通过 public 方法调用 private 事务方法，事务仍然不会生效");
        transferPrivate(fromAccountId, toAccountId, amount);
    }
}