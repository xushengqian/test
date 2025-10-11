package com.example.transaction.scenario;

import com.example.transaction.entity.TransactionLog;
import com.example.transaction.entity.UserAccount;
import com.example.transaction.repository.TransactionLogRepository;
import com.example.transaction.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 场景1: 类内部方法调用导致事务失效
 * 
 * 问题：在同一个类中，一个非事务方法调用另一个事务方法，事务不会生效
 * 原因：Spring AOP 基于代理模式，类内部调用不经过代理，因此事务注解失效
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InternalMethodCallScenario {
    
    private final UserAccountRepository userAccountRepository;
    private final TransactionLogRepository transactionLogRepository;
    
    /**
     * 错误示例：非事务方法调用事务方法
     * 这种情况下，transferMoney 方法的 @Transactional 注解不会生效
     */
    public void transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("开始转账操作：从账户 {} 转账 {} 到账户 {}", fromAccountId, amount, toAccountId);
        
        // 类内部调用，不经过代理，事务失效！
        transferMoney(fromAccountId, toAccountId, amount);
    }
    
    @Transactional
    public void transferMoney(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        // 扣减转出账户余额
        UserAccount fromAccount = userAccountRepository.findById(fromAccountId).orElse(null);
        if (fromAccount == null) {
            throw new RuntimeException("转出账户不存在");
        }
        
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        userAccountRepository.save(fromAccount);
        
        // 模拟异常，验证事务是否回滚
        if (amount.compareTo(new BigDecimal("100")) > 0) {
            throw new RuntimeException("转账金额过大，触发异常");
        }
        
        // 增加转入账户余额
        UserAccount toAccount = userAccountRepository.findById(toAccountId).orElse(null);
        if (toAccount == null) {
            throw new RuntimeException("转入账户不存在");
        }
        
        toAccount.setBalance(toAccount.getBalance().add(amount));
        userAccountRepository.save(toAccount);
        
        // 记录交易日志
        TransactionLog log = new TransactionLog();
        log.setFromAccountId(fromAccountId);
        log.setToAccountId(toAccountId);
        log.setAmount(amount);
        log.setDescription("转账");
        transactionLogRepository.save(log);
    }
    
    /**
     * 正确解决方案1：通过自注入获取代理对象
     */
    // @Autowired
    // private InternalMethodCallScenario self;
    
    public void transferCorrect1(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("正确方案1：通过自注入调用事务方法");
        // self.transferMoney(fromAccountId, toAccountId, amount);
    }
    
    /**
     * 正确解决方案2：在调用方法上添加 @Transactional 注解
     */
    @Transactional
    public void transferCorrect2(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("正确方案2：在调用方法上添加事务注解");
        
        // 直接在当前方法中实现事务逻辑
        UserAccount fromAccount = userAccountRepository.findById(fromAccountId).orElse(null);
        if (fromAccount == null) {
            throw new RuntimeException("转出账户不存在");
        }
        
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        userAccountRepository.save(fromAccount);
        
        if (amount.compareTo(new BigDecimal("100")) > 0) {
            throw new RuntimeException("转账金额过大，触发异常");
        }
        
        UserAccount toAccount = userAccountRepository.findById(toAccountId).orElse(null);
        if (toAccount == null) {
            throw new RuntimeException("转入账户不存在");
        }
        
        toAccount.setBalance(toAccount.getBalance().add(amount));
        userAccountRepository.save(toAccount);
        
        TransactionLog transactionLog = new TransactionLog();
        transactionLog.setFromAccountId(fromAccountId);
        transactionLog.setToAccountId(toAccountId);
        transactionLog.setAmount(amount);
        transactionLog.setDescription("转账");
        transactionLogRepository.save(transactionLog);
    }
}