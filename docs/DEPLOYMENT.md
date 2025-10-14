# FreeSWITCH 机器人呼出系统部署指南

## 系统要求

- **操作系统**: CentOS 7+ / Ubuntu 18.04+
- **Python**: 3.8+
- **FreeSWITCH**: 1.10+
- **MySQL**: 5.7+
- **Redis**: 5.0+
- **内存**: 最低 4GB，建议 8GB+
- **磁盘**: 最低 20GB（用于存储录音文件）

## 安装步骤

### 1. 安装 FreeSWITCH

#### CentOS/RHEL
```bash
# 添加 FreeSWITCH 仓库
yum install -y https://files.freeswitch.org/repo/yum/centos-release/freeswitch-release-repo-0-1.noarch.rpm

# 安装 FreeSWITCH
yum install -y freeswitch-config-vanilla freeswitch-lang-* freeswitch-sounds-*

# 启动服务
systemctl enable freeswitch
systemctl start freeswitch
```

#### Ubuntu/Debian
```bash
# 添加 FreeSWITCH 仓库
wget -O - https://files.freeswitch.org/repo/deb/debian-release/fsstretch-archive-keyring.asc | apt-key add -
echo "deb http://files.freeswitch.org/repo/deb/debian-release/ `lsb_release -sc` main" > /etc/apt/sources.list.d/freeswitch.list

# 安装 FreeSWITCH
apt-get update && apt-get install -y freeswitch-meta-all

# 启动服务
systemctl enable freeswitch
systemctl start freeswitch
```

### 2. 安装 MySQL

```bash
# CentOS
yum install -y mysql-server

# Ubuntu
apt-get install -y mysql-server

# 启动服务
systemctl enable mysqld
systemctl start mysqld

# 初始化数据库
mysql -u root -p < scripts/init_db.sql
```

### 3. 安装 Redis

```bash
# CentOS
yum install -y redis

# Ubuntu
apt-get install -y redis-server

# 启动服务
systemctl enable redis
systemctl start redis
```

### 4. 安装 Python 环境

```bash
# 安装 Python 3.8+
python3 --version

# 创建虚拟环境
python3 -m venv venv
source venv/bin/activate

# 安装依赖
pip install --upgrade pip
pip install -r requirements.txt
```

### 5. 配置系统

#### 5.1 配置 FreeSWITCH

1. 复制拨号计划配置：
```bash
cp config/freeswitch/dialplan.xml /etc/freeswitch/dialplan/
```

2. 编辑 FreeSWITCH 配置文件：
```bash
vi /etc/freeswitch/autoload_configs/event_socket.conf.xml
```

确保以下配置正确：
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

3. 重载 FreeSWITCH 配置：
```bash
fs_cli -x "reloadxml"
```

#### 5.2 配置应用

编辑 `config/config.yaml`，配置以下内容：

1. **FreeSWITCH 连接信息**
2. **数据库连接信息**
3. **ASR 服务配置**（百度/阿里云等）
4. **坐席信息**

### 6. 启动服务

```bash
# 激活虚拟环境
source venv/bin/activate

# 启动主服务
python src/main.py
```

## 使用 Systemd 管理服务

创建服务文件 `/etc/systemd/system/freeswitch-bot.service`：

```ini
[Unit]
Description=FreeSWITCH Bot Service
After=network.target mysql.service redis.service freeswitch.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/freeswitch-bot
Environment="PATH=/opt/freeswitch-bot/venv/bin"
ExecStart=/opt/freeswitch-bot/venv/bin/python /opt/freeswitch-bot/src/main.py
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启用并启动服务：
```bash
systemctl daemon-reload
systemctl enable freeswitch-bot
systemctl start freeswitch-bot
systemctl status freeswitch-bot
```

## 配置 Nginx 反向代理（可选）

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://127.0.0.1:5000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

## 防火墙配置

```bash
# 开放必要端口
firewall-cmd --permanent --add-port=5000/tcp  # Web API
firewall-cmd --permanent --add-port=8021/tcp  # FreeSWITCH ESL
firewall-cmd --permanent --add-port=5060/udp  # SIP
firewall-cmd --permanent --add-port=16384-32768/udp  # RTP
firewall-cmd --reload
```

## 监控和日志

### 查看日志
```bash
# 应用日志
tail -f logs/outbound_bot.log

# FreeSWITCH 日志
tail -f /var/log/freeswitch/freeswitch.log

# 系统服务日志
journalctl -u freeswitch-bot -f
```

### 监控指标
- 访问 `http://your-server:5000/health` 检查服务健康状态
- 访问 `http://your-server:5000/api/queue/status` 查看队列状态
- 访问 `http://your-server:5000/api/calls/active` 查看活动通话

## 故障排除

### 1. FreeSWITCH 连接失败
- 检查 Event Socket 配置
- 确认密码正确
- 检查防火墙设置

### 2. 数据库连接失败
- 确认 MySQL 服务正在运行
- 检查数据库用户权限
- 验证连接参数

### 3. ASR 识别失败
- 检查 API 密钥配置
- 确认网络连接正常
- 查看 ASR 服务配额

### 4. 转接失败
- 检查坐席状态
- 确认拨号计划配置正确
- 查看 FreeSWITCH 日志

## 性能优化

### 1. 数据库优化
```sql
-- 定期优化表
OPTIMIZE TABLE call_records;
OPTIMIZE TABLE intent_logs;

-- 添加分区（按月）
ALTER TABLE call_records PARTITION BY RANGE (TO_DAYS(call_time)) (
    PARTITION p202401 VALUES LESS THAN (TO_DAYS('2024-02-01')),
    PARTITION p202402 VALUES LESS THAN (TO_DAYS('2024-03-01'))
);
```

### 2. Redis 优化
```bash
# 配置持久化
redis-cli CONFIG SET save "900 1 300 10 60 10000"

# 设置最大内存
redis-cli CONFIG SET maxmemory 2gb
redis-cli CONFIG SET maxmemory-policy allkeys-lru
```

### 3. FreeSWITCH 优化
```xml
<!-- 调整并发限制 -->
<param name="max-sessions" value="1000"/>
<param name="sessions-per-second" value="30"/>
```

## 备份和恢复

### 备份数据库
```bash
# 创建备份脚本
cat > /opt/backup.sh << 'EOF'
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M%S)
mysqldump -u root -p freeswitch_bot > /backup/freeswitch_bot_$DATE.sql
find /backup -name "*.sql" -mtime +7 -delete
EOF

# 添加定时任务
crontab -e
0 2 * * * /opt/backup.sh
```

### 备份录音文件
```bash
# 使用 rsync 同步到备份服务器
rsync -avz /var/lib/freeswitch/recordings/ backup-server:/backup/recordings/
```

## 安全建议

1. **修改默认密码**
   - FreeSWITCH Event Socket 密码
   - MySQL root 密码
   - Redis 密码

2. **限制访问**
   - 使用防火墙限制端口访问
   - 配置 IP 白名单

3. **启用 SSL/TLS**
   - 为 Web API 配置 HTTPS
   - 启用 SIP TLS

4. **定期更新**
   - 及时更新系统补丁
   - 升级依赖包版本

5. **审计日志**
   - 启用详细日志记录
   - 定期审查日志文件