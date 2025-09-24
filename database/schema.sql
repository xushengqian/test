-- 大批量指标调度执行系统数据库表结构
-- 创建数据库
CREATE DATABASE IF NOT EXISTS metric_scheduler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE metric_scheduler;

-- 1. 指标定义表
CREATE TABLE metric_definitions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL COMMENT '指标名称',
    description TEXT COMMENT '指标描述',
    sql_template TEXT NOT NULL COMMENT 'SQL模板',
    data_source VARCHAR(100) NOT NULL COMMENT '数据源标识',
    category VARCHAR(100) DEFAULT 'default' COMMENT '指标分类',
    tags JSON COMMENT '标签信息',
    timeout_seconds INT DEFAULT 300 COMMENT '超时时间(秒)',
    retry_count INT DEFAULT 3 COMMENT '重试次数',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(100) COMMENT '创建人',
    UNIQUE KEY uk_name (name),
    INDEX idx_category (category),
    INDEX idx_is_active (is_active)
) COMMENT='指标定义表';

-- 2. 调度配置表
CREATE TABLE schedule_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_id BIGINT NOT NULL COMMENT '指标ID',
    cron_expression VARCHAR(100) NOT NULL COMMENT 'Cron表达式',
    priority INT DEFAULT 5 COMMENT '优先级(1-10, 数字越小优先级越高)',
    max_concurrent INT DEFAULT 1 COMMENT '最大并发数',
    start_time TIMESTAMP NULL COMMENT '开始时间',
    end_time TIMESTAMP NULL COMMENT '结束时间',
    is_enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    parameters JSON COMMENT '执行参数',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (metric_id) REFERENCES metric_definitions(id) ON DELETE CASCADE,
    INDEX idx_metric_id (metric_id),
    INDEX idx_priority (priority),
    INDEX idx_is_enabled (is_enabled)
) COMMENT='调度配置表';

-- 3. 执行任务表
CREATE TABLE execution_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_id BIGINT NOT NULL COMMENT '指标ID',
    schedule_id BIGINT NOT NULL COMMENT '调度配置ID',
    task_id VARCHAR(64) NOT NULL COMMENT '任务唯一标识',
    status ENUM('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'TIMEOUT', 'CANCELLED') DEFAULT 'PENDING' COMMENT '执行状态',
    priority INT DEFAULT 5 COMMENT '优先级',
    scheduled_time TIMESTAMP NOT NULL COMMENT '计划执行时间',
    start_time TIMESTAMP NULL COMMENT '开始执行时间',
    end_time TIMESTAMP NULL COMMENT '结束执行时间',
    duration_ms BIGINT COMMENT '执行耗时(毫秒)',
    worker_id VARCHAR(100) COMMENT '执行节点ID',
    result_data JSON COMMENT '执行结果数据',
    error_message TEXT COMMENT '错误信息',
    retry_count INT DEFAULT 0 COMMENT '已重试次数',
    parameters JSON COMMENT '执行参数',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (metric_id) REFERENCES metric_definitions(id),
    FOREIGN KEY (schedule_id) REFERENCES schedule_configs(id),
    UNIQUE KEY uk_task_id (task_id),
    INDEX idx_status (status),
    INDEX idx_scheduled_time (scheduled_time),
    INDEX idx_metric_schedule (metric_id, schedule_id),
    INDEX idx_priority_status (priority, status)
) COMMENT='执行任务表';

-- 4. 执行历史表（用于归档和统计）
CREATE TABLE execution_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID',
    metric_id BIGINT NOT NULL COMMENT '指标ID',
    metric_name VARCHAR(255) NOT NULL COMMENT '指标名称',
    status VARCHAR(20) NOT NULL COMMENT '执行状态',
    scheduled_time TIMESTAMP NOT NULL COMMENT '计划执行时间',
    start_time TIMESTAMP NULL COMMENT '开始执行时间',
    end_time TIMESTAMP NULL COMMENT '结束执行时间',
    duration_ms BIGINT COMMENT '执行耗时(毫秒)',
    worker_id VARCHAR(100) COMMENT '执行节点ID',
    result_rows INT COMMENT '结果行数',
    error_message TEXT COMMENT '错误信息',
    execution_date DATE NOT NULL COMMENT '执行日期',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_execution_date (execution_date),
    INDEX idx_metric_id_date (metric_id, execution_date),
    INDEX idx_status_date (status, execution_date)
) COMMENT='执行历史表';

-- 5. 数据源配置表
CREATE TABLE data_sources (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '数据源名称',
    type VARCHAR(50) NOT NULL COMMENT '数据源类型(mysql, postgresql, clickhouse等)',
    host VARCHAR(255) NOT NULL COMMENT '主机地址',
    port INT NOT NULL COMMENT '端口',
    database_name VARCHAR(100) NOT NULL COMMENT '数据库名',
    username VARCHAR(100) NOT NULL COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码(加密存储)',
    connection_params JSON COMMENT '连接参数',
    max_connections INT DEFAULT 10 COMMENT '最大连接数',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_name (name),
    INDEX idx_type (type),
    INDEX idx_is_active (is_active)
) COMMENT='数据源配置表';

-- 6. 系统配置表
CREATE TABLE system_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL COMMENT '配置键',
    config_value TEXT NOT NULL COMMENT '配置值',
    description TEXT COMMENT '配置描述',
    config_type VARCHAR(50) DEFAULT 'string' COMMENT '配置类型',
    is_encrypted BOOLEAN DEFAULT FALSE COMMENT '是否加密',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_config_key (config_key)
) COMMENT='系统配置表';

-- 7. 工作节点表
CREATE TABLE worker_nodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    node_id VARCHAR(100) NOT NULL COMMENT '节点ID',
    node_name VARCHAR(255) NOT NULL COMMENT '节点名称',
    host_ip VARCHAR(45) NOT NULL COMMENT '主机IP',
    port INT NOT NULL COMMENT '端口',
    max_concurrent_tasks INT DEFAULT 10 COMMENT '最大并发任务数',
    current_tasks INT DEFAULT 0 COMMENT '当前任务数',
    status ENUM('ONLINE', 'OFFLINE', 'BUSY', 'MAINTENANCE') DEFAULT 'OFFLINE' COMMENT '节点状态',
    last_heartbeat TIMESTAMP NULL COMMENT '最后心跳时间',
    capabilities JSON COMMENT '节点能力',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_node_id (node_id),
    INDEX idx_status (status),
    INDEX idx_last_heartbeat (last_heartbeat)
) COMMENT='工作节点表';

-- 8. 任务队列表（用于高性能任务分发）
CREATE TABLE task_queue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL COMMENT '任务ID',
    priority INT NOT NULL COMMENT '优先级',
    scheduled_time TIMESTAMP NOT NULL COMMENT '计划执行时间',
    payload JSON NOT NULL COMMENT '任务载荷',
    status ENUM('QUEUED', 'PROCESSING', 'COMPLETED') DEFAULT 'QUEUED' COMMENT '队列状态',
    worker_id VARCHAR(100) COMMENT '分配的工作节点',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_id (task_id),
    INDEX idx_priority_scheduled (priority, scheduled_time),
    INDEX idx_status (status),
    INDEX idx_worker_id (worker_id)
) COMMENT='任务队列表';

-- 插入默认系统配置
INSERT INTO system_configs (config_key, config_value, description, config_type) VALUES
('scheduler.enabled', 'true', '调度器是否启用', 'boolean'),
('scheduler.scan_interval', '10', '调度扫描间隔(秒)', 'integer'),
('scheduler.max_concurrent_tasks', '100', '系统最大并发任务数', 'integer'),
('executor.default_timeout', '300', '默认执行超时时间(秒)', 'integer'),
('executor.max_retry_count', '3', '默认最大重试次数', 'integer'),
('history.retention_days', '90', '历史数据保留天数', 'integer'),
('queue.batch_size', '50', '队列批处理大小', 'integer');

-- 创建分区表（按月分区执行历史表）
ALTER TABLE execution_history PARTITION BY RANGE (TO_DAYS(execution_date)) (
    PARTITION p202501 VALUES LESS THAN (TO_DAYS('2025-02-01')),
    PARTITION p202502 VALUES LESS THAN (TO_DAYS('2025-03-01')),
    PARTITION p202503 VALUES LESS THAN (TO_DAYS('2025-04-01')),
    PARTITION p202504 VALUES LESS THAN (TO_DAYS('2025-05-01')),
    PARTITION p202505 VALUES LESS THAN (TO_DAYS('2025-06-01')),
    PARTITION p202506 VALUES LESS THAN (TO_DAYS('2025-07-01')),
    PARTITION p202507 VALUES LESS THAN (TO_DAYS('2025-08-01')),
    PARTITION p202508 VALUES LESS THAN (TO_DAYS('2025-09-01')),
    PARTITION p202509 VALUES LESS THAN (TO_DAYS('2025-10-01')),
    PARTITION p202510 VALUES LESS THAN (TO_DAYS('2025-11-01')),
    PARTITION p202511 VALUES LESS THAN (TO_DAYS('2025-12-01')),
    PARTITION p202512 VALUES LESS THAN (TO_DAYS('2026-01-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);