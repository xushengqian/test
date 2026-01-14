package com.example.transaction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring @Transactional(propagation = Propagation.NOT_SUPPORTED) 示例
 * 
 * Propagation.NOT_SUPPORTED 的行为：
 * 1. 如果当前存在事务，则挂起该事务
 * 2. 以非事务方式执行方法
 * 3. 方法执行完成后，恢复挂起的事务
 * 
 * @author Example
 */
@Service
public class TransactionPropagationNotSupportedExample {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ExternalApiService externalApiService;

    @Autowired
    private ReportRepository reportRepository;

    // ==================== 示例1：基础用法 ====================

    /**
     * 基础示例：此方法永远不会在事务中执行
     * 
     * 无论调用方是否存在事务，此方法都以非事务方式运行
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void basicNotSupportedExample() {
        System.out.println("此方法在非事务环境中执行");
        // 任何数据库操作都会立即提交，无法回滚
    }

    // ==================== 示例2：审计日志记录 ====================

    /**
     * 场景：记录审计日志
     * 
     * 为什么使用 NOT_SUPPORTED：
     * - 审计日志应该独立于业务事务
     * - 即使业务事务回滚，审计日志也需要保留
     * - 记录"谁在什么时候尝试做了什么"比业务是否成功更重要
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void saveAuditLog(String userId, String action, String details) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setDetails(details);
        log.setTimestamp(System.currentTimeMillis());
        
        // 即使外部事务回滚，此日志也会被保存
        auditLogRepository.save(log);
    }

    // ==================== 示例3：外部API调用 ====================

    /**
     * 场景：调用外部第三方API
     * 
     * 为什么使用 NOT_SUPPORTED：
     * - 外部API调用可能耗时较长
     * - 不应该让外部调用阻塞数据库事务
     * - 外部调用失败不应导致整个事务回滚
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ApiResponse callThirdPartyApi(String requestData) {
        // 挂起当前事务，释放数据库连接
        // 进行可能耗时的外部API调用
        return externalApiService.call(requestData);
    }

    // ==================== 示例4：生成大型报表 ====================

    /**
     * 场景：生成大型报表（只读操作）
     * 
     * 为什么使用 NOT_SUPPORTED：
     * - 报表生成是只读操作，不需要事务保护
     * - 避免长时间占用数据库连接
     * - 提高系统并发性能
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Report generateLargeReport(Long reportId) {
        // 执行复杂的只读查询
        // 不需要事务保护
        return reportRepository.generateReport(reportId);
    }

    // ==================== 示例5：发送通知 ====================

    /**
     * 场景：发送通知（邮件、短信等）
     * 
     * 为什么使用 NOT_SUPPORTED：
     * - 通知发送是独立操作
     * - 发送失败不应影响业务流程
     * - 避免事务超时
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendNotification(String recipient, String message) {
        // 发送邮件或短信
        // 此操作独立于任何事务
        notificationSender.send(recipient, message);
    }

    // ==================== 示例6：与事务方法配合使用 ====================

    /**
     * 主业务方法（在事务中执行）
     */
    @Transactional
    public void processOrder(Order order) {
        // 1. 保存订单（在事务中）
        orderRepository.save(order);
        
        // 2. 记录审计日志（NOT_SUPPORTED - 事务被挂起）
        //    即使后续操作失败，此日志也会保留
        saveAuditLog(order.getUserId(), "CREATE_ORDER", "订单ID: " + order.getId());
        
        // 3. 调用外部API（NOT_SUPPORTED - 事务被挂起）
        //    外部调用不会阻塞事务
        try {
            callThirdPartyApi("order:" + order.getId());
        } catch (Exception e) {
            // 外部调用失败不影响订单创建
            System.err.println("外部API调用失败，但订单创建继续");
        }
        
        // 4. 继续事务操作（事务已恢复）
        orderRepository.updateStatus(order.getId(), "PROCESSING");
        
        // 5. 发送通知（NOT_SUPPORTED - 事务被挂起）
        sendNotification(order.getUserEmail(), "订单已创建");
        
        // 6. 完成事务
        orderRepository.updateStatus(order.getId(), "COMPLETED");
    }

    // ==================== 示例7：缓存操作 ====================

    /**
     * 场景：缓存操作
     * 
     * 为什么使用 NOT_SUPPORTED：
     * - 缓存是独立的存储系统
     * - 不需要数据库事务支持
     * - 缓存操作失败不应影响业务
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void updateCache(String key, Object value) {
        // 更新 Redis/Memcached 等缓存
        cacheService.put(key, value);
    }

    // ==================== 注意事项示例 ====================

    /**
     * ⚠️ 注意：同类内部调用不会触发代理
     * 
     * 错误示例：
     */
    public void wrongUsage() {
        // 直接调用 this.basicNotSupportedExample()
        // NOT_SUPPORTED 配置不会生效！
        // 因为没有通过 Spring 代理调用
        this.basicNotSupportedExample(); // ❌ 事务配置无效
    }

    /**
     * ✅ 正确做法：通过注入的 Bean 调用
     * 
     * 或者使用 AopContext.currentProxy() 获取代理对象
     */
    @Autowired
    private TransactionPropagationNotSupportedExample self;

    public void correctUsage() {
        // 通过注入的代理对象调用
        self.basicNotSupportedExample(); // ✅ 事务配置生效
    }

    // ==================== 内部类定义（示例用） ====================

    // 模拟实体类
    static class AuditLog {
        private String userId;
        private String action;
        private String details;
        private Long timestamp;

        // Getters and Setters
        public void setUserId(String userId) { this.userId = userId; }
        public void setAction(String action) { this.action = action; }
        public void setDetails(String details) { this.details = details; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
    }

    static class Order {
        private Long id;
        private String userId;
        private String userEmail;

        public Long getId() { return id; }
        public String getUserId() { return userId; }
        public String getUserEmail() { return userEmail; }
    }

    static class Report {
        // 报表数据
    }

    static class ApiResponse {
        // API 响应数据
    }

    // 模拟依赖注入（实际项目中会是真正的 Repository/Service）
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private NotificationSender notificationSender;

    @Autowired
    private CacheService cacheService;

    // 模拟接口（实际项目中需要实现）
    interface AuditLogRepository {
        void save(AuditLog log);
    }

    interface ExternalApiService {
        ApiResponse call(String requestData);
    }

    interface ReportRepository {
        Report generateReport(Long reportId);
    }

    interface OrderRepository {
        void save(Order order);
        void updateStatus(Long orderId, String status);
    }

    interface NotificationSender {
        void send(String recipient, String message);
    }

    interface CacheService {
        void put(String key, Object value);
    }
}
