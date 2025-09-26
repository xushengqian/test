# API接口文档

## 概述

FS机器人外呼系统提供RESTful API接口，支持外呼管理、坐席管理、数据统计等功能。

**基础URL**: `http://localhost:8000/api`

**认证方式**: Bearer Token（可选）

## 通话管理 API

### 发起外呼

**POST** `/call/initiate`

发起一个新的外呼任务。

**请求参数**:
```json
{
  "task_id": 123,
  "phone": "13800138001",
  "script": "您好，我是智能客服..."
}
```

**响应**:
```json
{
  "success": true,
  "call_uuid": "robot_call_123_1640995200"
}
```

### 转接到人工

**POST** `/call/transfer`

将当前通话转接到人工坐席。

**请求参数**:
```json
{
  "call_uuid": "robot_call_123_1640995200",
  "reason": "user_request"
}
```

**响应**:
```json
{
  "success": true,
  "message": "Call transferred"
}
```

### 获取活跃通话

**GET** `/call/active`

获取当前所有活跃的通话列表。

**响应**:
```json
{
  "success": true,
  "calls": [
    {
      "call_id": 1,
      "task_id": 123,
      "phone": "13800138001",
      "status": "answered",
      "start_time": "2023-12-31T10:00:00Z",
      "fs_uuid": "uuid-string"
    }
  ]
}
```

### 获取通话历史

**GET** `/call/history`

获取通话历史记录。

**查询参数**:
- `customer_phone`: 客户电话号码（可选）
- `agent_id`: 坐席ID（可选）
- `limit`: 返回数量限制，默认50
- `offset`: 偏移量，默认0

**响应**:
```json
{
  "success": true,
  "calls": [
    {
      "id": 1,
      "call_uuid": "robot_call_123_1640995200",
      "customer_phone": "13800138001",
      "agent_name": "张三",
      "status": "hangup",
      "start_time": "2023-12-31T10:00:00Z",
      "end_time": "2023-12-31T10:05:30Z",
      "duration": 330,
      "human_transfer": true,
      "transfer_reason": "user_request",
      "call_result": "interested"
    }
  ]
}
```

### 获取通话详情

**GET** `/call/{call_uuid}`

获取特定通话的详细信息。

**响应**:
```json
{
  "success": true,
  "call": {
    "id": 1,
    "call_uuid": "robot_call_123_1640995200",
    "customer_phone": "13800138001",
    "customer_name": "客户A",
    "agent_name": "张三",
    "status": "hangup",
    "start_time": "2023-12-31T10:00:00Z",
    "answer_time": "2023-12-31T10:00:05Z",
    "end_time": "2023-12-31T10:05:30Z",
    "duration": 325,
    "robot_duration": 120,
    "human_transfer": true,
    "transfer_reason": "user_request",
    "transfer_time": "2023-12-31T10:02:00Z",
    "call_result": "interested",
    "customer_intent": "product_inquiry",
    "recording_file": "/recordings/2023-12-31-10-00-00_13800138001_robot.wav",
    "transcript": "完整的通话转写内容...",
    "notes": "客户对产品很感兴趣",
    "conversations": [
      {
        "sequence": 1,
        "speaker": "robot",
        "content": "您好，我是智能客服",
        "confidence": 0.95,
        "intent": "greeting",
        "timestamp": "2023-12-31T10:00:05Z"
      }
    ]
  }
}
```

## 坐席管理 API

### 坐席上线注册

**POST** `/agent/register`

坐席上线并注册到系统。

**请求参数**:
```json
{
  "agent_id": "agent_001",
  "websocket_id": "ws_connection_id",
  "skills": ["销售", "产品咨询"]
}
```

**响应**:
```json
{
  "success": true,
  "agent_info": {
    "agent_id": "agent_001",
    "name": "张三",
    "status": "online",
    "websocket_id": "ws_connection_id",
    "skills": ["销售", "产品咨询"],
    "current_calls": 0,
    "max_calls": 2,
    "last_activity": "2023-12-31T10:00:00Z"
  }
}
```

### 坐席下线

**POST** `/agent/unregister`

坐席下线。

**请求参数**:
```json
{
  "agent_id": "agent_001"
}
```

**响应**:
```json
{
  "success": true
}
```

### 查找可用坐席

**POST** `/agent/find`

为通话查找可用的坐席。

**请求参数**:
```json
{
  "customer_phone": "13800138001",
  "call_uuid": "robot_call_123_1640995200",
  "transfer_reason": "user_request",
  "required_skills": ["销售"]
}
```

**响应**:
```json
{
  "status": "available",
  "agent_id": "agent_001",
  "agent_name": "张三",
  "extension": "1001",
  "estimated_wait_time": 0
}
```

或者需要排队时：
```json
{
  "status": "queue",
  "queue_position": 3,
  "estimated_wait_time": 180
}
```

### 坐席接受通话

**POST** `/agent/accept`

坐席接受分配的通话。

**请求参数**:
```json
{
  "agent_id": "agent_001",
  "call_uuid": "robot_call_123_1640995200"
}
```

**响应**:
```json
{
  "success": true
}
```

### 坐席拒绝通话

**POST** `/agent/reject`

坐席拒绝分配的通话。

**请求参数**:
```json
{
  "agent_id": "agent_001",
  "call_uuid": "robot_call_123_1640995200",
  "reason": "busy"
}
```

**响应**:
```json
{
  "success": true
}
```

### 获取坐席列表

**GET** `/agent/list`

获取所有坐席的列表。

**查询参数**:
- `status`: 坐席状态过滤（可选）

**响应**:
```json
{
  "success": true,
  "agents": [
    {
      "id": 1,
      "agent_id": "agent_001",
      "name": "张三",
      "extension": "1001",
      "email": "zhangsan@company.com",
      "department": "销售部",
      "status": "online",
      "max_concurrent_calls": 2,
      "skills": "[\"销售\", \"产品咨询\"]",
      "created_at": "2023-12-31T09:00:00Z",
      "updated_at": "2023-12-31T10:00:00Z"
    }
  ]
}
```

### 获取坐席统计

**GET** `/agent/statistics`

获取坐席的统计信息。

**响应**:
```json
{
  "success": true,
  "statistics": {
    "total_agents": 10,
    "online_agents": 8,
    "available_agents": 5,
    "busy_agents": 3,
    "queue_length": 2
  }
}
```

## 机器人服务 API

### 机器人对话

**POST** `/robot/chat`

处理机器人与客户的对话。

**请求参数**:
```json
{
  "user_input": "我想了解你们的产品",
  "customer_phone": "13800138001",
  "call_uuid": "robot_call_123_1640995200",
  "context": {
    "conversation_history": [],
    "customer_intent": "unknown"
  }
}
```

**响应**:
```json
{
  "message": "好的，我来为您详细介绍我们的产品特点",
  "intent": "product_inquiry",
  "action": "continue",
  "context": {
    "conversation_history": [
      {
        "speaker": "customer",
        "content": "我想了解你们的产品",
        "timestamp": "2023-12-31T10:01:00Z"
      },
      {
        "speaker": "robot",
        "content": "好的，我来为您详细介绍我们的产品特点",
        "timestamp": "2023-12-31T10:01:05Z"
      }
    ],
    "customer_intent": "product_inquiry",
    "transfer_score": 0
  }
}
```

如果需要转人工：
```json
{
  "message": "正在为您转接人工客服",
  "action": "transfer_human",
  "transfer_reason": "user_request",
  "context": {...}
}
```

### 意图分类

**POST** `/robot/intent/classify`

对用户输入进行意图分类。

**请求参数**:
```json
{
  "text": "我想了解价格",
  "context": {}
}
```

**响应**:
```json
{
  "success": true,
  "result": {
    "intent": "price_inquiry",
    "confidence": 0.85,
    "transfer_score": 3,
    "entities": {
      "product": "软件"
    }
  }
}
```

### 获取机器人统计

**GET** `/robot/statistics`

获取机器人服务的统计信息。

**响应**:
```json
{
  "success": true,
  "statistics": {
    "active_conversations": 15,
    "intent_distribution": {
      "greeting": 25,
      "product_inquiry": 40,
      "price_inquiry": 20,
      "complaint": 5,
      "unknown": 10
    },
    "transfer_reasons": {
      "user_request": 30,
      "price_inquiry": 25,
      "complaint": 20,
      "high_transfer_score": 15,
      "unknown_intent": 10
    }
  }
}
```

## 管理后台 API

### 获取仪表板数据

**GET** `/admin/dashboard`

获取管理后台仪表板的数据。

**响应**:
```json
{
  "success": true,
  "data": {
    "today_stats": {
      "total_calls": 150,
      "answered_calls": 120,
      "transferred_calls": 45,
      "answer_rate": 80.0,
      "transfer_rate": 37.5,
      "avg_duration": 180
    },
    "realtime": {
      "active_calls": 8,
      "available_agents": 5,
      "queue_length": 2,
      "system_load": {
        "active_calls": 8,
        "max_calls": 100,
        "load_percentage": 8.0
      }
    }
  }
}
```

### 获取小时统计

**GET** `/admin/statistics/hourly`

获取按小时的统计数据。

**查询参数**:
- `date`: 日期，格式YYYY-MM-DD（可选，默认今天）

**响应**:
```json
{
  "success": true,
  "statistics": [
    {
      "hour": 9,
      "total_calls": 15,
      "answered_calls": 12,
      "transferred_calls": 5
    },
    {
      "hour": 10,
      "total_calls": 20,
      "answered_calls": 16,
      "transferred_calls": 7
    }
  ]
}
```

### 获取每日统计

**GET** `/admin/statistics/daily`

获取按天的统计数据。

**查询参数**:
- `days`: 天数，默认7天

**响应**:
```json
{
  "success": true,
  "statistics": [
    {
      "date": "2023-12-25",
      "total_calls": 200,
      "answered_calls": 160,
      "transferred_calls": 60,
      "avg_duration": 185
    },
    {
      "date": "2023-12-26",
      "total_calls": 180,
      "answered_calls": 150,
      "transferred_calls": 55,
      "avg_duration": 175
    }
  ]
}
```

### 创建外呼任务

**POST** `/admin/tasks`

创建新的外呼任务。

**请求参数**:
```json
{
  "task_name": "产品推广活动",
  "phone": "13800138001",
  "priority": 2,
  "scheduled_time": "2023-12-31T14:00:00Z",
  "max_attempts": 3,
  "robot_script": "您好，我是XX公司的智能客服..."
}
```

**响应**:
```json
{
  "success": true,
  "task": {
    "id": 123,
    "task_name": "产品推广活动",
    "phone": "13800138001",
    "status": "pending"
  }
}
```

### 执行外呼任务

**POST** `/admin/tasks/{task_id}/execute`

立即执行指定的外呼任务。

**响应**:
```json
{
  "success": true,
  "call_uuid": "robot_call_123_1640995200"
}
```

## WebSocket 事件

系统通过WebSocket推送实时事件，连接地址：`ws://localhost:8000/ws/{client_type}/{client_id}`

### 客户端类型
- `admin`: 管理员客户端
- `agent`: 坐席客户端
- `monitor`: 监控客户端

### 事件类型

#### 通话事件
```json
{
  "type": "call_initiated",
  "data": {
    "call_uuid": "robot_call_123_1640995200",
    "phone": "13800138001",
    "status": "calling"
  }
}
```

```json
{
  "type": "call_answered",
  "data": {
    "call_uuid": "robot_call_123_1640995200",
    "answer_time": "2023-12-31T10:00:05Z"
  }
}
```

```json
{
  "type": "call_transferred",
  "data": {
    "call_uuid": "robot_call_123_1640995200",
    "reason": "user_request",
    "transfer_time": "2023-12-31T10:02:00Z"
  }
}
```

#### 坐席事件
```json
{
  "type": "agent_online",
  "data": {
    "agent_id": "agent_001",
    "name": "张三",
    "status": "online"
  }
}
```

```json
{
  "type": "incoming_call",
  "data": {
    "call_uuid": "robot_call_123_1640995200",
    "customer_phone": "13800138001",
    "transfer_reason": "user_request",
    "customer_info": {},
    "timestamp": "2023-12-31T10:02:00Z"
  }
}
```

## 错误码

| 错误码 | 说明 |
|--------|------|
| 400 | 请求参数错误 |
| 401 | 未授权访问 |
| 403 | 权限不足 |
| 404 | 资源不存在 |
| 409 | 资源冲突 |
| 500 | 服务器内部错误 |

## 限流规则

- API请求频率限制：100次/分钟/IP
- WebSocket连接限制：10个/IP
- 并发外呼限制：可配置，默认100个

## 认证说明

目前系统支持简单的Token认证（可选），在请求头中添加：
```
Authorization: Bearer your-token-here
```

生产环境建议实现完整的OAuth2或JWT认证机制。