package com.example.transaction.scenario;

import com.example.transaction.entity.TransactionLog;
import com.example.transaction.entity.UserAccount;
import com.example.transaction.repository.TransactionLogRepository;
import com.example.transaction.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 场景4: 事务传播行为配置错误
 * 
 * 问题：错误的传播行为配置导致事务不按预期工作
 * 原因：不同的传播行为有不同的事务处理逻辑
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PropagationScenario {
    
    private final UserAccountRepository userAccountRepository;
    private final TransactionLogRepository transactionLogRepository;
    
    /**
     * 错误示例：使用 NOT_SUPPORTED 传播行为，事务被挂起
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void transferNotSupported(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("NOT_SUPPORTED 传播行为：事务被挂起，不会回滚");
        
        UserAccount fromAccount = userAccountRepository.findById(fromAccountId).orElse(null);
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        userAccountRepository.save(fromAccount);
        
        // 即使抛出异常，由于没有事务，数据不会回滚
        if (amount.compareTo(new BigDecimal("100")) > 0) {
            throw new RuntimeException("转账金额过大");
        }
        
        UserAccount toAccount = userAccountRepository.findById(toAccountId).orElse(null);
        toAccount.setBalance(toAccount.getBalance().add(amount));
        userAccountRepository.save(toAccount);
    }
    
    /**
     * 错误示例：使用 NEVER 传播行为，如果存在事务会抛出异常
     */
    @Transactional(propagation = Propagation.NEVER)
    public void transferNever(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("NEVER 传播行为：如果存在事务会抛出异常");
        
        UserAccount fromAccount = userAccountRepository.findById(fromAccountId).orElse(null);
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        userAccountRepository.save(fromAccount);
        
        UserAccount toAccount = userAccountRepository.findById(toAccountId).orElse(null);
        toAccount.setBalance(toAccount.getBalance().add(amount));
        userAccountRepository.save(toAccount);
    }
    
    /**
     * 问题示例：嵌套事务中使用 REQUIRES_NEW 可能导致数据不一致
     */
    @Transactional
    public void transferWithNestedRequiresNew(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("外层事务：REQUIRED 传播行为");
        
        // 扣减转出账户
        UserAccount fromAccount = userAccountRepository.findById(fromAccountId).orElse(null);
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        userAccountRepository.save(fromAccount);
        
        // 调用新事务方法
        try {
            transferInNewTransaction(toAccountId, amount);
        } catch (Exception e) {
            log.error("内层事务失败，但外层事务可能仍会提交: {}", e.getMessage());
            // 外层事务不会因为内层事务的异常而回滚（如果异常被捕获）
        }
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transferInNewTransaction(Long toAccountId, BigDecimal amount) {
        log.info("内层事务：REQUIRES_NEW 传播行为，独立事务");
        
        // 模拟内层事务异常
        if (amount.compareTo(new BigDecimal("50")) > 0) {
            throw new RuntimeException("内层事务异常");
        }
        
        UserAccount toAccount = userAccountRepository.findById(toAccountId).orElse(null);
        toAccount.setBalance(toAccount.getBalance().add(amount));
        userAccountRepository.save(toAccount);
        
        TransactionLog transactionLog = new TransactionLog();
        transactionLog.setToAccountId(toAccountId);
        transactionLog.setAmount(amount);
        transactionLog.setDescription("转入");
        transactionLogRepository.save(transactionLog);
    }
    
    /**
     * 正确解决方案1：使用默认的 REQUIRED 传播行为
     */
    @Transactional
    public void transferCorrect1(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("正确方案1：使用 REQUIRED 传播行为（默认）");
        
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
        
        TransactionLog transactionLog = new TransactionLog();
        transactionLog.setFromAccountId(fromAccountId);
        transactionLog.setToAccountId(toAccountId);
        transactionLog.setAmount(amount);
        transactionLog.setDescription("转账");
        transactionLogRepository.save(transactionLog);
    }
    
    /**
     * 正确解决方案2：合理使用 REQUIRES_NEW 并处理异常
     */
    @Transactional
    public void transferCorrect2(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("正确方案2：合理使用 REQUIRES_NEW 传播行为");
        
        UserAccount fromAccount = userAccountRepository.findById(fromAccountId).orElse(null);
        if (fromAccount == null) {
            throw new RuntimeException("转出账户不存在");
        }
        
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        userAccountRepository.save(fromAccount);
        
        // 使用新事务记录日志，即使主事务回滚，日志也会保存
        try {
            logTransactionInNewTransaction(fromAccountId, toAccountId, amount, "转账开始");
        } catch (Exception e) {
            log.warn("记录日志失败，继续执行主要业务: {}", e.getMessage());
        }
        
        if (amount.compareTo(new BigDecimal("100")) > 0) {
            throw new RuntimeException("转账金额过大，主事务将回滚");
        }
        
        UserAccount toAccount = userAccountRepository.findById(toAccountId).orElse(null);
        if (toAccount == null) {
            throw new RuntimeException("转入账户不存在");
        }
        
        toAccount.setBalance(toAccount.getBalance().add(amount));
        userAccountRepository.save(toAccount);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logTransactionInNewTransaction(Long fromAccountId, Long toAccountId, 
                                             BigDecimal amount, String description) {
        log.info("独立事务记录日志");
        
        TransactionLog transactionLog = new TransactionLog();
        transactionLog.setFromAccountId(fromAccountId);
        transactionLog.setToAccountId(toAccountId);
        transactionLog.setAmount(amount);
        transactionLog.setDescription(description);
        transactionLogRepository.save(transactionLog);
    }
}