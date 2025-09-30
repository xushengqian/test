-- 用户表
CREATE TABLE IF NOT EXISTS user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100),
    email VARCHAR(100),
    phone VARCHAR(20),
    address VARCHAR(200),
    age INT,
    status INT DEFAULT 1,
    remark VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 插入测试数据
INSERT INTO user (username, password, email, phone, address, age, status, remark) VALUES
('张三', '123456', 'zhangsan@example.com', '13800138000', '北京市朝阳区', 25, 1, '测试用户1'),
('李四', '123456', 'lisi@example.com', '13800138001', '上海市浦东新区', 30, 1, '测试用户2'),
('王五', '123456', 'wangwu@example.com', '13800138002', '广州市天河区', 28, 1, '测试用户3');