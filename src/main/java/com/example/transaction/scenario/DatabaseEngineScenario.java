package com.example.transaction.scenario;

import com.example.transaction.entity.UserAccount;
import com.example.transaction.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 场景5: 数据库引擎不支持事务
 * 
 * 问题：使用不支持事务的存储引擎（如 MySQL 的 MyISAM）
 * 原因：事务是数据库层面的功能，如果数据库不支持事务，Spring 事务管理无效
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseEngineScenario {
    
    private final UserAccountRepository userAccountRepository;
    
    /**
     * 在不支持事务的存储引擎中，事务注解无效
     * 
     * 注意：这个例子在支持事务的环境中是正常的，
     * 但如果数据库表使用 MyISAM 存储引擎，事务不会生效
     */
    @Transactional
    public void transferWithMyISAM(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("在不支持事务的存储引擎中执行转账");
        
        // 如果表使用 MyISAM 引擎，以下操作不会被事务管理
        UserAccount fromAccount = userAccountRepository.findById(fromAccountId).orElse(null);
        if (fromAccount == null) {
            throw new RuntimeException("转出账户不存在");
        }
        
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        userAccountRepository.save(fromAccount);
        
        // 即使这里抛出异常，前面的操作也不会回滚（如果使用 MyISAM 引擎）
        if (amount.compareTo(new BigDecimal("100")) > 0) {
            throw new RuntimeException("转账金额过大");
        }
        
        UserAccount toAccount = userAccountRepository.findById(toAccountId).orElse(null);
        if (toAccount == null) {
            throw new RuntimeException("转入账户不存在");
        }
        
        toAccount.setBalance(toAccount.getBalance().add(amount));
        userAccountRepository.save(toAccount);
    }
    
    /**
     * 正确解决方案：确保数据库表使用支持事务的存储引擎
     * 
     * MySQL: 使用 InnoDB 引擎（MySQL 5.5+ 默认）
     * PostgreSQL: 默认支持事务
     * Oracle: 默认支持事务
     * SQL Server: 默认支持事务
     */
    @Transactional
    public void transferCorrect(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        log.info("在支持事务的存储引擎中执行转账");
        
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
        
        log.info("转账成功，事务将提交");
    }
}