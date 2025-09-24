-- 创建指标调度系统数据库
CREATE DATABASE IF NOT EXISTS metric_scheduler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE metric_scheduler;

-- 1. 指标定义表
CREATE TABLE IF NOT EXISTS metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_code VARCHAR(100) NOT NULL UNIQUE COMMENT '指标代码',
    metric_name VARCHAR(200) NOT NULL COMMENT '指标名称',
    metric_type VARCHAR(50) NOT NULL COMMENT '指标类型: SQL, STORED_PROCEDURE, SCRIPT',
    metric_sql TEXT COMMENT 'SQL语句或存储过程名称',
    script_path VARCHAR(500) COMMENT '脚本文件路径',
    description TEXT COMMENT '指标描述',
    params JSON COMMENT '指标参数配置',
    is_active TINYINT DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_metric_code (metric_code),
    INDEX idx_metric_type (metric_type),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标定义表';

-- 2. 调度配置表
CREATE TABLE IF NOT EXISTS schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_code VARCHAR(100) NOT NULL UNIQUE COMMENT '调度代码',
    schedule_name VARCHAR(200) NOT NULL COMMENT '调度名称',
    schedule_type VARCHAR(50) NOT NULL COMMENT '调度类型: CRON, FIXED_RATE, ONCE',
    cron_expression VARCHAR(100) COMMENT 'Cron表达式',
    fixed_rate_seconds INT COMMENT '固定频率(秒)',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    is_active TINYINT DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_schedule_type (schedule_type),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度配置表';

-- 3. 指标调度关联表
CREATE TABLE IF NOT EXISTS metric_schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    metric_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    priority INT DEFAULT 0 COMMENT '优先级，数值越大优先级越高',
    max_retry_times INT DEFAULT 3 COMMENT '最大重试次数',
    timeout_seconds INT DEFAULT 3600 COMMENT '超时时间(秒)',
    is_active TINYINT DEFAULT 1 COMMENT '是否启用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_metric_schedule (metric_id, schedule_id),
    FOREIGN KEY (metric_id) REFERENCES metrics(id),
    FOREIGN KEY (schedule_id) REFERENCES schedules(id),
    INDEX idx_priority (priority),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标调度关联表';

-- 4. 作业实例表（每次调度生成的作业）
CREATE TABLE IF NOT EXISTS job_instances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_code VARCHAR(100) NOT NULL UNIQUE COMMENT '作业代码',
    metric_schedule_id BIGINT NOT NULL,
    scheduled_time DATETIME NOT NULL COMMENT '计划执行时间',
    actual_start_time DATETIME COMMENT '实际开始时间',
    actual_end_time DATETIME COMMENT '实际结束时间',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING, RUNNING, SUCCESS, FAILED, TIMEOUT, CANCELLED',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    error_message TEXT COMMENT '错误信息',
    result_data JSON COMMENT '执行结果数据',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (metric_schedule_id) REFERENCES metric_schedules(id),
    INDEX idx_status (status),
    INDEX idx_scheduled_time (scheduled_time),
    INDEX idx_metric_schedule_id (metric_schedule_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业实例表';

-- 5. 作业执行日志表
CREATE TABLE IF NOT EXISTS job_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_instance_id BIGINT NOT NULL,
    log_level VARCHAR(20) NOT NULL COMMENT '日志级别: DEBUG, INFO, WARN, ERROR',
    log_message TEXT NOT NULL COMMENT '日志内容',
    log_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_instance_id) REFERENCES job_instances(id),
    INDEX idx_job_instance_id (job_instance_id),
    INDEX idx_log_level (log_level),
    INDEX idx_log_time (log_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业执行日志表';

-- 6. 指标结果表（存储指标计算结果）
CREATE TABLE IF NOT EXISTS metric_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_instance_id BIGINT NOT NULL,
    metric_id BIGINT NOT NULL,
    dimension_key VARCHAR(500) COMMENT '维度键（用于分组统计的维度组合）',
    dimension_values JSON COMMENT '维度值详情',
    metric_value DECIMAL(20,4) COMMENT '指标值',
    metric_json JSON COMMENT '复杂指标结果（JSON格式）',
    calc_time DATETIME NOT NULL COMMENT '计算时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_instance_id) REFERENCES job_instances(id),
    FOREIGN KEY (metric_id) REFERENCES metrics(id),
    INDEX idx_metric_id (metric_id),
    INDEX idx_calc_time (calc_time),
    INDEX idx_dimension_key (dimension_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指标结果表';

-- 7. 系统配置表
CREATE TABLE IF NOT EXISTS system_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value TEXT NOT NULL COMMENT '配置值',
    config_desc VARCHAR(500) COMMENT '配置描述',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 插入默认系统配置
INSERT INTO system_configs (config_key, config_value, config_desc) VALUES
('max_concurrent_jobs', '10', '最大并发作业数'),
('job_scan_interval', '5', '作业扫描间隔(秒)'),
('job_timeout_check_interval', '60', '作业超时检查间隔(秒)'),
('default_job_timeout', '3600', '默认作业超时时间(秒)'),
('max_retry_times', '3', '默认最大重试次数'),
('retry_interval', '300', '重试间隔(秒)');

-- 创建示例指标
INSERT INTO metrics (metric_code, metric_name, metric_type, metric_sql, description) VALUES
('DAILY_USER_COUNT', '日活跃用户数', 'SQL', 
 'SELECT COUNT(DISTINCT user_id) as value, DATE(login_time) as calc_date FROM user_login_logs WHERE DATE(login_time) = CURDATE() GROUP BY DATE(login_time)', 
 '统计每日活跃用户数'),
('HOURLY_ORDER_AMOUNT', '小时订单金额', 'SQL',
 'SELECT SUM(order_amount) as value, DATE_FORMAT(create_time, "%Y-%m-%d %H:00:00") as calc_time FROM orders WHERE create_time >= NOW() - INTERVAL 1 HOUR GROUP BY DATE_FORMAT(create_time, "%Y-%m-%d %H:00:00")',
 '统计每小时订单总金额');

-- 创建示例调度
INSERT INTO schedules (schedule_code, schedule_name, schedule_type, cron_expression) VALUES
('DAILY_0AM', '每日凌晨执行', 'CRON', '0 0 0 * * ?'),
('HOURLY', '每小时执行', 'CRON', '0 0 * * * ?'),
('EVERY_5MIN', '每5分钟执行', 'FIXED_RATE', NULL);

UPDATE schedules SET fixed_rate_seconds = 300 WHERE schedule_code = 'EVERY_5MIN';