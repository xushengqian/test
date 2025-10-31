# FreeSWITCH 机器人外呼转人工系统

一个基于 FreeSWITCH 的智能外呼机器人系统，能够自动识别用户的转人工意图，并实现无缝转接到人工客服。

## 功能特性

- ✅ **智能外呼**: 自动发起外呼并进行机器人对话
- ✅ **意图识别**: 实时识别用户的"转人工"意图
- ✅ **关键词匹配**: 支持多种转人工关键词（转人工、人工、客服等）
- ✅ **智能转接**: 自动查找可用坐席并转接
- ✅ **队列支持**: 支持 FIFO 队列排队等待
- ✅ **多坐席管理**: 支持多个人工坐席轮询
- ✅ **通话录音**: 自动录制完整通话过程
- ✅ **ASR/TTS 集成**: 支持阿里云、腾讯云、讯飞等语音服务
- ✅ **完整日志**: 详细的调用和转接日志

## 系统架构

```
┌─────────────┐      ESL        ┌──────────────┐
│  Python     │◄────────────────┤  FreeSWITCH  │
│  脚本       │                 │              │
└─────────────┘                 └──────────────┘
      │                                │
      │ 调用 ASR/TTS                    │ SIP 外呼
      ▼                                ▼
┌─────────────┐                 ┌──────────────┐
│  云端语音   │                 │   目标用户    │
│  服务       │                 │              │
└─────────────┘                 └──────────────┘
                                      │
                                      │ 转接
                                      ▼
                                ┌──────────────┐
                                │  人工坐席     │
                                │  (分机/队列)  │
                                └──────────────┘
```

## 文件说明

### 核心文件

| 文件 | 说明 |
|------|------|
| `robot_call_handler.py` | 主程序，处理机器人外呼和转接逻辑 |
| `asr_service.py` | ASR（语音识别）服务模块 |
| `tts_service.py` | TTS（语音合成）服务模块 |
| `config.json` | 配置文件 |
| `dialplan_robot_call.xml` | FreeSWITCH 拨号计划配置 |
| `requirements.txt` | Python 依赖包列表 |

### 核心类说明

#### IntentRecognizer（意图识别器）
- 识别用户的转人工意图
- 支持多种关键词匹配
- 置信度评分机制
- 支持否定词过滤

#### RobotCallHandler（机器人处理器）
- 连接 FreeSWITCH ESL
- 发起外呼
- 管理对话流程
- 执行转接操作
- 坐席状态检查

## 安装部署

### 1. 环境要求

- FreeSWITCH 1.10+
- Python 3.8+
- Linux 系统（推荐 Ubuntu/Debian）

### 2. 安装 FreeSWITCH

```bash
# Ubuntu/Debian
apt-get update
apt-get install -y freeswitch freeswitch-lang-zh freeswitch-mod-esl

# 启动 FreeSWITCH
systemctl start freeswitch
systemctl enable freeswitch
```

### 3. 安装 Python 依赖

```bash
# 安装 Python ESL 模块
cd /usr/src/freeswitch/libs/esl
make pymod
make pymod-install

# 安装其他依赖
pip install -r requirements.txt
```

### 4. 配置 FreeSWITCH

#### 4.1 启用 ESL 模块

编辑 `/usr/local/freeswitch/conf/autoload_configs/event_socket.conf.xml`:

```xml
<configuration name="event_socket.conf" description="Socket Client">
  <settings>
    <param name="nat-map" value="false"/>
    <param name="listen-ip" value="127.0.0.1"/>
    <param name="listen-port" value="8021"/>
    <param name="password" value="ClueCon"/>
  </settings>
</configuration>
```

#### 4.2 部署拨号计划

```bash
# 复制拨号计划配置文件
cp dialplan_robot_call.xml /usr/local/freeswitch/conf/dialplan/default/

# 重新加载拨号计划
fs_cli -x "reloadxml"
```

#### 4.3 配置 SIP 网关

编辑 `/usr/local/freeswitch/conf/sip_profiles/external/my_gateway.xml`:

```xml
<gateway name="my_gateway">
  <param name="username" value="your_username"/>
  <param name="password" value="your_password"/>
  <param name="realm" value="sip.provider.com"/>
  <param name="proxy" value="sip.provider.com"/>
  <param name="register" value="true"/>
  <param name="caller-id-in-from" value="true"/>
</gateway>
```

### 5. 配置应用

编辑 `config.json`，修改以下配置：

```json
{
  "freeswitch": {
    "host": "127.0.0.1",
    "esl_port": 8021,
    "esl_password": "ClueCon"
  },
  "outbound": {
    "gateway": "my_gateway",
    "caller_id": "10086"
  },
  "agents": {
    "agent_list": ["1001", "1002", "1003"],
    "queue_name": "support_queue"
  },
  "asr": {
    "provider": "aliyun",
    "app_key": "你的AppKey",
    "access_key": "你的AccessKey"
  },
  "tts": {
    "provider": "aliyun",
    "app_key": "你的AppKey",
    "access_key": "你的AccessKey"
  }
}
```

## 使用方法

### 基本使用

```python
from robot_call_handler import RobotCallHandler
import json

# 加载配置
with open('config.json', 'r', encoding='utf-8') as f:
    config = json.load(f)

# 创建处理器
handler = RobotCallHandler(config)

# 执行外呼
handler.run('13800138000')
```

### 命令行使用

```bash
# 单次外呼
python robot_call_handler.py

# 批量外呼
python batch_call.py phone_list.txt
```

## 工作流程

### 完整流程图

```
开始
  ↓
连接 FreeSWITCH ESL
  ↓
发起外呼 → 拨号失败 → 结束
  ↓ 接通
播放欢迎语
  ↓
┌──────────────────┐
│ 机器人对话循环    │
│                  │
│ 1. 机器人播放话术 │
│ 2. ASR 识别用户  │
│ 3. 意图识别      │
│ 4. 判断是否转人工 │
└──────────────────┘
  │
  ├─ 识别到"转人工"意图
  │    ↓
  │  播放转接提示
  │    ↓
  │  查找可用坐席
  │    ↓
  │  执行转接
  │    ↓
  │  人工接管 → 结束
  │
  └─ 对话结束
       ↓
     播放再见
       ↓
     挂断通话
       ↓
     结束
```

### 意图识别逻辑

```python
# 支持的转人工关键词
转人工、人工、客服、转接、人工客服
找人工、转、接人工、人工服务

# 识别规则
1. 匹配任一关键词 → 置信度 0.9+
2. 多次提及 → 置信度递增
3. 包含否定词（不用、不要）→ 继续机器人
4. 置信度 >= 0.8 → 触发转接
```

### 转接策略

#### 1. 直接转接坐席
```python
# 按顺序查找可用坐席
agent_list = ['1001', '1002', '1003']

for agent in agent_list:
    if check_agent_available(agent):
        transfer_to_agent(agent)
        break
```

#### 2. 队列转接
```python
# 转入 FIFO 队列
transfer_to_queue('support_queue')
```

#### 3. 混合策略
```python
# 优先尝试坐席，失败后转队列
if not transfer_to_agent_list():
    transfer_to_queue()
```

## 关键功能实现

### 1. 意图识别

```python
intent_result = intent_recognizer.recognize(user_input)

# 返回结果
{
    'intent': 'transfer_to_human',  # 意图类型
    'confidence': 0.95,             # 置信度
    'keywords_matched': ['人工']    # 匹配的关键词
}
```

### 2. 坐席状态检查

```python
def _check_agent_available(self, agent_number: str) -> bool:
    """检查坐席是否在线且空闲"""
    # 1. 检查注册状态
    # 2. 检查通话状态
    # 3. 检查 DND 状态
    return is_online and is_idle and not_dnd
```

### 3. 通话转接

```python
# 方式1: uuid_transfer（推荐）
uuid_transfer <uuid> <destination>

# 方式2: uuid_bridge（双向桥接）
uuid_bridge <uuid1> <uuid2>

# 方式3: 转入队列
uuid_transfer <uuid> fifo <queue_name>
```

## 配置说明

### 机器人配置

```json
{
  "robot": {
    "max_conversation_rounds": 5,     // 最大对话轮数
    "speech_timeout": 5,              // 语音识别超时（秒）
    "enable_recording": true,         // 是否启用录音
    "recording_path": "/var/log/..."  // 录音保存路径
  }
}
```

### 意图识别配置

```json
{
  "intent_recognition": {
    "transfer_keywords": [            // 转人工关键词列表
      "转人工", "人工", "客服"
    ],
    "negative_keywords": [            // 否定词列表
      "不用", "不要", "不需要"
    ],
    "confidence_threshold": 0.8       // 转接置信度阈值
  }
}
```

### 坐席配置

```json
{
  "agents": {
    "enabled": true,                  // 是否启用人工坐席
    "agent_list": ["1001", "1002"],   // 坐席分机列表
    "queue_name": "support_queue",    // 队列名称
    "queue_timeout": 60,              // 队列超时（秒）
    "max_wait_time": 300              // 最大等待时间（秒）
  }
}
```

## 测试验证

### 1. 测试 ESL 连接

```bash
fs_cli
# 进入 FreeSWITCH 控制台

freeswitch> sofia status
# 查看 SIP 状态

freeswitch> show channels
# 查看当前通道
```

### 2. 测试外呼

```python
python robot_call_handler.py
```

### 3. 测试转接

```bash
# 在 FreeSWITCH 控制台
freeswitch> uuid_transfer <uuid> user/1001

# 查看转接结果
freeswitch> show channels
```

## 常见问题

### 1. ESL 连接失败

**问题**: `无法连接到 FreeSWITCH ESL`

**解决**:
```bash
# 检查 ESL 配置
vim /usr/local/freeswitch/conf/autoload_configs/event_socket.conf.xml

# 检查端口监听
netstat -tuln | grep 8021

# 检查防火墙
iptables -L | grep 8021
```

### 2. 外呼失败

**问题**: `外呼失败: -ERR NORMAL_TEMPORARY_FAILURE`

**解决**:
- 检查 SIP 网关配置
- 确认网关已注册: `sofia status gateway my_gateway`
- 检查余额和权限

### 3. 转接无反应

**问题**: 转接命令执行但没有效果

**解决**:
```bash
# 检查坐席注册状态
sofia_contact user/1001

# 检查拨号计划
xml_locate dialplan

# 查看错误日志
tail -f /var/log/freeswitch/freeswitch.log
```

### 4. ASR/TTS 不工作

**问题**: 语音识别或合成失败

**解决**:
- 检查云服务 API 配置
- 确认网络连接
- 查看 API 调用日志
- 验证音频格式（8kHz/16kHz）

## 扩展开发

### 1. 自定义意图识别

```python
class CustomIntentRecognizer(IntentRecognizer):
    """自定义意图识别器"""
    
    def recognize(self, user_input: str) -> Dict:
        # 接入 NLP 服务
        # 或使用机器学习模型
        pass
```

### 2. 添加新的 ASR/TTS 服务

```python
from asr_service import ASRService

class CustomASR(ASRService):
    """自定义 ASR 服务"""
    
    def recognize(self, audio_data: bytes) -> str:
        # 实现自定义 ASR 逻辑
        pass
```

### 3. 智能坐席分配

```python
def _get_available_agent(self) -> Optional[str]:
    """智能选择坐席"""
    # 1. 技能匹配
    # 2. 负载均衡
    # 3. 历史服务记录
    pass
```

## 性能优化

### 1. 并发处理

```python
from concurrent.futures import ThreadPoolExecutor

# 多线程处理多个外呼
with ThreadPoolExecutor(max_workers=10) as executor:
    futures = [
        executor.submit(handler.run, phone)
        for phone in phone_list
    ]
```

### 2. 连接池

```python
# 维护 ESL 连接池
from queue import Queue

esl_pool = Queue(maxsize=10)
for _ in range(10):
    conn = ESL.ESLconnection(...)
    esl_pool.put(conn)
```

### 3. 缓存优化

```python
# 缓存 TTS 音频
import redis

redis_client = redis.Redis()
audio_cache_key = f"tts:{text_hash}"
cached_audio = redis_client.get(audio_cache_key)
```

## 监控告警

### 1. 日志监控

```bash
# 实时查看日志
tail -f /var/log/robot_call.log

# 错误统计
grep ERROR /var/log/robot_call.log | wc -l
```

### 2. 指标统计

```python
# 统计指标
- 外呼成功率
- 转人工率
- 平均对话轮数
- 坐席接通率
```

## 安全建议

1. **修改默认密码**: 更改 ESL 密码
2. **限制访问**: ESL 只监听本地地址
3. **加密传输**: 使用 TLS 加密 SIP 通话
4. **权限控制**: 限制脚本运行权限
5. **日志脱敏**: 避免记录敏感信息

## 许可证

MIT License

## 联系方式

如有问题或建议，欢迎提交 Issue。

## 更新日志

### v1.0.0 (2025-10-31)
- ✅ 初始版本发布
- ✅ 实现基本外呼功能
- ✅ 实现意图识别
- ✅ 实现转接人工
- ✅ 支持多种 ASR/TTS 服务
