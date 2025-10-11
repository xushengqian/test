package com.example.transaction.service;

import com.example.transaction.entity.User;
import com.example.transaction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.sql.SQLException;

/**
 * 场景4: 抛出检查型异常（非运行时异常）导致事务不回滚
 * 
 * Spring事务默认只对运行时异常(RuntimeException)和Error进行回滚。
 * 对于受检异常(checked exception)，默认不会触发事务回滚。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExceptionTypeService {
    
    private final UserRepository userRepository;
    
    /**
     * 错误示例：抛出受检异常，事务不会回滚
     */
    @Transactional
    public void throwCheckedException(String username) throws Exception {
        log.info("错误示例：抛出受检异常，事务不会回滚");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        if ("error".equals(username)) {
            // 抛出受检异常，事务不会回滚
            throw new Exception("受检异常 - 事务不会回滚");
        }
    }
    
    /**
     * 错误示例：抛出IOException（受检异常）
     */
    @Transactional
    public void throwIOException(String username) throws IOException {
        log.info("错误示例：抛出IOException");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        if ("error".equals(username)) {
            // IOException是受检异常，事务不会回滚
            throw new IOException("IO异常 - 事务不会回滚");
        }
    }
    
    /**
     * 错误示例：抛出SQLException（受检异常）
     */
    @Transactional
    public void throwSQLException(String username) throws SQLException {
        log.info("错误示例：抛出SQLException");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        if ("error".equals(username)) {
            // SQLException是受检异常，事务不会回滚
            throw new SQLException("SQL异常 - 事务不会回滚");
        }
    }
    
    /**
     * 正确示例1：抛出运行时异常，事务会回滚
     */
    @Transactional
    public void throwRuntimeException(String username) {
        log.info("正确示例1：抛出运行时异常");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        if ("error".equals(username)) {
            // 运行时异常，事务会回滚
            throw new RuntimeException("运行时异常 - 事务会回滚");
        }
    }
    
    /**
     * 正确示例2：指定rollbackFor处理受检异常
     */
    @Transactional(rollbackFor = Exception.class)
    public void throwCheckedExceptionWithRollback(String username) throws Exception {
        log.info("正确示例2：配置rollbackFor处理受检异常");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        if ("error".equals(username)) {
            // 配置了rollbackFor，受检异常也会触发回滚
            throw new Exception("受检异常 - 配置rollbackFor后会回滚");
        }
    }
    
    /**
     * 正确示例3：指定特定的受检异常类型
     */
    @Transactional(rollbackFor = {IOException.class, SQLException.class})
    public void throwSpecificCheckedException(String username) throws IOException, SQLException {
        log.info("正确示例3：指定特定的受检异常类型");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        if ("io_error".equals(username)) {
            throw new IOException("IO异常 - 配置后会回滚");
        }
        
        if ("sql_error".equals(username)) {
            throw new SQLException("SQL异常 - 配置后会回滚");
        }
    }
    
    /**
     * 正确示例4：将受检异常包装为运行时异常
     */
    @Transactional
    public void wrapCheckedException(String username) {
        log.info("正确示例4：将受检异常包装为运行时异常");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        try {
            if ("error".equals(username)) {
                throw new Exception("受检异常");
            }
        } catch (Exception e) {
            // 包装为运行时异常
            throw new RuntimeException("包装后的异常", e);
        }
    }
    
    /**
     * 演示noRollbackFor的使用
     */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void noRollbackForExample(String username) {
        log.info("演示noRollbackFor：IllegalArgumentException不会触发回滚");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        if ("illegal".equals(username)) {
            // 配置了noRollbackFor，这个异常不会触发回滚
            throw new IllegalArgumentException("参数异常 - 配置noRollbackFor不会回滚");
        }
        
        if ("error".equals(username)) {
            // 其他运行时异常仍然会触发回滚
            throw new RuntimeException("其他运行时异常 - 会回滚");
        }
    }
    
    /**
     * 自定义业务异常示例
     */
    public static class BusinessException extends Exception {
        public BusinessException(String message) {
            super(message);
        }
    }
    
    /**
     * 使用自定义业务异常
     */
    @Transactional(rollbackFor = BusinessException.class)
    public void throwBusinessException(String username) throws BusinessException {
        log.info("使用自定义业务异常");
        
        User user = User.builder()
                .username(username)
                .email(username + "@example.com")
                .age(25)
                .balance(1000.0)
                .build();
        userRepository.save(user);
        
        if ("error".equals(username)) {
            throw new BusinessException("业务异常 - 配置rollbackFor会回滚");
        }
    }
}