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
 * 场景7: 类没有被 Spring 管理
 * 
 * 问题：直接 new 对象或者类没有被 Spring 容器管理，事务注解不会生效
 * 原因：Spring AOP 只能作用在 Spring 容器管理的 Bean 上
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NonManagedBeanScenario {
    
    private final UserAccountRepository userAccountRepository;
    private final TransactionLogRepository transactionLogRepository;
    
    /**
     * 演示非 Spring 管理的类
     */
    public static class NonManagedService {
        
        private UserAccountRepository userAccountRepository;
        private TransactionLogRepository transactionLogRepository;
        
        public NonManagedService(UserAccountRepository userAccountRepository, 
                               TransactionLogRepository transactionLogRepository) {
            this.userAccountRepository = userAccountRepository;
            this.transactionLogRepository = transactionLogRepository;
        }
        
        /**
         * 错误示例：非 Spring 管理的类，事务注解不生效
         */
        @Transactional
        public void transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
            log.info("非 Spring 管理的类，事务不会生效");
            
            UserAccount fromAccount = userAccountRepository.findById(fromAccountId).orElse(null);
            if (fromAccount == null) {
                throw new RuntimeException("转出账户不存在");
            }
            
            fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
            userAccountRepository.save(fromAccount);
            
            // 即使抛出异常，事务也不会回滚，因为这个类不是 Spring Bean
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
            transactionLog.setDescription("非管理Bean转账");
            transactionLogRepository.save(transactionLog);
        }
    }
    
    /**
     * 错误示例：使用 new 关键字创建对象
     */
    public void transferWithNewObject(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("使用 new 关键字创建的对象，事务不会生效");
        
        // 直接 new 对象，不是 Spring 管理的 Bean
        NonManagedService service = new NonManagedService(userAccountRepository, transactionLogRepository);
        service.transfer(fromAccountId, toAccountId, amount);
    }
    
    /**
     * 正确解决方案1：让类被 Spring 管理
     */
    @Transactional
    public void transferCorrect1(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("正确方案1：Spring 管理的 Bean，事务生效");
        
        UserAccount fromAccount = userAccountRepository.findById(fromAccountId).orElse(null);
        if (fromAccount == null) {
            throw new RuntimeException("转出账户不存在");
        }
        
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        userAccountRepository.save(fromAccount);
        
        if (amount.compareTo(new BigDecimal("100")) > 0) {
            throw new RuntimeException("转账金额过大，事务将回滚");
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
        transactionLog.setDescription("Spring管理Bean转账");
        transactionLogRepository.save(transactionLog);
    }
}

/**
 * 正确解决方案2：将服务类注册为 Spring Bean
 */
@Component
@RequiredArgsConstructor
@Slf4j
class ManagedTransactionService {
    
    private final UserAccountRepository userAccountRepository;
    private final TransactionLogRepository transactionLogRepository;
    
    @Transactional
    public void transfer(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("Spring 管理的 Bean，事务正常工作");
        
        UserAccount fromAccount = userAccountRepository.findById(fromAccountId).orElse(null);
        if (fromAccount == null) {
            throw new RuntimeException("转出账户不存在");
        }
        
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        userAccountRepository.save(fromAccount);
        
        if (amount.compareTo(new BigDecimal("100")) > 0) {
            throw new RuntimeException("转账金额过大，事务将回滚");
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
        transactionLog.setDescription("正确管理Bean转账");
        transactionLogRepository.save(transactionLog);
    }
}