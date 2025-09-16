# FreeSWITCH 音转文系统部署指南

## 🚀 快速部署

### 1. 一键启动（推荐）
```bash
# 克隆项目
git clone <repository-url>
cd freeswitch-call-transcription

# 一键启动
./start.sh
```

### 2. 手动部署
```bash
# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

## 📋 系统要求

### 最低配置
- **CPU**: 4核心 2.4GHz
- **内存**: 8GB RAM
- **磁盘**: 100GB SSD
- **网络**: 100Mbps
- **操作系统**: Linux (Ubuntu 20.04+ 推荐)

### 推荐配置
- **CPU**: 8核心 3.0GHz
- **内存**: 16GB RAM
- **磁盘**: 500GB SSD
- **网络**: 1Gbps
- **操作系统**: Linux (Ubuntu 22.04+ 推荐)

### 高负载配置
- **CPU**: 16核心 3.5GHz
- **内存**: 32GB RAM
- **磁盘**: 1TB NVMe SSD
- **网络**: 10Gbps
- **GPU**: NVIDIA RTX 3080+ (可选，用于加速语音识别)

## 🔧 配置说明

### 环境变量配置
```bash
# 复制环境变量模板
cp .env.example .env

# 编辑配置文件
nano .env
```

### 关键配置项
```bash
# 数据库配置
DATABASE_URL=postgresql://freeswitch:freeswitch123@postgres:5432/freeswitch_calls

# Redis配置
REDIS_URL=redis://redis:6379

# FreeSWITCH配置
FREESWITCH_HOST=freeswitch
FREESWITCH_PORT=8021
FREESWITCH_PASSWORD=ClueCon

# 语音识别配置
WHISPER_MODEL=tiny  # tiny, base, small, medium, large
WHISPER_DEVICE=auto  # auto, cpu, cuda

# 性能配置
MAX_CONCURRENT_CALLS=5
MAX_CALLS_PER_MINUTE=20
MAX_TRANSCRIPTIONS_PER_SECOND=3
```

### FreeSWITCH网关配置
编辑 `freeswitch/conf/sip_profiles/internal.xml` 添加SIP网关：

```xml
<gateways>
  <gateway name="trunk">
    <param name="username" value="your_username"/>
    <param name="realm" value="your_provider.com"/>
    <param name="password" value="your_password"/>
    <param name="register" value="true"/>
  </gateway>
</gateways>
```

## 📊 监控和维护

### 1. 系统监控
访问监控面板: http://localhost:8000/monitor

监控指标包括：
- 系统资源使用率
- 通话统计信息
- 转写性能指标
- 限流状态
- 实时性能图表

### 2. 日志查看
```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f app
docker-compose logs -f freeswitch
docker-compose logs -f postgres

# 查看实时日志
docker-compose logs -f --tail=100 app
```

### 3. 性能测试
```bash
# 运行性能测试
python3 performance_test.py

# 查看测试结果
cat performance_test_results.json
```

### 4. 数据库维护
```bash
# 进入数据库容器
docker-compose exec postgres psql -U freeswitch -d freeswitch_calls

# 查看表大小
\dt+

# 清理旧数据
DELETE FROM transcriptions WHERE created_at < NOW() - INTERVAL '30 days';
```

## 🛠️ 故障排除

### 常见问题

#### 1. 服务启动失败
```bash
# 检查端口占用
netstat -tulpn | grep :8000
netstat -tulpn | grep :5060

# 检查Docker状态
docker-compose ps
docker-compose logs
```

#### 2. FreeSWITCH连接失败
```bash
# 检查FreeSWITCH服务
docker-compose exec freeswitch fs_cli -x "status"

# 检查ESL连接
telnet localhost 8021
```

#### 3. 语音转写不工作
```bash
# 检查语音服务状态
curl http://localhost:8000/api/speech/status

# 检查模型加载
docker-compose logs app | grep -i whisper
```

#### 4. 内存不足
```bash
# 检查内存使用
free -h
docker stats

# 重启服务
docker-compose restart app
```

### 性能优化

#### 1. 调整限流参数
编辑 `optimized_main.py`:
```python
call_rate_limiter = CallRateLimiter(
    max_concurrent_calls=10,  # 增加并发通话数
    max_calls_per_minute=50   # 增加每分钟通话数
)
```

#### 2. 优化语音识别
编辑 `optimized_speech_service.py`:
```python
speech_service = OptimizedSpeechService(
    max_workers=5,        # 增加处理线程数
    chunk_duration=1.0    # 减少处理块大小
)
```

#### 3. 数据库优化
```sql
-- 创建索引
CREATE INDEX CONCURRENTLY idx_transcriptions_call_id_timestamp 
ON transcriptions(call_id, timestamp);

-- 分析表统计信息
ANALYZE transcriptions;
ANALYZE call_sessions;
```

## 🔄 升级和维护

### 1. 系统升级
```bash
# 备份数据
docker-compose exec postgres pg_dump -U freeswitch freeswitch_calls > backup.sql

# 停止服务
docker-compose down

# 更新代码
git pull origin main

# 重新构建和启动
docker-compose up -d --build
```

### 2. 数据备份
```bash
# 创建备份脚本
cat > backup.sh << 'EOF'
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M%S)
docker-compose exec postgres pg_dump -U freeswitch freeswitch_calls > "backup_${DATE}.sql"
echo "Backup created: backup_${DATE}.sql"
EOF

chmod +x backup.sh

# 设置定时备份
crontab -e
# 添加: 0 2 * * * /path/to/backup.sh
```

### 3. 日志轮转
```bash
# 配置Docker日志轮转
cat > /etc/docker/daemon.json << 'EOF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m",
    "max-file": "3"
  }
}
EOF

# 重启Docker
sudo systemctl restart docker
```

## 📈 扩展部署

### 1. 负载均衡
使用Nginx进行负载均衡：

```nginx
upstream app_servers {
    server app1:8000;
    server app2:8000;
    server app3:8000;
}

server {
    listen 80;
    location / {
        proxy_pass http://app_servers;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
    
    location /ws/ {
        proxy_pass http://app_servers;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

### 2. 数据库集群
使用PostgreSQL主从复制：

```yaml
# docker-compose.cluster.yml
version: '3.8'
services:
  postgres-master:
    image: postgres:15
    environment:
      POSTGRES_REPLICATION_MODE: master
      POSTGRES_REPLICATION_USER: replicator
      POSTGRES_REPLICATION_PASSWORD: replicator_password
    volumes:
      - postgres_master_data:/var/lib/postgresql/data
      - ./postgres-master.conf:/etc/postgresql/postgresql.conf
  
  postgres-slave:
    image: postgres:15
    environment:
      POSTGRES_REPLICATION_MODE: slave
      POSTGRES_MASTER_HOST: postgres-master
      POSTGRES_REPLICATION_USER: replicator
      POSTGRES_REPLICATION_PASSWORD: replicator_password
    depends_on:
      - postgres-master
```

### 3. 微服务架构
将系统拆分为多个微服务：

- **呼叫服务**: 处理FreeSWITCH通话
- **转写服务**: 处理语音识别
- **API网关**: 统一API入口
- **监控服务**: 系统监控和告警
- **数据服务**: 数据库操作

## 🔒 安全配置

### 1. 网络安全
```bash
# 配置防火墙
ufw allow 22    # SSH
ufw allow 80    # HTTP
ufw allow 443   # HTTPS
ufw allow 5060  # SIP
ufw enable
```

### 2. 数据库安全
```sql
-- 创建只读用户
CREATE USER readonly_user WITH PASSWORD 'readonly_password';
GRANT CONNECT ON DATABASE freeswitch_calls TO readonly_user;
GRANT USAGE ON SCHEMA public TO readonly_user;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO readonly_user;
```

### 3. API安全
```python
# 添加API密钥认证
from fastapi import HTTPException, Depends, Header

async def verify_api_key(x_api_key: str = Header(...)):
    if x_api_key != "your-secret-api-key":
        raise HTTPException(status_code=401, detail="Invalid API key")
    return x_api_key

@app.get("/api/calls")
async def get_calls(api_key: str = Depends(verify_api_key)):
    # API逻辑
    pass
```

## 📞 技术支持

### 联系方式
- **邮箱**: support@example.com
- **GitHub**: https://github.com/your-username/freeswitch-call-transcription
- **文档**: https://docs.example.com

### 常见问题FAQ
1. **Q: 系统支持多少并发通话？**
   A: 默认支持5个并发通话，可根据硬件配置调整。

2. **Q: 语音转写准确率如何？**
   A: 使用Whisper模型，中文转写准确率约85-95%。

3. **Q: 如何提高转写速度？**
   A: 可以调整chunk_duration参数，使用更小的模型，或增加处理线程数。

4. **Q: 系统是否支持集群部署？**
   A: 支持，需要配置负载均衡和数据库集群。

5. **Q: 如何监控系统性能？**
   A: 访问 /monitor 页面查看实时监控面板。

---

**注意**: 本系统仅供学习和测试使用，生产环境部署请根据实际需求进行安全加固和性能优化。