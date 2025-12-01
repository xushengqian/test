-- 创建转接日志表
-- 用于记录所有转接尝试的结果

CREATE TABLE IF NOT EXISTS transfer_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    call_uuid VARCHAR(50) NOT NULL,
    caller_id VARCHAR(50),
    agent_number VARCHAR(20),
    result VARCHAR(20),
    reason VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_call_uuid ON transfer_logs(call_uuid);
CREATE INDEX IF NOT EXISTS idx_caller_id ON transfer_logs(caller_id);
CREATE INDEX IF NOT EXISTS idx_agent_number ON transfer_logs(agent_number);
CREATE INDEX IF NOT EXISTS idx_created_at ON transfer_logs(created_at);

-- 创建坐席状态表（可选）
CREATE TABLE IF NOT EXISTS agent_status (
    agent_number VARCHAR(20) PRIMARY KEY,
    status VARCHAR(20) DEFAULT 'OFFLINE',
    last_status_change DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_call_time DATETIME,
    total_calls INTEGER DEFAULT 0,
    successful_calls INTEGER DEFAULT 0
);

-- 插入示例坐席
INSERT OR IGNORE INTO agent_status (agent_number, status) VALUES 
    ('1001', 'OFFLINE'),
    ('1002', 'OFFLINE'),
    ('1003', 'OFFLINE');

-- 创建坐席统计视图
CREATE VIEW IF NOT EXISTS agent_statistics AS
SELECT 
    a.agent_number,
    a.status,
    COUNT(t.id) as total_transfers,
    SUM(CASE WHEN t.result = 'SUCCESS' THEN 1 ELSE 0 END) as successful_transfers,
    SUM(CASE WHEN t.result = 'FAILED' THEN 1 ELSE 0 END) as failed_transfers,
    ROUND(SUM(CASE WHEN t.result = 'SUCCESS' THEN 1 ELSE 0 END) * 100.0 / COUNT(t.id), 2) as success_rate
FROM 
    agent_status a
LEFT JOIN 
    transfer_logs t ON a.agent_number = t.agent_number
GROUP BY 
    a.agent_number, a.status;

-- 查询语句示例

-- 查询某个坐席的转接记录
-- SELECT * FROM transfer_logs WHERE agent_number = '1001' ORDER BY created_at DESC LIMIT 10;

-- 查询今天的转接统计
-- SELECT agent_number, result, COUNT(*) as count 
-- FROM transfer_logs 
-- WHERE DATE(created_at) = DATE('now') 
-- GROUP BY agent_number, result;

-- 查询失败原因分布
-- SELECT reason, COUNT(*) as count 
-- FROM transfer_logs 
-- WHERE result = 'FAILED' 
-- GROUP BY reason 
-- ORDER BY count DESC;

-- 查询坐席统计
-- SELECT * FROM agent_statistics;
