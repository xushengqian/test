-- 创建企业表
CREATE TABLE IF NOT EXISTS companies (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(100) UNIQUE NOT NULL,
    status VARCHAR(50) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建指标定义表
CREATE TABLE IF NOT EXISTS metric_definitions (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    calculation_sql TEXT,
    data_source VARCHAR(100),
    frequency VARCHAR(50) DEFAULT 'daily', -- daily, weekly, monthly
    priority INTEGER DEFAULT 5, -- 1-10, 1 is highest priority
    timeout_seconds INTEGER DEFAULT 300,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建企业指标关联表
CREATE TABLE IF NOT EXISTS company_metrics (
    id SERIAL PRIMARY KEY,
    company_id INTEGER REFERENCES companies(id),
    metric_id INTEGER REFERENCES metric_definitions(id),
    is_enabled BOOLEAN DEFAULT true,
    custom_params JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(company_id, metric_id)
);

-- 创建指标执行记录表
CREATE TABLE IF NOT EXISTS metric_executions (
    id SERIAL PRIMARY KEY,
    company_id INTEGER REFERENCES companies(id),
    metric_id INTEGER REFERENCES metric_definitions(id),
    execution_id VARCHAR(100) NOT NULL,
    status VARCHAR(50) DEFAULT 'pending', -- pending, running, completed, failed, timeout
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration_ms INTEGER,
    result_data JSONB,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建系统监控表
CREATE TABLE IF NOT EXISTS system_metrics (
    id SERIAL PRIMARY KEY,
    metric_name VARCHAR(100) NOT NULL,
    metric_value DECIMAL(15,4),
    tags JSONB,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_companies_status ON companies(status);
CREATE INDEX IF NOT EXISTS idx_metric_executions_status ON metric_executions(status);
CREATE INDEX IF NOT EXISTS idx_metric_executions_company_metric ON metric_executions(company_id, metric_id);
CREATE INDEX IF NOT EXISTS idx_metric_executions_created_at ON metric_executions(created_at);
CREATE INDEX IF NOT EXISTS idx_system_metrics_timestamp ON system_metrics(timestamp);
CREATE INDEX IF NOT EXISTS idx_system_metrics_name ON system_metrics(metric_name);

-- 插入示例数据
INSERT INTO companies (name, code) VALUES 
    ('阿里巴巴', 'ALIBABA'),
    ('腾讯', 'TENCENT'),
    ('百度', 'BAIDU'),
    ('京东', 'JD'),
    ('美团', 'MEITUAN')
ON CONFLICT (code) DO NOTHING;

INSERT INTO metric_definitions (name, code, description, calculation_sql, frequency, priority) VALUES 
    ('营收指标', 'REVENUE', '计算企业月度营收', 'SELECT SUM(amount) FROM revenue WHERE company_id = %s AND date >= %s', 'daily', 1),
    ('用户增长率', 'USER_GROWTH', '计算用户月度增长率', 'SELECT COUNT(*) FROM users WHERE company_id = %s AND created_at >= %s', 'daily', 2),
    ('成本分析', 'COST_ANALYSIS', '分析企业运营成本', 'SELECT SUM(cost) FROM expenses WHERE company_id = %s AND date >= %s', 'daily', 3),
    ('市场份额', 'MARKET_SHARE', '计算市场份额占比', 'SELECT market_share FROM market_data WHERE company_id = %s', 'weekly', 4),
    ('客户满意度', 'CUSTOMER_SATISFACTION', '客户满意度评分', 'SELECT AVG(rating) FROM reviews WHERE company_id = %s', 'weekly', 5)
ON CONFLICT (code) DO NOTHING;

-- 为每个企业启用所有指标
INSERT INTO company_metrics (company_id, metric_id, is_enabled)
SELECT c.id, m.id, true
FROM companies c
CROSS JOIN metric_definitions m
ON CONFLICT (company_id, metric_id) DO NOTHING;