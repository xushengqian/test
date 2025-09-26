# FreeSWITCH 人工坐席系统 (Agent Access System)

这是一个完整的FreeSWITCH人工坐席接入系统实现，包含IVR、队列管理、坐席管理、回拨等功能。

## 系统架构

### 核心组件

1. **agent_queue.lua** - 队列管理核心脚本
   - 呼叫排队管理
   - 坐席分配策略
   - 等待音乐播放
   - VIP优先级处理

2. **agent_management.lua** - 坐席管理脚本
   - 坐席登录/登出
   - 状态管理（可用/忙碌/休息/离线）
   - 坐席信息查询

3. **main_ivr.lua** - 主IVR交互脚本
   - 语音菜单导航
   - 按键选择处理
   - 转接到不同队列或坐席

4. **dialplan_agent.xml** - 拨号计划配置
   - 定义所有接入号码
   - 坐席功能码配置
   - 路由规则设置

5. **database_schema.sql** - 数据库架构
   - 坐席信息存储
   - 通话记录
   - 队列统计
   - VIP客户管理

## 功能特性

### 1. 坐席管理功能

- **坐席登录**: 拨打 *61，输入坐席ID和分机号
- **快速登录**: 拨打 *60，使用分机号作为坐席ID
- **坐席登出**: 拨打 *62
- **设置休息**: 拨打 *63
- **设置可用**: 拨打 *64
- **查询状态**: 拨打 *65

### 2. 队列功能

- **多队列支持**: 销售、技术支持、账单查询等
- **技能路由**: 根据坐席技能分配呼叫
- **优先级管理**: VIP客户优先接入
- **等待提示**: 定期播报队列位置
- **溢出处理**: 超时转语音信箱

### 3. IVR功能

- **主菜单**: 拨打 8000 或 8888
- **多级菜单**: 支持子菜单导航
- **智能路由**: 根据选择转接相应队列
- **语音识别**: 支持语音输入（需配置ASR）

### 4. 监控功能

- **实时监听**: 拨打 *66+分机号
- **耳语功能**: 拨打 *67+分机号
- **强插功能**: 拨打 *68+分机号
- **队列统计**: 拨打 *69

### 5. 回拨功能

- **请求回拨**: 拨打 *70
- **时间选择**: 立即/30分钟/1小时/2小时
- **自动重试**: 失败后自动重试3次
- **取消回拨**: 支持取消待处理的回拨

## 安装配置

### 1. 前置要求

```bash
# FreeSWITCH 1.10+
# Lua 5.2+
# MySQL 5.7+ 或 MariaDB 10.3+

# 安装Lua MySQL模块（可选，用于数据库功能）
apt-get install lua-sql-mysql
```

### 2. 文件部署

```bash
# 将Lua脚本复制到FreeSWITCH脚本目录
cp *.lua /usr/share/freeswitch/scripts/

# 将拨号计划配置添加到dialplan
cp dialplan_agent.xml /etc/freeswitch/dialplan/default/

# 重新加载配置
fs_cli -x "reloadxml"
```

### 3. 数据库配置（可选）

```bash
# 创建数据库
mysql -u root -p < database_schema.sql

# 配置ODBC连接
# 编辑 /etc/odbc.ini
[freeswitch_db]
Driver = MySQL
Server = localhost
Database = freeswitch_agents
User = freeswitch
Password = your_password
Port = 3306

# 测试连接
isql -v freeswitch_db
```

### 4. 音频文件准备

系统使用的音频提示文件：
- `ivr/ivr-welcome.wav` - 欢迎语
- `ivr/ivr-menu_options.wav` - 菜单选项
- `ivr/ivr-transferring_to_agent.wav` - 转接提示
- `ivr/ivr-no_agents_available.wav` - 无坐席提示
- `ivr/ivr-you_are_now_logged_in.wav` - 登录成功
- `ivr/ivr-you_are_now_logged_out.wav` - 登出成功

可以使用FreeSWITCH自带的音频文件或录制自定义音频。

## 使用示例

### 1. 坐席操作流程

```bash
# 坐席登录
1. 拨打 *61
2. 输入坐席ID: 1001
3. 输入分机号: 1001
4. 听到"登录成功"提示

# 接听呼叫
- 系统自动分配呼叫到可用坐席
- 坐席电话响铃，接听即可

# 坐席休息
1. 拨打 *63
2. 系统设置为休息状态
3. 不再接收新呼叫

# 坐席登出
1. 拨打 *62
2. 系统确认登出
```

### 2. 客户呼入流程

```bash
# 客户拨打 8000
1. 听到欢迎语
2. 选择服务类型：
   - 按1: 销售咨询
   - 按2: 技术支持
   - 按3: 账单查询
   - 按9: 直接转人工
3. 系统自动分配到相应队列
4. 等待可用坐席接听
```

### 3. 管理员操作

```bash
# 查看队列统计
fs_cli -x "lua queue_stats.lua"

# 监听坐席通话
拨打 *66+坐席分机号

# 查看所有坐席状态
fs_cli -x "lua agent_management.lua status_all"
```

## 高级配置

### 1. 队列策略

编辑 `agent_queue.lua` 中的分配策略：

```lua
-- 可选策略：
-- ring-all: 同时振铃所有坐席
-- round-robin: 轮询分配
-- least-recent: 最久未接听优先
-- fewest-calls: 接听最少优先
-- random: 随机分配
```

### 2. VIP配置

在数据库中添加VIP客户：

```sql
INSERT INTO vip_customers (phone_number, customer_name, vip_level, priority)
VALUES ('13800138000', '重要客户', 5, 100);
```

### 3. 技能组配置

```lua
-- 在 agent_management.lua 中配置技能组
local skill_groups = {
    sales = {"1002", "1004"},
    support = {"1001", "1004"},
    billing = {"1003", "1001"}
}
```

### 4. 等待音乐

```xml
<!-- 在 FreeSWITCH 配置中设置 MOH -->
<configuration name="local_stream.conf">
  <directory name="moh" path="/usr/share/freeswitch/sounds/music">
    <param name="rate" value="8000"/>
    <param name="shuffle" value="true"/>
    <param name="channels" value="1"/>
    <param name="interval" value="20"/>
  </directory>
</configuration>
```

## API接口

系统提供以下API接口供外部调用：

```bash
# 坐席登录
fs_cli -x "lua agent_management.lua login 1001 1001"

# 坐席登出
fs_cli -x "lua agent_management.lua logout 1001"

# 查询坐席状态
fs_cli -x "lua agent_management.lua status 1001"

# 获取队列统计
fs_cli -x "lua queue_stats.lua"

# 创建回拨请求
fs_cli -x "lua callback_manager.lua request 13800138000 support_queue"
```

## 监控和报表

### 实时监控

```bash
# 启动实时监控
fs_cli -x "lua queue_stats.lua monitor"

# 查看全局变量
fs_cli -x "global_getvar"
```

### 生成报表

```sql
-- 日报表
SELECT 
    DATE(call_start) as date,
    COUNT(*) as total_calls,
    AVG(wait_time) as avg_wait,
    AVG(talk_time) as avg_talk,
    COUNT(DISTINCT agent_id) as agents_worked
FROM call_records
WHERE DATE(call_start) = CURDATE()
GROUP BY DATE(call_start);

-- 坐席绩效
SELECT 
    a.agent_name,
    COUNT(cr.id) as calls_handled,
    AVG(cr.talk_time) as avg_handle_time,
    SUM(cr.talk_time) as total_talk_time
FROM agents a
LEFT JOIN call_records cr ON a.agent_id = cr.agent_id
WHERE DATE(cr.call_start) = CURDATE()
GROUP BY a.agent_id;
```

## 故障排除

### 1. 坐席无法登录
- 检查分机是否注册
- 验证坐席ID是否存在
- 查看FreeSWITCH日志

### 2. 呼叫无法分配
- 确认有坐席在线且可用
- 检查队列配置
- 验证拨号计划是否正确加载

### 3. 音频文件找不到
- 确认音频文件路径正确
- 检查文件权限
- 使用绝对路径测试

### 4. 数据库连接失败
- 验证数据库服务运行
- 检查连接参数
- 测试ODBC连接

## 性能优化

1. **使用连接池**: 数据库操作使用连接池
2. **缓存坐席状态**: 使用Redis缓存频繁查询的数据
3. **异步处理**: 非关键操作使用异步处理
4. **负载均衡**: 多FreeSWITCH实例负载均衡

## 安全建议

1. **认证加强**: 坐席登录添加密码验证
2. **加密传输**: 使用TLS加密SIP信令
3. **访问控制**: 限制管理功能访问IP
4. **审计日志**: 记录所有操作日志
5. **定期备份**: 自动备份配置和数据

## 扩展功能

可以基于此系统扩展的功能：

1. **WebRTC集成**: 支持浏览器软电话
2. **CRM集成**: 弹屏显示客户信息
3. **智能路由**: AI预测最佳坐席匹配
4. **情绪分析**: 实时通话情绪检测
5. **质检系统**: 自动通话质量评分
6. **预测式外呼**: 自动外呼系统
7. **多媒体支持**: 视频、屏幕共享
8. **工单系统**: 集成工单管理

## 许可证

MIT License

## 支持

如有问题，请提交Issue或联系技术支持。

## 更新日志

### v1.0.0 (2024-01)
- 初始版本发布
- 基础队列功能
- 坐席管理
- IVR系统
- 回拨功能
- 数据库集成