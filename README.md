# FreeSWITCH 人工坐席接入系统

这是一个完整的FreeSWITCH人工坐席队列管理系统，支持多队列、坐席状态管理、呼叫统计等功能。

## 功能特性

### 🎯 核心功能
- **多队列支持**: 支持多个业务队列（客服、技术支持、销售、VIP等）
- **智能路由**: 基于坐席技能组和状态的智能呼叫分配
- **坐席管理**: 完整的坐席登录/登出、状态切换功能
- **实时统计**: 队列统计、坐席统计、系统概览
- **呼叫记录**: 详细的呼叫日志和统计分析

### 📊 队列策略
- `round_robin`: 轮询分配
- `longest_idle`: 最长空闲时间优先
- `least_recent`: 最少最近通话优先
- `random`: 随机分配

### 🔧 坐席状态
- `available`: 可用
- `busy`: 忙碌
- `break`: 休息
- `away`: 离开
- `offline`: 离线

## 系统架构

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   呼叫进入      │    │   队列管理      │    │   坐席分配      │
│  (拨号计划)     │───▶│ (agent_queue)   │───▶│ (智能路由)      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │
                                ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   统计报告      │    │   数据库存储    │    │   坐席管理      │
│ (queue_stats)   │◀───│   (SQLite)      │───▶│(agent_mgmt)     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 安装部署

### 1. 环境要求

- FreeSWITCH 1.10+
- Lua 5.1+
- LuaSQL SQLite3 模块
- SQLite3 数据库

### 2. 安装步骤

#### 2.1 安装依赖
```bash
# CentOS/RHEL
yum install lua lua-devel sqlite3-devel
luarocks install luasql-sqlite3

# Ubuntu/Debian  
apt-get install lua5.1 liblua5.1-dev libsqlite3-dev
luarocks install luasql-sqlite3
```

#### 2.2 部署脚本文件
```bash
# 创建脚本目录
mkdir -p /usr/local/freeswitch/scripts/agent_system

# 复制Lua脚本文件
cp *.lua /usr/local/freeswitch/scripts/agent_system/

# 设置执行权限
chmod +x /usr/local/freeswitch/scripts/agent_system/*.lua
```

#### 2.3 配置拨号计划
```bash
# 复制拨号计划配置
cp dialplan_agent_queue.xml /usr/local/freeswitch/conf/dialplan/default/

# 重新加载配置
fs_cli -x "reloadxml"
```

#### 2.4 初始化数据库
```bash
# 运行数据库初始化脚本
cd /usr/local/freeswitch/scripts/agent_system
lua init_database.lua
```

### 3. 目录结构
```
/usr/local/freeswitch/
├── scripts/agent_system/
│   ├── agent_queue.lua          # 队列管理主脚本
│   ├── agent_management.lua     # 坐席状态管理
│   ├── queue_stats.lua         # 统计查询脚本
│   └── init_database.lua       # 数据库初始化
├── conf/dialplan/default/
│   └── dialplan_agent_queue.xml # 拨号计划配置
├── db/
│   └── agent_queue.db          # SQLite数据库文件
└── sounds/
    └── queue_announcement.wav   # 队列提示音
```

## 使用说明

### 📞 呼叫接入号码

| 号码 | 功能 | 说明 |
|------|------|------|
| 8000 | 默认客服队列 | 通用客服接入 |
| 8001 | 技术支持队列 | 技术问题咨询 |
| 8002 | 销售队列 | 销售咨询 |
| 8003 | VIP客户队列 | VIP客户专线 |

### 👥 坐席管理号码

| 号码格式 | 功能 | 示例 |
|----------|------|------|
| 9000 + 分机号 | 坐席登录 | 90001001 |
| 9001 + 分机号 | 坐席登出 | 90011001 |
| 9999 | 坐席状态菜单 | 9999 |
| 9998 | 队列统计查询 | 9998 |

### 🎛️ 坐席状态菜单操作

拨打 `9999` 进入坐席状态管理菜单：

- **1**: 设置为可用状态
- **2**: 设置为忙碌状态  
- **3**: 设置为休息状态
- **9**: 坐席登出
- **0**: 播放当前状态
- *****: 退出菜单

### 🔧 API接口使用

#### 坐席管理API
```bash
# 坐席登录
fs_cli -x "lua agent_management.lua login 1001 password technical"

# 坐席登出  
fs_cli -x "lua agent_management.lua logout 1001"

# 设置坐席状态
fs_cli -x "lua agent_management.lua status 1001 available"

# 查询坐席状态
fs_cli -x "lua agent_management.lua status 1001"

# 获取所有坐席列表
fs_cli -x "lua agent_management.lua list"
```

#### 统计查询API
```bash
# 获取系统概览
fs_cli -x "lua queue_stats.lua overview"

# 获取队列统计
fs_cli -x "lua queue_stats.lua queues"

# 获取坐席统计
fs_cli -x "lua queue_stats.lua agents"

# 获取等待队列信息
fs_cli -x "lua queue_stats.lua waiting"

# 生成HTML报告
fs_cli -x "lua queue_stats.lua html" > /tmp/report.html
```

## 数据库结构

### 主要数据表

#### agents (坐席表)
```sql
CREATE TABLE agents (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    extension VARCHAR(20) UNIQUE NOT NULL,     -- 分机号
    name VARCHAR(100) NOT NULL,                -- 坐席姓名
    password VARCHAR(100) DEFAULT '',          -- 登录密码
    status VARCHAR(20) DEFAULT 'offline',      -- 当前状态
    last_call_time DATETIME,                   -- 最后通话时间
    total_calls INTEGER DEFAULT 0,             -- 总通话数
    skill_groups VARCHAR(200),                 -- 技能组
    max_concurrent_calls INTEGER DEFAULT 1,    -- 最大并发通话
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

#### queues (队列表)
```sql
CREATE TABLE queues (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(50) UNIQUE NOT NULL,          -- 队列名称
    description VARCHAR(200),                  -- 队列描述
    max_wait_time INTEGER DEFAULT 300,         -- 最大等待时间
    ring_timeout INTEGER DEFAULT 30,           -- 振铃超时
    strategy VARCHAR(20) DEFAULT 'round_robin', -- 分配策略
    max_queue_size INTEGER DEFAULT 100,        -- 最大队列长度
    announcement_file VARCHAR(200),            -- 提示音文件
    moh_sound VARCHAR(200) DEFAULT 'local_stream://moh', -- 等待音乐
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

#### call_logs (呼叫记录表)
```sql
CREATE TABLE call_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid VARCHAR(100) UNIQUE NOT NULL,         -- 呼叫UUID
    caller_number VARCHAR(20),                 -- 主叫号码
    caller_name VARCHAR(100),                  -- 主叫姓名
    queue_name VARCHAR(50),                    -- 队列名称
    agent_extension VARCHAR(20),               -- 坐席分机
    start_time DATETIME,                       -- 开始时间
    queue_time DATETIME,                       -- 进入队列时间
    answer_time DATETIME,                      -- 应答时间
    end_time DATETIME,                         -- 结束时间
    status VARCHAR(20),                        -- 呼叫状态
    wait_time INTEGER DEFAULT 0,               -- 等待时间(秒)
    talk_time INTEGER DEFAULT 0,               -- 通话时长(秒)
    hangup_cause VARCHAR(50),                  -- 挂机原因
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

## 配置说明

### 系统配置参数

在 `system_config` 表中可以配置以下参数：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| max_queue_size | 100 | 队列最大长度 |
| default_ring_timeout | 30 | 默认振铃超时时间(秒) |
| default_max_wait_time | 300 | 默认最大等待时间(秒) |
| moh_sound | local_stream://moh | 默认等待音乐 |
| auto_logout_time | 3600 | 坐席自动登出时间(秒) |
| queue_stats_interval | 60 | 队列统计更新间隔(秒) |

### 队列配置

每个队列可以独立配置：
- 最大等待时间
- 振铃超时时间
- 分配策略
- 最大队列长度
- 提示音文件
- 等待音乐

### 坐席配置

每个坐席可以配置：
- 技能组（支持多个，逗号分隔）
- 最大并发通话数
- 登录密码
- 优先级和权重

## 监控和统计

### 实时监控

系统提供多种监控方式：

1. **语音查询**: 拨打 9998 听取语音统计
2. **API查询**: 通过fs_cli命令行查询
3. **HTML报告**: 生成可视化统计报告
4. **数据库查询**: 直接查询SQLite数据库

### 统计指标

- **队列统计**: 总呼叫数、完成数、超时数、平均等待时间
- **坐席统计**: 在线状态、今日呼叫数、平均通话时长、完成率
- **系统概览**: 可用坐席数、当前排队数、系统负载

### 报告生成

```bash
# 生成今日统计报告
fs_cli -x "lua queue_stats.lua html" > /var/www/html/daily_report.html

# 定时生成报告 (添加到crontab)
0 */1 * * * fs_cli -x "lua queue_stats.lua html" > /var/www/html/hourly_report.html
```

## 故障排除

### 常见问题

#### 1. 数据库连接失败
```bash
# 检查数据库文件权限
ls -la /usr/local/freeswitch/db/agent_queue.db
chown freeswitch:freeswitch /usr/local/freeswitch/db/agent_queue.db

# 检查LuaSQL模块
lua -e "require 'luasql.sqlite3'; print('OK')"
```

#### 2. 坐席无法登录
```bash
# 检查坐席是否存在
sqlite3 /usr/local/freeswitch/db/agent_queue.db "SELECT * FROM agents WHERE extension='1001';"

# 手动添加坐席
sqlite3 /usr/local/freeswitch/db/agent_queue.db "INSERT INTO agents (extension, name, status) VALUES ('1001', '测试坐席', 'offline');"
```

#### 3. 呼叫无法进入队列
```bash
# 检查拨号计划
fs_cli -x "show dialplan"

# 检查Lua脚本路径
fs_cli -x "lua /usr/local/freeswitch/scripts/agent_system/agent_queue.lua"
```

#### 4. 统计数据异常
```bash
# 重建统计视图
sqlite3 /usr/local/freeswitch/db/agent_queue.db < init_database.lua

# 清理过期数据
sqlite3 /usr/local/freeswitch/db/agent_queue.db "DELETE FROM call_logs WHERE start_time < datetime('now', '-30 days');"
```

### 日志调试

启用详细日志：
```bash
# 在FreeSWITCH控制台
fs_cli> console loglevel 7
fs_cli> lua /usr/local/freeswitch/scripts/agent_system/agent_queue.lua default
```

查看日志文件：
```bash
tail -f /usr/local/freeswitch/log/freeswitch.log | grep "agent_queue"
```

## 扩展开发

### 添加新队列

1. 在数据库中添加队列记录：
```sql
INSERT INTO queues (name, description, max_wait_time, ring_timeout, strategy) 
VALUES ('new_queue', '新队列', 300, 30, 'round_robin');
```

2. 在拨号计划中添加路由：
```xml
<extension name="new_queue">
  <condition field="destination_number" expression="^8004$">
    <action application="lua" data="agent_queue.lua new_queue skill_group"/>
  </condition>
</extension>
```

### 自定义分配策略

在 `agent_queue.lua` 中的 `get_available_agent` 函数中添加新的策略逻辑：

```lua
function get_available_agent(queue_name, skill_groups, strategy)
    local query = ""
    
    if strategy == "custom_strategy" then
        -- 自定义策略逻辑
        query = [[
            SELECT extension, name FROM agents 
            WHERE status = 'available' 
            AND skill_groups LIKE '%]] .. skill_groups .. [[%'
            ORDER BY custom_field ASC
            LIMIT 1
        ]]
    end
    
    -- 执行查询...
end
```

### 集成外部系统

系统支持通过API接口与外部系统集成：

```bash
# CRM系统集成示例
curl -X POST "http://freeswitch-server/api/agent/login" \
     -d "extension=1001&password=123456&skill_groups=sales,vip"

# 统计数据推送
fs_cli -x "lua queue_stats.lua overview" | curl -X POST "http://monitoring-system/stats" -d @-
```

## 许可证

本项目采用 MIT 许可证，详见 LICENSE 文件。

## 技术支持

如有问题或建议，请通过以下方式联系：

- 📧 Email: support@example.com
- 📱 电话: 400-xxx-xxxx
- 💬 QQ群: xxxxxxxxx

---

**版本**: 1.0  
**更新时间**: 2025-09-26  
**作者**: AI Assistant