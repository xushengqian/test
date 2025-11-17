-- 创建数据库
CREATE DATABASE IF NOT EXISTS metric_scheduler DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE metric_scheduler;

-- 指标定义表
CREATE TABLE IF NOT EXISTS metrics (
    id INT AUTO_INCREMENT PRIMARY KEY,
    metric_code VARCHAR(100) NOT NULL UNIQUE COMMENT '指标编码',
    metric_name VARCHAR(200) NOT NULL COMMENT '指标名称',
    metric_type VARCHAR(50) NOT NULL COMMENT '指标类型: SQL/STORED_PROCEDURE/SCRIPT/PARALLEL_SQL',
    metric_sql TEXT COMMENT 'SQL语句或存储过程名称',
    script_path VARCHAR(500) COMMENT '脚本路径',
    params JSON COMMENT '参数配置',
    description TEXT COMMENT '指标描述',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否激活',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_metric_code (metric_code),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指标定义表';

-- 调度配置表
CREATE TABLE IF NOT EXISTS schedules (
    id INT AUTO_INCREMENT PRIMARY KEY,
    schedule_code VARCHAR(100) NOT NULL UNIQUE COMMENT '调度编码',
    schedule_name VARCHAR(200) NOT NULL COMMENT '调度名称',
    schedule_type VARCHAR(50) NOT NULL COMMENT '调度类型: CRON/FIXED_RATE/ONCE',
    cron_expression VARCHAR(100) COMMENT 'Cron表达式',
    fixed_rate_seconds INT COMMENT '固定频率(秒)',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否激活',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_schedule_code (schedule_code),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调度配置表';

-- 指标调度关联表
CREATE TABLE IF NOT EXISTS metric_schedules (
    id INT AUTO_INCREMENT PRIMARY KEY,
    metric_id INT NOT NULL,
    schedule_id INT NOT NULL,
    max_retry_times INT DEFAULT 3 COMMENT '最大重试次数',
    timeout_seconds INT DEFAULT 3600 COMMENT '超时时间(秒)',
    priority INT DEFAULT 0 COMMENT '优先级，数值越大优先级越高',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否激活',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (metric_id) REFERENCES metrics(id),
    FOREIGN KEY (schedule_id) REFERENCES schedules(id),
    INDEX idx_metric_id (metric_id),
    INDEX idx_schedule_id (schedule_id),
    INDEX idx_is_active (is_active),
    INDEX idx_priority (priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指标调度关联表';

-- 作业实例表
CREATE TABLE IF NOT EXISTS job_instances (
    id INT AUTO_INCREMENT PRIMARY KEY,
    job_code VARCHAR(200) NOT NULL UNIQUE COMMENT '作业编码',
    metric_schedule_id INT NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT',
    scheduled_time DATETIME NOT NULL COMMENT '计划执行时间',
    actual_start_time DATETIME COMMENT '实际开始时间',
    actual_end_time DATETIME COMMENT '实际结束时间',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    error_message TEXT COMMENT '错误信息',
    result_data JSON COMMENT '结果数据',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (metric_schedule_id) REFERENCES metric_schedules(id),
    INDEX idx_job_code (job_code),
    INDEX idx_status (status),
    INDEX idx_scheduled_time (scheduled_time),
    INDEX idx_metric_schedule_status (metric_schedule_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作业实例表';

-- 作业日志表
CREATE TABLE IF NOT EXISTS job_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    job_instance_id INT NOT NULL,
    log_level VARCHAR(20) NOT NULL COMMENT '日志级别',
    log_message TEXT NOT NULL COMMENT '日志内容',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_instance_id) REFERENCES job_instances(id),
    INDEX idx_job_instance_id (job_instance_id),
    INDEX idx_log_level (log_level),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作业日志表';

-- 指标结果表
CREATE TABLE IF NOT EXISTS metric_results (
    id INT AUTO_INCREMENT PRIMARY KEY,
    job_instance_id INT NOT NULL,
    metric_id INT NOT NULL,
    dimension_key VARCHAR(500) COMMENT '维度键',
    dimension_values JSON COMMENT '维度值',
    metric_value DOUBLE COMMENT '指标值',
    metric_json JSON COMMENT '指标JSON数据',
    calc_time DATETIME NOT NULL COMMENT '计算时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_instance_id) REFERENCES job_instances(id),
    FOREIGN KEY (metric_id) REFERENCES metrics(id),
    INDEX idx_job_instance_id (job_instance_id),
    INDEX idx_metric_id (metric_id),
    INDEX idx_calc_time (calc_time),
    INDEX idx_dimension_key (dimension_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指标结果表';

-- 调度器性能指标表
CREATE TABLE IF NOT EXISTS scheduler_metrics (
    id INT AUTO_INCREMENT PRIMARY KEY,
    metric_time DATETIME NOT NULL COMMENT '指标时间',
    pending_jobs INT DEFAULT 0 COMMENT '待执行作业数',
    running_jobs INT DEFAULT 0 COMMENT '运行中作业数',
    success_jobs INT DEFAULT 0 COMMENT '成功作业数',
    failed_jobs INT DEFAULT 0 COMMENT '失败作业数',
    avg_execution_time DOUBLE COMMENT '平均执行时间(秒)',
    max_execution_time DOUBLE COMMENT '最大执行时间(秒)',
    throughput DOUBLE COMMENT '吞吐量(作业/分钟)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_metric_time (metric_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='调度器性能指标表';
