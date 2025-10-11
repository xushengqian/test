-- 初始化数据脚本
INSERT INTO user_account (id, username, balance, created_time, updated_time) VALUES 
(1, 'alice', 1000.00, NOW(), NOW()),
(2, 'bob', 500.00, NOW(), NOW()),
(3, 'charlie', 800.00, NOW(), NOW()),
(4, 'diana', 1200.00, NOW(), NOW());