# FS机器人外呼系统部署指南

## 系统要求

### 硬件要求
- CPU: 4核心以上
- 内存: 8GB以上
- 存储: 100GB以上SSD
- 网络: 100Mbps以上带宽

### 软件要求
- Docker 20.10+
- Docker Compose 2.0+
- Linux系统（推荐Ubuntu 20.04+或CentOS 8+）

## 快速部署

### 1. 克隆项目
```bash
git clone <repository-url>
cd fs-robot-call-system
```

### 2. 配置环境变量
```bash
cp .env.example .env
# 编辑.env文件，配置数据库密码、API密钥等
```

### 3. 启动服务
```bash
cd docker
docker-compose up -d
```

### 4. 验证部署
```bash
# 检查服务状态
docker-compose ps

# 查看日志
docker-compose logs -f backend
```

## 详细配置

### FreeSwitch配置

#### 1. SIP网关配置
编辑 `freeswitch/conf/sip_profiles.xml`：
```xml
<gateway name="outbound">
  <param name="username" data="your_sip_username"/>
  <param name="password" data="your_sip_password"/>
  <param name="realm" data="your_sip_provider.com"/>
  <param name="proxy" data="your_sip_provider.com"/>
  <param name="register" data="true"/>
</gateway>
```

#### 2. 拨号计划配置
根据实际需求修改 `freeswitch/conf/dialplan.xml`

#### 3. 录音配置
确保录音目录权限正确：
```bash
sudo chown -R freeswitch:freeswitch /var/lib/freeswitch/recordings
sudo chmod 755 /var/lib/freeswitch/recordings
```

### 数据库配置

#### 1. 连接配置
在 `.env` 文件中配置：
```
DATABASE_URL=postgresql://postgres:password@localhost:5432/robot_call_db
```

#### 2. 初始化数据
```bash
# 进入数据库容器
docker exec -it fs_robot_postgres psql -U postgres -d robot_call_db

# 执行初始化脚本
\i /docker-entrypoint-initdb.d/init.sql
```

### 后端服务配置

#### 1. 环境变量
```bash
# 基本配置
DEBUG=false
HOST=0.0.0.0
PORT=8000

# 数据库
DATABASE_URL=postgresql://postgres:password@postgres:5432/robot_call_db

# Redis
REDIS_URL=redis://redis:6379/0

# FreeSwitch
FREESWITCH_HOST=freeswitch
FREESWITCH_PORT=8021
FREESWITCH_PASSWORD=ClueCon

# 外呼配置
OUTBOUND_GATEWAY=outbound
MAX_CONCURRENT_CALLS=100

# AI服务
OPENAI_API_KEY=your_openai_key
AZURE_SPEECH_KEY=your_azure_key
AZURE_SPEECH_REGION=eastasia
```

#### 2. 启动服务
```bash
cd backend
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### 前端配置

#### 1. 构建配置
```bash
cd frontend
npm install
npm run build
```

#### 2. Nginx配置
确保 `docker/nginx.conf` 中的代理配置正确

## 监控和维护

### 1. 日志管理
```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f backend
docker-compose logs -f freeswitch

# 日志轮转配置
# 在docker-compose.yml中添加logging配置
```

### 2. 性能监控
```bash
# 查看资源使用情况
docker stats

# 查看系统负载
htop
iostat -x 1
```

### 3. 备份策略
```bash
# 数据库备份
docker exec fs_robot_postgres pg_dump -U postgres robot_call_db > backup_$(date +%Y%m%d).sql

# 录音文件备份
tar -czf recordings_backup_$(date +%Y%m%d).tar.gz /var/lib/freeswitch/recordings/
```

### 4. 更新部署
```bash
# 拉取最新代码
git pull origin main

# 重新构建并启动
docker-compose down
docker-compose build
docker-compose up -d
```

## 故障排除

### 1. 常见问题

#### FreeSwitch无法启动
```bash
# 检查端口占用
netstat -tulpn | grep :5060

# 检查配置文件语法
docker exec fs_robot_freeswitch fs_cli -x "reloadxml"
```

#### 数据库连接失败
```bash
# 检查数据库状态
docker exec fs_robot_postgres pg_isready -U postgres

# 检查连接字符串
docker exec fs_robot_backend python -c "from app.models.database import engine; print(engine.url)"
```

#### WebSocket连接失败
```bash
# 检查Nginx配置
docker exec fs_robot_nginx nginx -t

# 检查防火墙设置
ufw status
```

### 2. 性能优化

#### 数据库优化
```sql
-- 创建索引
CREATE INDEX CONCURRENTLY idx_call_records_composite ON call_records(status, start_time);

-- 分析查询性能
EXPLAIN ANALYZE SELECT * FROM call_records WHERE status = 'active';
```

#### FreeSwitch优化
```xml
<!-- 在vars.xml中调整并发数 -->
<X-PRE-PROCESS cmd="set" data="max_sessions=1000"/>
<X-PRE-PROCESS cmd="set" data="sessions_per_second=30"/>
```

## 安全配置

### 1. 防火墙设置
```bash
# 开放必要端口
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 5060/udp
ufw allow 16384:16394/udp

# 限制数据库访问
ufw deny 5432/tcp
```

### 2. SSL证书配置
```bash
# 使用Let's Encrypt
certbot --nginx -d your-domain.com

# 或者使用自签名证书
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout docker/ssl/key.pem \
  -out docker/ssl/cert.pem
```

### 3. 访问控制
```nginx
# 在nginx.conf中添加IP白名单
location /admin/ {
    allow 192.168.1.0/24;
    deny all;
    proxy_pass http://backend;
}
```

## 扩展部署

### 1. 集群部署
```yaml
# docker-compose.cluster.yml
version: '3.8'
services:
  backend:
    deploy:
      replicas: 3
      resources:
        limits:
          cpus: '1'
          memory: 1G
```

### 2. 负载均衡
```nginx
upstream backend_cluster {
    server backend1:8000;
    server backend2:8000;
    server backend3:8000;
}
```

### 3. 数据库主从复制
```yaml
postgres_master:
  image: postgres:15
  environment:
    POSTGRES_REPLICATION_MODE: master
    POSTGRES_REPLICATION_USER: replicator
    POSTGRES_REPLICATION_PASSWORD: replicator_password

postgres_slave:
  image: postgres:15
  environment:
    POSTGRES_REPLICATION_MODE: slave
    POSTGRES_MASTER_HOST: postgres_master
```

## 联系支持

如果在部署过程中遇到问题，请：

1. 查看日志文件
2. 检查配置文件
3. 参考故障排除章节
4. 联系技术支持团队