package com.example.transaction;

import com.example.transaction.entity.User;
import com.example.transaction.repository.UserRepository;
import com.example.transaction.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring事务失效场景测试类
 */
@SpringBootTest
@ActiveProfiles("test")
public class TransactionDemoApplicationTests {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private NonPublicMethodService nonPublicMethodService;
    
    @Autowired
    private SelfInvocationService selfInvocationService;
    
    @Autowired
    private ExceptionHandlingService exceptionHandlingService;
    
    @Autowired
    private ExceptionTypeService exceptionTypeService;
    
    @Autowired
    private PropagationService propagationService;
    
    @Autowired
    private MisconfigurationService misconfigurationService;
    
    @BeforeEach
    void setUp() {
        // 清空数据库
        userRepository.deleteAll();
    }
    
    /**
     * 测试正常的事务回滚
     */
    @Test
    void testNormalTransaction() {
        assertThrows(RuntimeException.class, () -> {
            nonPublicMethodService.publicMethodWithTransaction("error");
        });
        
        // 事务回滚，数据库应该是空的
        assertEquals(0, userRepository.count());
    }
    
    /**
     * 测试非public方法事务不生效
     */
    @Test
    void testNonPublicMethodTransaction() {
        // 调用private方法（通过public方法间接调用）
        nonPublicMethodService.testPrivateMethod("error");
        
        // 即使抛出异常，由于事务不生效，数据仍然保存
        assertEquals(1, userRepository.count());
    }
    
    /**
     * 测试内部方法调用事务不生效
     */
    @Test
    void testSelfInvocationFails() {
        assertDoesNotThrow(() -> {
            selfInvocationService.incorrectSelfInvocation("error");
        });
        
        // 内部调用，事务不生效，数据仍然保存
        assertEquals(1, userRepository.count());
    }
    
    /**
     * 测试通过代理调用事务生效
     */
    @Test
    void testSelfInvocationByProxy() {
        assertThrows(RuntimeException.class, () -> {
            selfInvocationService.correctSelfInvocationByProxy("error");
        });
        
        // 通过代理调用，事务生效，数据回滚
        assertEquals(0, userRepository.count());
    }
    
    /**
     * 测试异常被捕获导致事务不回滚
     */
    @Test
    void testExceptionCaught() {
        // 异常被捕获，不会抛出
        assertDoesNotThrow(() -> {
            exceptionHandlingService.incorrectExceptionHandling("error");
        });
        
        // 异常被捕获，事务不回滚，数据保存
        assertEquals(1, userRepository.count());
    }
    
    /**
     * 测试重新抛出异常使事务回滚
     */
    @Test
    void testExceptionRethrown() {
        assertThrows(RuntimeException.class, () -> {
            exceptionHandlingService.correctExceptionHandlingByRethrow("error");
        });
        
        // 异常重新抛出，事务回滚
        assertEquals(0, userRepository.count());
    }
    
    /**
     * 测试受检异常不触发回滚
     */
    @Test
    void testCheckedException() throws Exception {
        try {
            exceptionTypeService.throwCheckedException("error");
        } catch (Exception e) {
            // 捕获异常
        }
        
        // 受检异常，事务不回滚，数据保存
        assertEquals(1, userRepository.count());
    }
    
    /**
     * 测试配置rollbackFor后受检异常触发回滚
     */
    @Test
    void testCheckedExceptionWithRollback() {
        assertThrows(Exception.class, () -> {
            exceptionTypeService.throwCheckedExceptionWithRollback("error");
        });
        
        // 配置了rollbackFor，事务回滚
        assertEquals(0, userRepository.count());
    }
    
    /**
     * 测试SUPPORTS传播行为
     */
    @Test
    void testSupportsPropagation() {
        assertThrows(RuntimeException.class, () -> {
            propagationService.supportsPropagation("error");
        });
        
        // 单独调用SUPPORTS，没有事务，数据不回滚
        assertEquals(1, userRepository.count());
    }
    
    /**
     * 测试NOT_SUPPORTED传播行为
     */
    @Test
    void testNotSupportedPropagation() {
        assertThrows(RuntimeException.class, () -> {
            propagationService.notSupportedPropagation("error");
        });
        
        // NOT_SUPPORTED总是非事务执行，数据不回滚
        assertEquals(1, userRepository.count());
    }
    
    /**
     * 测试多线程环境下事务失效
     */
    @Test
    void testMultiThreadTransaction() throws InterruptedException {
        assertDoesNotThrow(() -> {
            misconfigurationService.multiThreadTransaction("error");
        });
        
        // 等待异步操作完成
        Thread.sleep(1000);
        
        // 子线程的操作不在事务中，会保存
        assertTrue(userRepository.count() >= 1);
    }
    
    /**
     * 测试只读事务
     */
    @Test
    void testReadOnlyTransaction() {
        // 只读事务中进行写操作可能会失败
        try {
            misconfigurationService.readOnlyTransaction("test");
        } catch (Exception e) {
            // 预期可能抛出异常
        }
    }
}