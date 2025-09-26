-- FreeSWITCH Agent System Database Schema
-- 坐席系统数据库架构

-- 创建数据库
CREATE DATABASE IF NOT EXISTS freeswitch_agents CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE freeswitch_agents;

-- 坐席信息表
CREATE TABLE IF NOT EXISTS agents (
    agent_id VARCHAR(50) PRIMARY KEY,
    agent_name VARCHAR(100) NOT NULL,
    extension VARCHAR(20) NOT NULL,
    password VARCHAR(255),
    email VARCHAR(100),
    department VARCHAR(50),
    skill_level INT DEFAULT 1,
    status ENUM('available', 'busy', 'break', 'offline') DEFAULT 'offline',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_extension (extension)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 坐席技能表
CREATE TABLE IF NOT EXISTS agent_skills (
    id INT AUTO_INCREMENT PRIMARY KEY,
    agent_id VARCHAR(50) NOT NULL,
    skill_name VARCHAR(50) NOT NULL,
    skill_level INT DEFAULT 1,
    certified BOOLEAN DEFAULT FALSE,
    certified_date DATE,
    FOREIGN KEY (agent_id) REFERENCES agents(agent_id) ON DELETE CASCADE,
    UNIQUE KEY unique_agent_skill (agent_id, skill_name),
    INDEX idx_skill (skill_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 队列配置表
CREATE TABLE IF NOT EXISTS queues (
    queue_name VARCHAR(50) PRIMARY KEY,
    description VARCHAR(200),
    priority INT DEFAULT 5,
    max_wait_time INT DEFAULT 300,
    max_queue_size INT DEFAULT 100,
    music_on_hold VARCHAR(100),
    announce_frequency INT DEFAULT 30,
    announce_sound VARCHAR(100),
    strategy ENUM('ring-all', 'round-robin', 'least-recent', 'fewest-calls', 'random') DEFAULT 'round-robin',
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 队列成员表（坐席与队列的关联）
CREATE TABLE IF NOT EXISTS queue_members (
    id INT AUTO_INCREMENT PRIMARY KEY,
    queue_name VARCHAR(50) NOT NULL,
    agent_id VARCHAR(50) NOT NULL,
    priority INT DEFAULT 5,
    penalty INT DEFAULT 0,
    join_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (queue_name) REFERENCES queues(queue_name) ON DELETE CASCADE,
    FOREIGN KEY (agent_id) REFERENCES agents(agent_id) ON DELETE CASCADE,
    UNIQUE KEY unique_queue_member (queue_name, agent_id),
    INDEX idx_queue (queue_name),
    INDEX idx_agent (agent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 通话记录表
CREATE TABLE IF NOT EXISTS call_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    call_uuid VARCHAR(50) UNIQUE NOT NULL,
    caller_id VARCHAR(50),
    caller_name VARCHAR(100),
    called_number VARCHAR(50),
    queue_name VARCHAR(50),
    agent_id VARCHAR(50),
    call_start TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    call_answer TIMESTAMP NULL,
    call_end TIMESTAMP NULL,
    duration INT DEFAULT 0,
    wait_time INT DEFAULT 0,
    talk_time INT DEFAULT 0,
    disposition ENUM('answered', 'no-answer', 'busy', 'failed', 'abandoned') DEFAULT 'no-answer',
    recording_path VARCHAR(255),
    INDEX idx_call_uuid (call_uuid),
    INDEX idx_agent_id (agent_id),
    INDEX idx_caller_id (caller_id),
    INDEX idx_call_start (call_start),
    INDEX idx_queue (queue_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 坐席登录记录表
CREATE TABLE IF NOT EXISTS agent_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id VARCHAR(50) NOT NULL,
    login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    logout_time TIMESTAMP NULL,
    session_duration INT DEFAULT 0,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    FOREIGN KEY (agent_id) REFERENCES agents(agent_id) ON DELETE CASCADE,
    INDEX idx_agent (agent_id),
    INDEX idx_login_time (login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 坐席状态变更记录表
CREATE TABLE IF NOT EXISTS agent_status_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id VARCHAR(50) NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    reason VARCHAR(100),
    change_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    duration INT DEFAULT 0,
    FOREIGN KEY (agent_id) REFERENCES agents(agent_id) ON DELETE CASCADE,
    INDEX idx_agent (agent_id),
    INDEX idx_change_time (change_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 休息原因表
CREATE TABLE IF NOT EXISTS break_reasons (
    id INT AUTO_INCREMENT PRIMARY KEY,
    reason_code VARCHAR(20) UNIQUE NOT NULL,
    reason_description VARCHAR(100) NOT NULL,
    max_duration INT DEFAULT 900, -- 最大休息时长（秒）
    requires_approval BOOLEAN DEFAULT FALSE,
    enabled BOOLEAN DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 队列统计表（实时更新）
CREATE TABLE IF NOT EXISTS queue_stats (
    queue_name VARCHAR(50) PRIMARY KEY,
    calls_waiting INT DEFAULT 0,
    calls_answered INT DEFAULT 0,
    calls_abandoned INT DEFAULT 0,
    avg_wait_time INT DEFAULT 0,
    avg_talk_time INT DEFAULT 0,
    service_level DECIMAL(5,2) DEFAULT 0.00,
    agents_available INT DEFAULT 0,
    agents_busy INT DEFAULT 0,
    agents_total INT DEFAULT 0,
    last_update TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (queue_name) REFERENCES queues(queue_name) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 坐席统计表（按日汇总）
CREATE TABLE IF NOT EXISTS agent_daily_stats (
    id INT AUTO_INCREMENT PRIMARY KEY,
    agent_id VARCHAR(50) NOT NULL,
    stat_date DATE NOT NULL,
    login_duration INT DEFAULT 0,
    available_duration INT DEFAULT 0,
    busy_duration INT DEFAULT 0,
    break_duration INT DEFAULT 0,
    calls_handled INT DEFAULT 0,
    calls_transferred INT DEFAULT 0,
    avg_handle_time INT DEFAULT 0,
    avg_after_call_work INT DEFAULT 0,
    first_call_resolution INT DEFAULT 0,
    customer_satisfaction DECIMAL(3,2) DEFAULT 0.00,
    FOREIGN KEY (agent_id) REFERENCES agents(agent_id) ON DELETE CASCADE,
    UNIQUE KEY unique_agent_date (agent_id, stat_date),
    INDEX idx_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- VIP客户表
CREATE TABLE IF NOT EXISTS vip_customers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    phone_number VARCHAR(50) UNIQUE NOT NULL,
    customer_name VARCHAR(100),
    vip_level INT DEFAULT 1,
    priority INT DEFAULT 10,
    preferred_agent VARCHAR(50),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_phone (phone_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 回拨请求表
CREATE TABLE IF NOT EXISTS callback_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone_number VARCHAR(50) NOT NULL,
    customer_name VARCHAR(100),
    queue_name VARCHAR(50),
    preferred_time TIMESTAMP NULL,
    priority INT DEFAULT 5,
    status ENUM('pending', 'scheduled', 'completed', 'failed', 'cancelled') DEFAULT 'pending',
    attempts INT DEFAULT 0,
    agent_id VARCHAR(50),
    request_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    callback_time TIMESTAMP NULL,
    notes TEXT,
    INDEX idx_status (status),
    INDEX idx_phone (phone_number),
    INDEX idx_request_time (request_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 插入默认数据
-- 插入默认队列
INSERT INTO queues (queue_name, description, priority, max_wait_time, strategy) VALUES
('support_queue', '技术支持队列', 5, 300, 'least-recent'),
('sales_queue', '销售咨询队列', 5, 300, 'round-robin'),
('billing_queue', '账单查询队列', 3, 300, 'fewest-calls'),
('vip_queue', 'VIP客户专属队列', 10, 60, 'ring-all'),
('emergency_queue', '紧急呼叫队列', 10, 30, 'ring-all');

-- 插入默认休息原因
INSERT INTO break_reasons (reason_code, reason_description, max_duration) VALUES
('lunch', '午餐休息', 3600),
('break', '工间休息', 900),
('meeting', '会议', 7200),
('training', '培训', 14400),
('personal', '私人事务', 1800);

-- 插入测试坐席
INSERT INTO agents (agent_id, agent_name, extension, department, skill_level) VALUES
('1001', '张三', '1001', 'support', 3),
('1002', '李四', '1002', 'sales', 2),
('1003', '王五', '1003', 'billing', 2),
('1004', '赵六', '1004', 'support', 4);

-- 插入坐席技能
INSERT INTO agent_skills (agent_id, skill_name, skill_level, certified) VALUES
('1001', 'support', 3, TRUE),
('1001', 'billing', 2, FALSE),
('1002', 'sales', 3, TRUE),
('1003', 'billing', 3, TRUE),
('1004', 'support', 4, TRUE),
('1004', 'sales', 2, TRUE);

-- 插入队列成员
INSERT INTO queue_members (queue_name, agent_id, priority) VALUES
('support_queue', '1001', 5),
('support_queue', '1004', 8),
('sales_queue', '1002', 5),
('sales_queue', '1004', 3),
('billing_queue', '1003', 5),
('billing_queue', '1001', 3);

-- 创建视图：当前坐席状态
CREATE VIEW current_agent_status AS
SELECT 
    a.agent_id,
    a.agent_name,
    a.extension,
    a.department,
    a.status,
    CASE 
        WHEN asl.new_status = a.status THEN asl.change_time
        ELSE a.updated_at
    END as status_since,
    TIMESTAMPDIFF(SECOND, 
        CASE 
            WHEN asl.new_status = a.status THEN asl.change_time
            ELSE a.updated_at
        END, 
        NOW()) as status_duration
FROM agents a
LEFT JOIN (
    SELECT agent_id, new_status, change_time
    FROM agent_status_log
    WHERE (agent_id, change_time) IN (
        SELECT agent_id, MAX(change_time)
        FROM agent_status_log
        GROUP BY agent_id
    )
) asl ON a.agent_id = asl.agent_id;

-- 创建视图：队列实时状态
CREATE VIEW queue_realtime_status AS
SELECT 
    q.queue_name,
    q.description,
    q.strategy,
    COUNT(DISTINCT qm.agent_id) as total_agents,
    COUNT(DISTINCT CASE WHEN a.status = 'available' THEN qm.agent_id END) as available_agents,
    COUNT(DISTINCT CASE WHEN a.status = 'busy' THEN qm.agent_id END) as busy_agents,
    COALESCE(qs.calls_waiting, 0) as calls_waiting,
    COALESCE(qs.avg_wait_time, 0) as avg_wait_time
FROM queues q
LEFT JOIN queue_members qm ON q.queue_name = qm.queue_name
LEFT JOIN agents a ON qm.agent_id = a.agent_id
LEFT JOIN queue_stats qs ON q.queue_name = qs.queue_name
WHERE q.enabled = TRUE
GROUP BY q.queue_name;

-- 创建存储过程：更新坐席状态
DELIMITER $$
CREATE PROCEDURE update_agent_status(
    IN p_agent_id VARCHAR(50),
    IN p_new_status VARCHAR(20),
    IN p_reason VARCHAR(100)
)
BEGIN
    DECLARE v_old_status VARCHAR(20);
    
    -- 获取当前状态
    SELECT status INTO v_old_status FROM agents WHERE agent_id = p_agent_id;
    
    -- 更新坐席状态
    UPDATE agents SET status = p_new_status WHERE agent_id = p_agent_id;
    
    -- 记录状态变更
    INSERT INTO agent_status_log (agent_id, old_status, new_status, reason)
    VALUES (p_agent_id, v_old_status, p_new_status, p_reason);
    
    -- 如果是登出，更新会话记录
    IF p_new_status = 'offline' THEN
        UPDATE agent_sessions 
        SET logout_time = NOW(), 
            session_duration = TIMESTAMPDIFF(SECOND, login_time, NOW())
        WHERE agent_id = p_agent_id AND logout_time IS NULL;
    END IF;
    
    -- 如果是登录，创建新会话
    IF p_new_status = 'available' AND v_old_status = 'offline' THEN
        INSERT INTO agent_sessions (agent_id) VALUES (p_agent_id);
    END IF;
END$$
DELIMITER ;

-- 创建触发器：自动更新队列统计
DELIMITER $$
CREATE TRIGGER update_queue_stats_after_call
AFTER INSERT ON call_records
FOR EACH ROW
BEGIN
    IF NEW.queue_name IS NOT NULL THEN
        INSERT INTO queue_stats (queue_name, calls_answered, avg_wait_time, avg_talk_time)
        VALUES (NEW.queue_name, 1, NEW.wait_time, NEW.talk_time)
        ON DUPLICATE KEY UPDATE
            calls_answered = calls_answered + 1,
            avg_wait_time = (avg_wait_time * calls_answered + NEW.wait_time) / (calls_answered + 1),
            avg_talk_time = (avg_talk_time * calls_answered + NEW.talk_time) / (calls_answered + 1);
    END IF;
END$$
DELIMITER ;