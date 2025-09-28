-- 人工接入服务配置文件
-- Configuration for Human Intervention Service

local config = {
    -- API服务器配置
    api = {
        -- 主服务器
        primary_endpoint = "https://api.customer-service.com/v1",
        -- 备用服务器
        backup_endpoint = "https://backup.customer-service.com/v1",
        -- API密钥
        api_key = os.getenv("CUSTOMER_SERVICE_API_KEY") or "your-api-key-here",
        -- API密钥（备用）
        api_secret = os.getenv("CUSTOMER_SERVICE_API_SECRET") or "your-api-secret-here"
    },
    
    -- 连接配置
    connection = {
        -- 超时时间（秒）
        timeout = 30,
        -- 连接超时（秒）
        connect_timeout = 10,
        -- 保持连接时间（秒）
        keepalive_timeout = 120,
        -- 最大重试次数
        max_retries = 3,
        -- 重试延迟（秒）
        retry_delay = 2,
        -- 指数退避
        exponential_backoff = true
    },
    
    -- 队列管理配置
    queue = {
        -- 优先级定义
        priority_levels = {
            URGENT = 1,      -- 紧急
            HIGH = 2,        -- 高
            MEDIUM = 3,      -- 中
            LOW = 4,         -- 低
            NORMAL = 3       -- 默认为中等优先级
        },
        
        -- 队列大小限制
        max_queue_size = 1000,
        -- 单用户最大请求数
        max_requests_per_user = 3,
        -- 队列超时时间（秒）
        queue_timeout = 600,  -- 10分钟
        
        -- 自动分配规则
        auto_assign = {
            enabled = true,
            -- 基于技能匹配
            skill_matching = true,
            -- 基于负载均衡
            load_balancing = true,
            -- 基于语言匹配
            language_matching = true
        }
    },
    
    -- 工作时间配置
    working_hours = {
        -- 时区
        timezone = "Asia/Shanghai",
        -- 工作日配置
        weekdays = {
            monday = {start = "09:00", ["end"] = "18:00", enabled = true},
            tuesday = {start = "09:00", ["end"] = "18:00", enabled = true},
            wednesday = {start = "09:00", ["end"] = "18:00", enabled = true},
            thursday = {start = "09:00", ["end"] = "18:00", enabled = true},
            friday = {start = "09:00", ["end"] = "18:00", enabled = true},
            saturday = {start = "09:00", ["end"] = "13:00", enabled = true},
            sunday = {start = "09:00", ["end"] = "13:00", enabled = false}
        },
        
        -- 节假日（可以动态加载）
        holidays = {
            "2025-01-01",  -- 元旦
            "2025-01-28",  -- 春节
            "2025-01-29",
            "2025-01-30",
            "2025-10-01",  -- 国庆节
            "2025-10-02",
            "2025-10-03"
        },
        
        -- 非工作时间消息
        off_hours_message = "抱歉，当前不在客服工作时间。我们的工作时间是周一至周五 9:00-18:00，周六 9:00-13:00。"
    },
    
    -- 客服人员配置
    agents = {
        -- 最大并发会话数
        max_concurrent_sessions = 5,
        -- 会话超时时间（秒）
        session_timeout = 1800,  -- 30分钟
        -- 空闲超时（秒）
        idle_timeout = 300,      -- 5分钟
        
        -- 技能分类
        skill_categories = {
            "技术支持",
            "账户问题",
            "订单查询",
            "售后服务",
            "投诉建议",
            "产品咨询",
            "支付问题"
        },
        
        -- 语言支持
        supported_languages = {
            "zh-CN",  -- 简体中文
            "zh-TW",  -- 繁体中文
            "en-US",  -- 英语
            "ja-JP"   -- 日语
        }
    },
    
    -- 消息配置
    messages = {
        -- 欢迎消息
        welcome = "欢迎使用人工客服服务，我们将尽快为您安排客服人员。",
        -- 排队消息
        queuing = "您已进入排队队列，当前排队位置：{position}，预计等待时间：{time}秒",
        -- 连接成功消息
        connected = "已为您连接客服 {agent_name}，请问有什么可以帮助您？",
        -- 会话结束消息
        session_ended = "感谢您的咨询，本次会话已结束。",
        -- 超时消息
        timeout = "由于长时间未活动，会话已自动结束。",
        -- 错误消息
        error = "抱歉，系统遇到错误，请稍后重试。",
        -- 取消消息
        cancelled = "您的人工客服请求已取消。"
    },
    
    -- 日志配置
    logging = {
        -- 日志级别: DEBUG, INFO, WARNING, ERROR, CRITICAL
        level = "INFO",
        -- 日志文件路径
        file_path = "/var/log/human_intervention.log",
        -- 最大文件大小（MB）
        max_file_size = 100,
        -- 最大备份文件数
        max_backup_files = 10,
        -- 是否输出到控制台
        console_output = true,
        -- 日志格式
        format = "[{timestamp}] [{level}] [{module}] {message}"
    },
    
    -- 监控配置
    monitoring = {
        -- 是否启用监控
        enabled = true,
        -- 指标收集间隔（秒）
        metrics_interval = 60,
        -- 健康检查端点
        health_check_endpoint = "/health",
        -- 指标端点
        metrics_endpoint = "/metrics",
        
        -- 告警阈值
        alerts = {
            -- 队列长度告警
            queue_length_threshold = 50,
            -- 等待时间告警（秒）
            wait_time_threshold = 300,
            -- 错误率告警（百分比）
            error_rate_threshold = 5,
            -- 响应时间告警（毫秒）
            response_time_threshold = 1000
        }
    },
    
    -- 安全配置
    security = {
        -- 是否启用SSL
        use_ssl = true,
        -- 是否验证SSL证书
        verify_ssl = true,
        -- 请求签名
        enable_signature = true,
        -- 签名算法
        signature_algorithm = "HMAC-SHA256",
        -- IP白名单（空表示不限制）
        ip_whitelist = {},
        -- 速率限制（每分钟请求数）
        rate_limit = {
            enabled = true,
            requests_per_minute = 60,
            requests_per_hour = 1000
        }
    },
    
    -- 数据库配置（如果需要持久化）
    database = {
        -- 是否启用数据库
        enabled = false,
        -- 数据库类型: mysql, postgresql, sqlite, redis
        type = "redis",
        -- 连接配置
        connection = {
            host = "localhost",
            port = 6379,
            database = 0,
            password = os.getenv("REDIS_PASSWORD") or "",
            -- 连接池大小
            pool_size = 10
        },
        -- 数据过期时间（秒）
        ttl = 86400  -- 24小时
    },
    
    -- 通知配置
    notifications = {
        -- 是否启用通知
        enabled = true,
        -- 通知渠道
        channels = {
            -- 邮件通知
            email = {
                enabled = false,
                smtp_server = "smtp.example.com",
                smtp_port = 587,
                username = "notifications@example.com",
                password = os.getenv("SMTP_PASSWORD") or ""
            },
            -- Webhook通知
            webhook = {
                enabled = true,
                url = "https://hooks.example.com/human-intervention",
                method = "POST",
                headers = {
                    ["Content-Type"] = "application/json",
                    ["Authorization"] = "Bearer " .. (os.getenv("WEBHOOK_TOKEN") or "")
                }
            },
            -- 短信通知
            sms = {
                enabled = false,
                provider = "twilio",
                account_sid = os.getenv("TWILIO_ACCOUNT_SID") or "",
                auth_token = os.getenv("TWILIO_AUTH_TOKEN") or "",
                from_number = "+1234567890"
            }
        }
    },
    
    -- 特性开关
    features = {
        -- 启用自动转接
        auto_transfer = true,
        -- 启用满意度调查
        satisfaction_survey = true,
        -- 启用会话录音
        session_recording = false,
        -- 启用情感分析
        sentiment_analysis = false,
        -- 启用关键词检测
        keyword_detection = true,
        -- 关键词列表（触发人工接入）
        trigger_keywords = {
            "投诉",
            "退款",
            "紧急",
            "人工",
            "客服",
            "帮助",
            "无法解决",
            "不满意"
        }
    }
}

return config