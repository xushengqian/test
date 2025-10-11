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
import java.util.concurrent.CompletableFuture;

/**
 * 场景6: 异步调用导致事务失效
 * 
 * 问题：在事务方法中使用异步调用，异步任务不在同一个事务中
 * 原因：Spring 事务是基于 ThreadLocal 的，异步执行在不同线程中，无法共享事务上下文
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncCallScenario {
    
    private final UserAccountRepository userAccountRepository;
    private final TransactionLogRepository transactionLogRepository;
    
    /**
     * 错误示例：异步调用不在同一个事务中
     */
    @Transactional
    public void transferWithAsync(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("事务方法中使用异步调用");
        
        // 主线程中的数据库操作
        UserAccount fromAccount = userAccountRepository.findById(fromAccountId).orElse(null);
        if (fromAccount == null) {
            throw new RuntimeException("转出账户不存在");
        }
        
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        userAccountRepository.save(fromAccount);
        
        // 异步执行，不在同一个事务中！
        CompletableFuture.runAsync(() -> {
            try {
                log.info("异步任务开始执行");
                
                // 这个操作不在主事务中，即使主事务回滚，这个操作也不会回滚
                UserAccount toAccount = userAccountRepository.findById(toAccountId).orElse(null);
                if (toAccount != null) {
                    toAccount.setBalance(toAccount.getBalance().add(amount));
                    userAccountRepository.save(toAccount);
                }
                
                // 记录日志
                TransactionLog transactionLog = new TransactionLog();
                transactionLog.setFromAccountId(fromAccountId);
                transactionLog.setToAccountId(toAccountId);
                transactionLog.setAmount(amount);
                transactionLog.setDescription("异步转账");
                transactionLogRepository.save(transactionLog);
                
            } catch (Exception e) {
                log.error("异步任务执行失败: {}", e.getMessage());
            }
        });
        
        // 主线程继续执行
        if (amount.compareTo(new BigDecimal("100")) > 0) {
            // 主事务回滚，但异步任务已经执行或正在执行，不会回滚
            throw new RuntimeException("转账金额过大，主事务回滚");
        }
    }
    
    /**
     * 错误示例：使用 @Async 注解的异步方法
     */
    @Transactional
    public void transferWithAsyncMethod(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("调用 @Async 异步方法");
        
        UserAccount fromAccount = userAccountRepository.findById(fromAccountId).orElse(null);
        if (fromAccount == null) {
            throw new RuntimeException("转出账户不存在");
        }
        
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        userAccountRepository.save(fromAccount);
        
        // 调用异步方法，不在同一个事务中
        processTransferAsync(toAccountId, amount);
        
        if (amount.compareTo(new BigDecimal("100")) > 0) {
            throw new RuntimeException("转账金额过大");
        }
    }
    
    // @Async  // 异步方法，在不同线程中执行
    public void processTransferAsync(Long toAccountId, BigDecimal amount) {
        log.info("异步处理转账，线程: {}", Thread.currentThread().getName());
        
        // 这些操作不在主事务中
        UserAccount toAccount = userAccountRepository.findById(toAccountId).orElse(null);
        if (toAccount != null) {
            toAccount.setBalance(toAccount.getBalance().add(amount));
            userAccountRepository.save(toAccount);
        }
    }
    
    /**
     * 正确解决方案1：在同一个线程中完成所有事务操作
     */
    @Transactional
    public void transferCorrect1(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("正确方案1：同步执行所有事务操作");
        
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
        
        // 同步记录日志
        TransactionLog transactionLog = new TransactionLog();
        transactionLog.setFromAccountId(fromAccountId);
        transactionLog.setToAccountId(toAccountId);
        transactionLog.setAmount(amount);
        transactionLog.setDescription("同步转账");
        transactionLogRepository.save(transactionLog);
        
        log.info("所有操作在同一事务中完成");
    }
    
    /**
     * 正确解决方案2：事务完成后再进行异步操作
     */
    @Transactional
    public void transferCorrect2(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("正确方案2：事务完成后异步处理");
        
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
        
        log.info("主要业务逻辑完成，事务即将提交");
    }
    
    /**
     * 事务完成后的异步处理（应该在事务方法外调用）
     */
    public void afterTransactionAsyncProcess(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        CompletableFuture.runAsync(() -> {
            log.info("事务完成后的异步处理");
            
            // 记录审计日志、发送通知等非核心业务
            TransactionLog auditLog = new TransactionLog();
            auditLog.setFromAccountId(fromAccountId);
            auditLog.setToAccountId(toAccountId);
            auditLog.setAmount(amount);
            auditLog.setDescription("转账审计");
            transactionLogRepository.save(auditLog);
        });
    }
}