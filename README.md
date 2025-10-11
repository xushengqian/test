# FreeSWITCH 基于用户意图的转人工功能实现

## 📋 功能概述

本项目实现了一个智能的FreeSWITCH转人工系统，能够通过多种方式检测用户的转人工意图，并自动将通话转接到合适的人工坐席。

### 核心功能

1. **智能意图识别**
   - DTMF按键识别（按0转人工）
   - 语音关键词识别（"转人工"、"人工服务"等）
   - 情绪检测（愤怒、沮丧等负面情绪）
   - 失败次数监控（多次识别失败自动转人工）
   - 通话时长监控（超时自动转人工）

2. **智能坐席分配**
   - 技能组匹配（销售、技术支持、投诉等）
   - 优先级管理（VIP客户优先）
   - 负载均衡（最长空闲时间、轮询等策略）
   - 坐席评分系统

3. **队列管理**
   - 多级队列（普通、VIP、紧急等）
   - 排队音乐和位置播报
   - 超时处理
   - 动态优先级调整

4. **失败处理**
   - 回调服务
   - 语音留言
   - 替代IVR选项
   - 自动重试机制

## 🚀 快速开始

### 环境要求

- FreeSWITCH 1.10+
- Python 3.7+
- ESL Python库
- SQLite3

### 安装步骤

1. **复制配置文件到FreeSWITCH目录**

```bash
# 复制拨号计划
cp /workspace/freeswitch/conf/dialplan/transfer_to_agent.xml /etc/freeswitch/dialplan/

# 复制Lua脚本
cp /workspace/freeswitch/scripts/*.lua /usr/share/freeswitch/scripts/

# 复制FIFO配置
cp /workspace/freeswitch/conf/autoload_configs/fifo.conf.xml /etc/freeswitch/autoload_configs/

# 复制坐席用户配置
cp /workspace/freeswitch/conf/directory/default/agents.xml /etc/freeswitch/directory/default/
```

2. **重新加载FreeSWITCH配置**

```bash
fs_cli -x "reloadxml"
fs_cli -x "reload mod_fifo"
fs_cli -x "reload mod_lua"
```

3. **安装Python依赖**

```bash
pip3 install python-ESL
```

## 📁 项目结构

```
/workspace/
├── freeswitch/
│   ├── conf/
│   │   ├── dialplan/
│   │   │   └── transfer_to_agent.xml      # 转人工拨号计划
│   │   ├── autoload_configs/
│   │   │   └── fifo.conf.xml             # FIFO队列配置
│   │   └── directory/default/
│   │       └── agents.xml                 # 坐席用户配置
│   └── scripts/
│       ├── detect_transfer_intent.lua     # 意图检测脚本
│       ├── transfer_to_agent.lua          # 转人工执行脚本
│       ├── check_available_agents.lua     # 坐席检查脚本
│       ├── agent_login.lua                # 坐席登录脚本
│       └── handle_transfer_failure.lua    # 失败处理脚本
├── transfer_agent_manager.py              # Python管理工具
├── test_transfer_agent.sh                 # 测试脚本
└── README.md                              # 本文档
```

## 🔧 配置说明

### 1. 意图检测配置

在 `detect_transfer_intent.lua` 中配置：

```lua
-- 转人工关键词
local transfer_keywords = {
    "转人工", "人工服务", "转接客服", ...
}

-- 情绪检测阈值
local emotion_thresholds = {
    anger = 0.7,       -- 愤怒
    frustration = 0.6, -- 沮丧
    impatience = 0.5   -- 不耐烦
}
```

### 2. 坐席配置

在 `agents.xml` 中配置坐席信息：

```xml
<user id="agent_001">
    <variables>
        <variable name="agent_skills" value="sales,general"/>
        <variable name="agent_level" value="3"/>
    </variables>
</user>
```

### 3. 队列配置

在 `fifo.conf.xml` 中配置队列参数：

```xml
<fifo name="agent_queue@$${domain}" importance="0">
    <param name="strategy" value="longest-idle-agent"/>
    <param name="max-wait" value="600"/>
</fifo>
```

## 📊 使用示例

### 1. 坐席登录

```bash
# 坐席登录
fs_cli -x "lua scripts/agent_login.lua agent_001"
```

### 2. 触发转人工

```bash
# DTMF触发（用户按0）
fs_cli -x "originate {dtmf_digits=0}loopback/detect_intent/transfer_to_agent &park()"

# 关键词触发
fs_cli -x "originate {asr_result='我要转人工'}loopback/detect_intent/transfer_to_agent &park()"

# 情绪触发
fs_cli -x "originate {emotion_type=anger,emotion_score=0.8}loopback/detect_intent/transfer_to_agent &park()"
```

### 3. 查看状态

```bash
# 查看队列状态
fs_cli -x "fifo list"

# 查看坐席状态
fs_cli -x "global_getvar agent_status_agent_001"
```

### 4. 使用管理工具

```python
from transfer_agent_manager import TransferAgentManager

# 创建管理器
manager = TransferAgentManager()
manager.connect()

# 获取坐席状态
agents = manager.get_agent_status_list()

# 获取队列状态
queues = manager.get_queue_status()

# 生成报告
report = manager.generate_report()
```

## 🧪 测试

运行测试脚本进行功能验证：

```bash
./test_transfer_agent.sh
```

测试场景包括：
- DTMF按键转人工
- 语音关键词识别
- 负面情绪检测
- 多次识别失败
- IVR超时
- VIP客户优先
- 坐席登录/登出
- 队列管理
- 失败处理

## 📈 监控与报告

系统提供实时监控和报告功能：

### 实时监控
- 坐席状态（在线/离线/忙碌/空闲）
- 队列长度和等待时间
- 转接成功率
- 平均处理时长

### 报告内容
- 日转接量统计
- 转接原因分布
- 坐席绩效分析
- 回调请求统计

## 🔍 故障排除

### 常见问题

1. **坐席无法接听**
   - 检查坐席是否已登录
   - 验证坐席分机号配置
   - 确认FIFO队列配置正确

2. **转人工失败**
   - 检查Lua脚本是否正确加载
   - 验证拨号计划配置
   - 查看FreeSWITCH日志

3. **队列音乐不播放**
   - 确认MOH（Music on Hold）配置
   - 检查音频文件路径

### 日志位置

```bash
# FreeSWITCH主日志
/var/log/freeswitch/freeswitch.log

# 转人工相关日志（通过grep过滤）
tail -f /var/log/freeswitch/freeswitch.log | grep -E "TransferIntent|TransferAgent|CheckAgents"
```

## 🔐 安全建议

1. **访问控制**
   - 限制ESL访问IP
   - 使用强密码
   - 启用TLS加密

2. **数据保护**
   - 定期备份数据库
   - 加密敏感信息
   - 清理过期日志

3. **监控告警**
   - 设置异常检测
   - 配置实时告警
   - 定期安全审计

## 📝 扩展开发

### 添加新的意图检测方式

在 `detect_transfer_intent.lua` 中添加新的检测函数：

```lua
local function check_custom_condition()
    -- 自定义检测逻辑
    local custom_value = session:getVariable("custom_parameter")
    if custom_value == "transfer_needed" then
        return true
    end
    return false
end
```

### 集成外部系统

使用ESL事件系统集成CRM或工单系统：

```python
# 监听转人工事件
def on_transfer_event(event):
    caller_id = event.getHeader("Caller-ID")
    agent_id = event.getHeader("Agent-ID")
    
    # 更新CRM系统
    crm.update_customer_interaction(caller_id, agent_id)
    
    # 创建工单
    ticket_id = ticketing.create_ticket(caller_id, "Transfer from IVR")
```

## 🤝 贡献

欢迎提交问题报告和功能建议。

## 📄 许可证

本项目采用 MIT 许可证。

## 📞 联系支持

如有问题，请联系技术支持团队。

---

**注意**: 请根据实际的FreeSWITCH环境调整路径和配置参数。