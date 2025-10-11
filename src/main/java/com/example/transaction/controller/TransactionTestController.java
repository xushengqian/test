package com.example.transaction.controller;

import com.example.transaction.entity.User;
import com.example.transaction.repository.UserRepository;
import com.example.transaction.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 事务测试控制器
 */
@RestController
@RequestMapping("/api/transaction")
@RequiredArgsConstructor
@Slf4j
public class TransactionTestController {
    
    private final UserRepository userRepository;
    private final NonPublicMethodService nonPublicMethodService;
    private final SelfInvocationService selfInvocationService;
    private final ExceptionHandlingService exceptionHandlingService;
    private final ExceptionTypeService exceptionTypeService;
    private final PropagationService propagationService;
    private final MisconfigurationService misconfigurationService;
    
    /**
     * 清空数据库
     */
    @DeleteMapping("/clear")
    public ResponseEntity<String> clearDatabase() {
        userRepository.deleteAll();
        return ResponseEntity.ok("数据库已清空");
    }
    
    /**
     * 查询所有用户
     */
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }
    
    /**
     * 测试场景1: 非public方法
     */
    @PostMapping("/test/non-public")
    public ResponseEntity<Map<String, Object>> testNonPublicMethod(
            @RequestParam String username,
            @RequestParam(defaultValue = "public") String type) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("scenario", "非public方法事务");
        result.put("username", username);
        result.put("type", type);
        
        try {
            switch (type) {
                case "public":
                    nonPublicMethodService.publicMethodWithTransaction(username);
                    break;
                case "private":
                    nonPublicMethodService.testPrivateMethod(username);
                    break;
                case "protected":
                    nonPublicMethodService.testProtectedMethod(username);
                    break;
                case "package":
                    nonPublicMethodService.testPackageMethod(username);
                    break;
            }
            result.put("status", "success");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        
        result.put("userCount", userRepository.count());
        result.put("users", userRepository.findAll());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 测试场景2: 内部方法调用
     */
    @PostMapping("/test/self-invocation")
    public ResponseEntity<Map<String, Object>> testSelfInvocation(
            @RequestParam String username,
            @RequestParam(defaultValue = "incorrect") String type) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("scenario", "类内部方法调用");
        result.put("username", username);
        result.put("type", type);
        
        try {
            switch (type) {
                case "incorrect":
                    selfInvocationService.incorrectSelfInvocation(username);
                    break;
                case "proxy":
                    selfInvocationService.correctSelfInvocationByProxy(username);
                    break;
                case "context":
                    selfInvocationService.correctSelfInvocationByContext(username);
                    break;
                case "aopcontext":
                    selfInvocationService.correctSelfInvocationByAopContext(username);
                    break;
                case "direct":
                    selfInvocationService.correctByDirectTransaction(username);
                    break;
                case "nested":
                    selfInvocationService.nestedCallExample();
                    break;
            }
            result.put("status", "success");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        
        result.put("userCount", userRepository.count());
        result.put("users", userRepository.findAll());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 测试场景3: 异常处理
     */
    @PostMapping("/test/exception-handling")
    public ResponseEntity<Map<String, Object>> testExceptionHandling(
            @RequestParam String username,
            @RequestParam(defaultValue = "incorrect") String type) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("scenario", "异常处理");
        result.put("username", username);
        result.put("type", type);
        
        try {
            switch (type) {
                case "incorrect":
                    exceptionHandlingService.incorrectExceptionHandling(username);
                    break;
                case "rethrow":
                    exceptionHandlingService.correctExceptionHandlingByRethrow(username);
                    break;
                case "newexception":
                    exceptionHandlingService.correctExceptionHandlingByNewException(username);
                    break;
                case "manual":
                    exceptionHandlingService.correctExceptionHandlingByManualRollback(username);
                    break;
                case "partial":
                    exceptionHandlingService.partialExceptionHandling(username);
                    break;
                case "nested":
                    exceptionHandlingService.nestedTryCatch(username);
                    break;
            }
            result.put("status", "success");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        
        result.put("userCount", userRepository.count());
        result.put("users", userRepository.findAll());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 测试场景4: 异常类型
     */
    @PostMapping("/test/exception-type")
    public ResponseEntity<Map<String, Object>> testExceptionType(
            @RequestParam String username,
            @RequestParam(defaultValue = "runtime") String type) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("scenario", "异常类型");
        result.put("username", username);
        result.put("type", type);
        
        try {
            switch (type) {
                case "checked":
                    exceptionTypeService.throwCheckedException(username);
                    break;
                case "io":
                    exceptionTypeService.throwIOException(username);
                    break;
                case "sql":
                    exceptionTypeService.throwSQLException(username);
                    break;
                case "runtime":
                    exceptionTypeService.throwRuntimeException(username);
                    break;
                case "checked-rollback":
                    exceptionTypeService.throwCheckedExceptionWithRollback(username);
                    break;
                case "specific":
                    exceptionTypeService.throwSpecificCheckedException(username);
                    break;
                case "wrap":
                    exceptionTypeService.wrapCheckedException(username);
                    break;
                case "norollback":
                    exceptionTypeService.noRollbackForExample(username);
                    break;
                case "business":
                    exceptionTypeService.throwBusinessException(username);
                    break;
            }
            result.put("status", "success");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        
        result.put("userCount", userRepository.count());
        result.put("users", userRepository.findAll());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 测试场景5: 事务传播
     */
    @PostMapping("/test/propagation")
    public ResponseEntity<Map<String, Object>> testPropagation(
            @RequestParam String username,
            @RequestParam(defaultValue = "required") String type) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("scenario", "事务传播");
        result.put("username", username);
        result.put("type", type);
        
        try {
            switch (type) {
                case "required":
                    propagationService.requiredPropagation(username);
                    break;
                case "supports":
                    propagationService.supportsPropagation(username);
                    break;
                case "not-supported":
                    propagationService.notSupportedPropagation(username);
                    break;
                case "never":
                    propagationService.neverPropagation(username);
                    break;
                case "requires-new":
                    propagationService.requiresNewPropagation(username);
                    break;
                case "mandatory":
                    propagationService.mandatoryPropagation(username);
                    break;
                case "nested":
                    propagationService.nestedPropagation(username);
                    break;
                case "mandatory-fail":
                    propagationService.callMandatoryWithoutTransaction(username);
                    break;
                case "never-fail":
                    propagationService.callNeverWithTransaction(username);
                    break;
                case "requires-new-demo":
                    propagationService.demonstrateRequiresNew(username);
                    break;
                case "not-supported-demo":
                    propagationService.demonstrateNotSupported(username);
                    break;
            }
            result.put("status", "success");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        
        result.put("userCount", userRepository.count());
        result.put("users", userRepository.findAll());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 测试场景6: 其他配置问题
     */
    @PostMapping("/test/misconfiguration")
    public ResponseEntity<Map<String, Object>> testMisconfiguration(
            @RequestParam String username,
            @RequestParam(defaultValue = "multithread") String type) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("scenario", "配置问题");
        result.put("username", username);
        result.put("type", type);
        
        try {
            switch (type) {
                case "multithread":
                    misconfigurationService.multiThreadTransaction(username);
                    break;
                case "readonly":
                    misconfigurationService.readOnlyTransaction(username);
                    break;
                case "timeout":
                    misconfigurationService.transactionTimeout(username);
                    break;
                case "native-sql":
                    misconfigurationService.nativeSqlWithoutProperTransaction(username);
                    break;
                case "check-status":
                    misconfigurationService.checkTransactionStatus();
                    break;
                case "demo-status":
                    misconfigurationService.demonstrateTransactionStatus(username);
                    break;
            }
            result.put("status", "success");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        
        result.put("userCount", userRepository.count());
        result.put("users", userRepository.findAll());
        
        return ResponseEntity.ok(result);
    }
}