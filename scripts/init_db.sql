-- FreeSWITCH 机器人呼出系统数据库初始化脚本

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `freeswitch_bot` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `freeswitch_bot`;

-- 通话记录表
CREATE TABLE IF NOT EXISTS `call_records` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `call_id` VARCHAR(64) NOT NULL COMMENT '通话ID',
  `phone_number` VARCHAR(20) NOT NULL COMMENT '电话号码',
  `call_time` DATETIME DEFAULT NULL COMMENT '呼叫时间',
  `answer_time` DATETIME DEFAULT NULL COMMENT '应答时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `duration` INT(11) DEFAULT 0 COMMENT '通话时长(秒)',
  `status` VARCHAR(20) DEFAULT NULL COMMENT '通话状态',
  `transfer_to_agent` BOOLEAN DEFAULT FALSE COMMENT '是否转人工',
  `agent_number` VARCHAR(20) DEFAULT NULL COMMENT '坐席号码',
  `recording_path` VARCHAR(255) DEFAULT NULL COMMENT '录音文件路径',
  `transcription` TEXT DEFAULT NULL COMMENT '通话转写文本',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_call_id` (`call_id`),
  KEY `idx_phone_number` (`phone_number`),
  KEY `idx_call_time` (`call_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通话记录表';

-- 意图识别日志表
CREATE TABLE IF NOT EXISTS `intent_logs` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `call_id` VARCHAR(64) NOT NULL COMMENT '通话ID',
  `user_input` TEXT DEFAULT NULL COMMENT '用户输入',
  `intent` VARCHAR(50) DEFAULT NULL COMMENT '识别的意图',
  `confidence` VARCHAR(10) DEFAULT NULL COMMENT '置信度',
  `timestamp` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_call_id` (`call_id`),
  KEY `idx_intent` (`intent`),
  KEY `idx_timestamp` (`timestamp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意图识别日志表';

-- 坐席信息表
CREATE TABLE IF NOT EXISTS `agents` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `agent_number` VARCHAR(20) NOT NULL COMMENT '坐席号码',
  `agent_name` VARCHAR(50) NOT NULL COMMENT '坐席姓名',
  `skills` VARCHAR(255) DEFAULT NULL COMMENT '技能标签(JSON)',
  `status` VARCHAR(20) DEFAULT 'offline' COMMENT '状态',
  `total_calls` INT(11) DEFAULT 0 COMMENT '总通话数',
  `avg_duration` INT(11) DEFAULT 0 COMMENT '平均通话时长',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_agent_number` (`agent_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='坐席信息表';

-- 插入示例坐席数据
INSERT INTO `agents` (`agent_number`, `agent_name`, `skills`) VALUES
('1001', '坐席小王', '["售后", "技术支持"]'),
('1002', '坐席小李', '["销售", "咨询"]'),
('1003', '坐席小张', '["投诉", "建议"]');

-- 创建统计视图
CREATE OR REPLACE VIEW `v_daily_statistics` AS
SELECT 
  DATE(call_time) as call_date,
  COUNT(*) as total_calls,
  COUNT(CASE WHEN status = 'completed' THEN 1 END) as completed_calls,
  COUNT(CASE WHEN transfer_to_agent = TRUE THEN 1 END) as transferred_calls,
  AVG(duration) as avg_duration,
  MAX(duration) as max_duration
FROM call_records
GROUP BY DATE(call_time);

-- 创建坐席统计视图
CREATE OR REPLACE VIEW `v_agent_statistics` AS
SELECT 
  cr.agent_number,
  a.agent_name,
  COUNT(*) as total_calls,
  AVG(cr.duration) as avg_duration,
  DATE(MAX(cr.end_time)) as last_call_date
FROM call_records cr
LEFT JOIN agents a ON cr.agent_number = a.agent_number
WHERE cr.agent_number IS NOT NULL
GROUP BY cr.agent_number, a.agent_name;

-- 创建索引优化查询性能
CREATE INDEX idx_call_records_composite ON call_records(call_time, status, transfer_to_agent);
CREATE INDEX idx_intent_logs_composite ON intent_logs(call_id, intent, timestamp);