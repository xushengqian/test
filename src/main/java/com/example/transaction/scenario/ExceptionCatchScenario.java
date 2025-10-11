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
 * 场景2: 异常被捕获导致事务不回滚
 * 
 * 问题：事务方法中抛出的异常被 try-catch 捕获，事务不会回滚
 * 原因：Spring 默认只对运行时异常和 Error 进行回滚，且异常必须抛出到事务边界外
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExceptionCatchScenario {
    
    private final UserAccountRepository userAccountRepository;
    private final TransactionLogRepository transactionLogRepository;
    
    /**
     * 错误示例：异常被捕获，事务不会回滚
     */
    @Transactional
    public void transferWithCatch(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("转账操作，异常被捕获");
        
        try {
            // 扣减转出账户余额
            UserAccount fromAccount = userAccountRepository.findById(fromAccountId).orElse(null);
            if (fromAccount == null) {
                throw new RuntimeException("转出账户不存在");
            }
            
            fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
            userAccountRepository.save(fromAccount);
            
            // 模拟业务异常
            if (amount.compareTo(new BigDecimal("100")) > 0) {
                throw new RuntimeException("转账金额过大");
            }
            
            // 增加转入账户余额
            UserAccount toAccount = userAccountRepository.findById(toAccountId).orElse(null);
            if (toAccount == null) {
                throw new RuntimeException("转入账户不存在");
            }
            
            toAccount.setBalance(toAccount.getBalance().add(amount));
            userAccountRepository.save(toAccount);
            
        } catch (Exception e) {
            log.error("转账失败：{}", e.getMessage());
            // 异常被捕获，事务不会回滚！
            // 此时数据库中的数据可能处于不一致状态
        }
    }
    
    /**
     * 正确解决方案1：重新抛出异常
     */
    @Transactional
    public void transferCorrect1(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("正确方案1：重新抛出异常");
        
        try {
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
            
        } catch (Exception e) {
            log.error("转账失败：{}", e.getMessage());
            // 重新抛出异常，触发事务回滚
            throw e;
        }
    }
    
    /**
     * 正确解决方案2：使用 TransactionAspectSupport 手动回滚
     */
    @Transactional
    public void transferCorrect2(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("正确方案2：手动标记回滚");
        
        try {
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
            
        } catch (Exception e) {
            log.error("转账失败：{}", e.getMessage());
            // 手动标记事务回滚
            org.springframework.transaction.interceptor.TransactionAspectSupport
                    .currentTransactionStatus().setRollbackOnly();
        }
    }
    
    /**
     * 正确解决方案3：指定回滚的异常类型
     */
    @Transactional(rollbackFor = Exception.class)
    public void transferCorrect3(Long fromAccountId, Long toAccountId, BigDecimal amount) throws Exception {
        log.info("正确方案3：指定回滚异常类型");
        
        try {
            UserAccount fromAccount = userAccountRepository.findById(fromAccountId).orElse(null);
            if (fromAccount == null) {
                throw new Exception("转出账户不存在");
            }
            
            fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
            userAccountRepository.save(fromAccount);
            
            if (amount.compareTo(new BigDecimal("100")) > 0) {
                throw new Exception("转账金额过大");
            }
            
            UserAccount toAccount = userAccountRepository.findById(toAccountId).orElse(null);
            if (toAccount == null) {
                throw new Exception("转入账户不存在");
            }
            
            toAccount.setBalance(toAccount.getBalance().add(amount));
            userAccountRepository.save(toAccount);
            
        } catch (Exception e) {
            log.error("转账失败：{}", e.getMessage());
            // 抛出检查异常，由于指定了 rollbackFor = Exception.class，事务会回滚
            throw e;
        }
    }
}