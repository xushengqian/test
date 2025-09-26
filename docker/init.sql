-- 初始化数据库脚本

-- 创建数据库（如果不存在）
-- CREATE DATABASE robot_call_db;

-- 使用数据库
\c robot_call_db;

-- 创建扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 插入初始坐席数据
INSERT INTO agents (agent_id, name, extension, email, department, status, max_concurrent_calls, skills, created_at, updated_at) VALUES
('agent_001', '张三', '1001', 'zhangsan@company.com', '销售部', 'offline', 2, '["销售", "产品咨询"]', NOW(), NOW()),
('agent_002', '李四', '1002', 'lisi@company.com', '客服部', 'offline', 1, '["客服", "投诉处理"]', NOW(), NOW()),
('agent_003', '王五', '1003', 'wangwu@company.com', '技术部', 'offline', 1, '["技术支持", "产品咨询"]', NOW(), NOW());

-- 插入测试客户数据
INSERT INTO customers (phone, name, email, company, status, created_at, updated_at) VALUES
('13800138001', '客户A', 'customer_a@email.com', 'A公司', 'active', NOW(), NOW()),
('13800138002', '客户B', 'customer_b@email.com', 'B公司', 'active', NOW(), NOW()),
('13800138003', '客户C', 'customer_c@email.com', 'C公司', 'active', NOW(), NOW());

-- 插入测试外呼任务
INSERT INTO call_tasks (task_name, customer_id, phone, priority, status, scheduled_time, max_attempts, robot_script, created_at, updated_at) VALUES
('产品推广-客户A', 1, '13800138001', 2, 'pending', NOW() + INTERVAL '1 hour', 3, '您好，我是XX公司的智能客服，想向您介绍我们的新产品...', NOW(), NOW()),
('回访调研-客户B', 2, '13800138002', 1, 'pending', NOW() + INTERVAL '2 hours', 2, '您好，感谢您使用我们的服务，想了解一下您的使用体验...', NOW(), NOW()),
('续费提醒-客户C', 3, '13800138003', 3, 'pending', NOW() + INTERVAL '30 minutes', 3, '您好，您的服务即将到期，我们为您准备了续费优惠...', NOW(), NOW());

-- 创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_call_records_start_time ON call_records(start_time);
CREATE INDEX IF NOT EXISTS idx_call_records_status ON call_records(status);
CREATE INDEX IF NOT EXISTS idx_call_records_customer_id ON call_records(customer_id);
CREATE INDEX IF NOT EXISTS idx_call_records_agent_id ON call_records(agent_id);
CREATE INDEX IF NOT EXISTS idx_call_tasks_status ON call_tasks(status);
CREATE INDEX IF NOT EXISTS idx_call_tasks_scheduled_time ON call_tasks(scheduled_time);
CREATE INDEX IF NOT EXISTS idx_customers_phone ON customers(phone);
CREATE INDEX IF NOT EXISTS idx_agents_agent_id ON agents(agent_id);
CREATE INDEX IF NOT EXISTS idx_agents_status ON agents(status);

-- 插入系统日志示例
INSERT INTO system_logs (level, module, message, details, timestamp) VALUES
('INFO', 'system', 'System initialized', '{"version": "1.0.0", "components": ["freeswitch", "backend", "frontend"]}', NOW()),
('INFO', 'database', 'Database initialized', '{"tables_created": 7, "initial_data_loaded": true}', NOW());

COMMIT;