# FreeSWITCH 机器人呼出系统 API 文档

## 基础信息

- **基础 URL**: `http://localhost:5000`
- **数据格式**: JSON
- **字符编码**: UTF-8

## API 端点

### 1. 健康检查

**端点**: `GET /health`

**描述**: 检查服务健康状态

**响应示例**:
```json
{
    "status": "healthy",
    "service": "freeswitch-bot"
}
```

---

### 2. 发起外呼

**端点**: `POST /api/call/make`

**描述**: 发起一个机器人外呼

**请求体**:
```json
{
    "phone_number": "13800138000"
}
```

**参数说明**:
- `phone_number` (string, 必需): 目标电话号码

**响应示例**:

成功:
```json
{
    "success": true,
    "call_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "message": "呼叫已发起"
}
```

失败:
```json
{
    "success": false,
    "message": "呼叫失败"
}
```

---

### 3. 查询通话状态

**端点**: `GET /api/call/status/<call_id>`

**描述**: 获取指定通话的状态信息

**参数说明**:
- `call_id` (string, 路径参数): 通话ID

**响应示例**:
```json
{
    "success": true,
    "data": {
        "call_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "phone_number": "13800138000",
        "call_time": "2024-01-15T10:30:00",
        "answer_time": "2024-01-15T10:30:05",
        "end_time": "2024-01-15T10:35:00",
        "duration": 295,
        "status": "completed",
        "transfer_to_agent": true,
        "agent_number": "1001",
        "recording_path": "/var/lib/freeswitch/recordings/2024-01-15/call_123.wav"
    }
}
```

**状态说明**:
- `initiated`: 已发起
- `ringing`: 振铃中
- `answered`: 已接听
- `transferring`: 转接中
- `transferred`: 已转接
- `completed`: 已完成
- `failed`: 失败

---

### 4. 获取活动通话列表

**端点**: `GET /api/calls/active`

**描述**: 获取当前所有活动通话的列表

**响应示例**:
```json
{
    "success": true,
    "count": 2,
    "data": [
        {
            "call_id": "abc123",
            "phone_number": "13800138000",
            "start_time": "2024-01-15T10:30:00",
            "status": "answered",
            "transferred": false
        },
        {
            "call_id": "def456",
            "phone_number": "13900139000",
            "start_time": "2024-01-15T10:31:00",
            "status": "transferred",
            "transferred": true,
            "agent_number": "1002"
        }
    ]
}
```

---

### 5. 查询队列状态

**端点**: `GET /api/queue/status`

**描述**: 获取呼叫队列的状态信息

**响应示例**:
```json
{
    "success": true,
    "data": {
        "queue_name": "support_queue",
        "queue_length": 3,
        "online_agents": 5,
        "busy_agents": 2,
        "available_agents": 3,
        "estimated_wait_time": 180
    }
}
```

---

### 6. 查询坐席状态

**端点**: `GET /api/agent/status/<agent_number>`

**描述**: 获取指定坐席的状态

**参数说明**:
- `agent_number` (string, 路径参数): 坐席号码

**响应示例**:
```json
{
    "success": true,
    "agent_number": "1001",
    "status": "online"
}
```

---

### 7. 更新坐席状态

**端点**: `POST /api/agent/status/<agent_number>`

**描述**: 更新指定坐席的状态

**参数说明**:
- `agent_number` (string, 路径参数): 坐席号码

**请求体**:
```json
{
    "status": "online"
}
```

**状态值**:
- `online`: 在线
- `offline`: 离线
- `busy`: 忙碌
- `break`: 小休
- `available`: 可用

**响应示例**:
```json
{
    "success": true,
    "message": "状态已更新"
}
```

---

## 错误码说明

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

## 使用示例

### Python
```python
import requests

# 发起呼叫
response = requests.post('http://localhost:5000/api/call/make', 
                         json={'phone_number': '13800138000'})
result = response.json()

if result['success']:
    call_id = result['call_id']
    print(f"呼叫成功，ID: {call_id}")
    
    # 查询状态
    status_response = requests.get(f'http://localhost:5000/api/call/status/{call_id}')
    status = status_response.json()
    print(f"通话状态: {status['data']['status']}")
```

### cURL
```bash
# 发起呼叫
curl -X POST http://localhost:5000/api/call/make \
     -H "Content-Type: application/json" \
     -d '{"phone_number":"13800138000"}'

# 查询状态
curl http://localhost:5000/api/call/status/abc123

# 更新坐席状态
curl -X POST http://localhost:5000/api/agent/status/1001 \
     -H "Content-Type: application/json" \
     -d '{"status":"online"}'
```

### JavaScript
```javascript
// 发起呼叫
fetch('http://localhost:5000/api/call/make', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json'
    },
    body: JSON.stringify({
        phone_number: '13800138000'
    })
})
.then(response => response.json())
.then(data => {
    if (data.success) {
        console.log('呼叫ID:', data.call_id);
    }
});
```

## WebSocket 事件（计划中）

未来版本将支持 WebSocket 实时事件推送：

- `call.initiated` - 呼叫发起
- `call.answered` - 呼叫应答
- `call.transferred` - 呼叫转接
- `call.completed` - 呼叫完成
- `agent.status_changed` - 坐席状态变更
- `queue.updated` - 队列更新

## 限流说明

- API 请求限制: 100次/分钟
- 并发呼叫限制: 10个（可配置）
- 单个号码呼叫间隔: 60秒

## 注意事项

1. 所有 API 调用建议添加超时设置
2. 批量呼叫时请控制并发数量
3. 定期清理历史数据以保持系统性能
4. 生产环境建议启用 API 认证机制