# ????

?????????????????Freeswitch???????

## ?? ??????

### 1. ?????

#### ????
- CPU: 4?
- ??: 8GB
- ??: 100GB SSD
- ??: 100Mbps

#### ????
- CPU: 8?+
- ??: 16GB+
- ??: 500GB SSD
- ??: 1Gbps

### 2. ????

#### 2.1 ??????

```bash
# CentOS/RHEL
sudo yum update -y
sudo yum install -y python3 python3-pip redis mongodb nginx

# Ubuntu/Debian
sudo apt update
sudo apt install -y python3 python3-pip redis-server mongodb nginx
```

#### 2.2 ??Freeswitch

???????https://freeswitch.org/confluence/display/FREESWITCH/Installation

```bash
# CentOS 7
sudo yum install -y https://files.freeswitch.org/repo/yum/centos-release/freeswitch-release-repo-0-1.noarch.rpm epel-release
sudo yum install -y freeswitch-config-vanilla freeswitch-lang-* freeswitch-sounds-*
```

### 3. ????

#### 3.1 ??????

```bash
sudo mkdir -p /opt/freeswitch-transcription
sudo chown -R $USER:$USER /opt/freeswitch-transcription
cd /opt/freeswitch-transcription
```

#### 3.2 ????

```bash
# ???????
git clone <repository_url> .

# ??????
python3 -m venv venv
source venv/bin/activate

# ????
pip install -r requirements.txt
```

#### 3.3 ??????

```bash
cp .env.example .env
vim .env  # ????
```

?????????

```bash
# Freeswitch
FREESWITCH_HOST=127.0.0.1
FREESWITCH_ESL_PORT=8021
FREESWITCH_ESL_PASSWORD=your_secure_password

# WebSocket
WS_HOST=0.0.0.0
WS_PORT=8765

# Flask API
API_HOST=127.0.0.1  # ??nginx????????
API_PORT=5000

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# MongoDB
MONGO_URI=mongodb://localhost:27017/
MONGO_DB=freeswitch_transcription

# ASR???????????
ASR_PROVIDER=aliyun
ALIYUN_ACCESS_KEY_ID=your_key
ALIYUN_ACCESS_KEY_SECRET=your_secret
ALIYUN_APP_KEY=your_app_key
```

### 4. ??Systemd??

#### 4.1 ??????

```bash
sudo vim /etc/systemd/system/freeswitch-transcription.service
```

???

```ini
[Unit]
Description=Freeswitch Realtime Transcription System
After=network.target redis.service mongodb.service freeswitch.service

[Service]
Type=simple
User=freeswitch
Group=freeswitch
WorkingDirectory=/opt/freeswitch-transcription/backend
Environment="PATH=/opt/freeswitch-transcription/venv/bin"
ExecStart=/opt/freeswitch-transcription/venv/bin/python main.py
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

#### 4.2 ????

```bash
sudo systemctl daemon-reload
sudo systemctl enable freeswitch-transcription
sudo systemctl start freeswitch-transcription
sudo systemctl status freeswitch-transcription
```

### 5. ??Nginx????

#### 5.1 ??Nginx??

```bash
sudo vim /etc/nginx/conf.d/transcription.conf
```

???

```nginx
upstream transcription_backend {
    server 127.0.0.1:5000;
}

upstream transcription_ws {
    server 127.0.0.1:8766;
}

server {
    listen 80;
    server_name your-domain.com;

    # ????HTTPS
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;

    # SSL????
    ssl_certificate /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # ??
    access_log /var/log/nginx/transcription_access.log;
    error_log /var/log/nginx/transcription_error.log;

    # ????
    location / {
        root /opt/freeswitch-transcription/frontend;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # API??
    location /api/ {
        proxy_pass http://transcription_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket??
    location /ws/ {
        proxy_pass http://transcription_ws;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 86400;
    }
}
```

#### 5.2 ?????Nginx

```bash
sudo nginx -t
sudo systemctl restart nginx
```

### 6. ?????

```bash
# CentOS/RHEL
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --permanent --add-port=8765/tcp  # Freeswitch WebSocket
sudo firewall-cmd --reload

# Ubuntu/Debian (UFW)
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8765/tcp
sudo ufw reload
```

### 7. ?????

#### 7.1 ??????

```bash
# ??????
sudo journalctl -u freeswitch-transcription -f

# Nginx??
sudo tail -f /var/log/nginx/transcription_access.log
sudo tail -f /var/log/nginx/transcription_error.log

# Freeswitch??
sudo tail -f /var/log/freeswitch/freeswitch.log
```

#### 7.2 ??????

```bash
sudo vim /etc/logrotate.d/freeswitch-transcription
```

???

```
/opt/freeswitch-transcription/logs/*.log {
    daily
    rotate 30
    compress
    delaycompress
    notifempty
    create 0640 freeswitch freeswitch
    sharedscripts
    postrotate
        systemctl reload freeswitch-transcription > /dev/null 2>&1 || true
    endscript
}
```

### 8. ????

#### 8.1 Redis??

```bash
sudo vim /etc/redis.conf
```

?????

```
maxmemory 2gb
maxmemory-policy allkeys-lru
save ""  # ??RDB??????AOF
appendonly yes
```

#### 8.2 MongoDB??

?????

```javascript
use freeswitch_transcription

db.sessions.createIndex({ "session_id": 1 })
db.sessions.createIndex({ "start_time": -1 })
db.transcriptions.createIndex({ "session_id": 1, "timestamp": 1 })
```

#### 8.3 ????

```bash
# ?????????
sudo vim /etc/security/limits.conf
```

???

```
* soft nofile 65535
* hard nofile 65535
```

### 9. ????

#### 9.1 ??????

```bash
sudo vim /opt/scripts/backup_transcription.sh
```

???

```bash
#!/bin/bash

BACKUP_DIR="/backup/transcription"
DATE=$(date +%Y%m%d_%H%M%S)

# ??MongoDB
mongodump --db freeswitch_transcription --out "$BACKUP_DIR/mongodb_$DATE"

# ??????
cp /opt/freeswitch-transcription/.env "$BACKUP_DIR/config_$DATE.env"

# ??
tar -czf "$BACKUP_DIR/backup_$DATE.tar.gz" "$BACKUP_DIR/mongodb_$DATE" "$BACKUP_DIR/config_$DATE.env"

# ????????30??
find $BACKUP_DIR -name "backup_*.tar.gz" -mtime +30 -delete

echo "Backup completed: $DATE"
```

#### 9.2 ??????

```bash
sudo crontab -e
```

???

```
0 2 * * * /opt/scripts/backup_transcription.sh >> /var/log/backup.log 2>&1
```

### 10. ?????????

?????????????

1. **????**: ??HAProxy?Nginx??????????
2. **Redis??**: ??Redis Sentinel?Cluster
3. **MongoDB???**: ??MongoDB???
4. **?????**: ??Docker?Kubernetes

### 11. SSL??

??Let's Encrypt???????

```bash
sudo yum install certbot python3-certbot-nginx -y
sudo certbot --nginx -d your-domain.com
```

### 12. ????

1. **????????**
2. **??SELinux/AppArmor**
3. **??????**
4. **?????**
5. **??SSH??**
6. **??fail2ban**

## ?? ????

?????????

```bash
#!/bin/bash

# ??????
systemctl is-active freeswitch-transcription
systemctl is-active redis
systemctl is-active mongodb
systemctl is-active nginx

# ????
netstat -tlnp | grep -E ':(5000|8765|8766|80|443)'

# ??API
curl -f http://localhost:5000/api/health || exit 1
```

## ?? ????

?????????????????

1. ????
2. ????
3. ????
4. ??Issue

---

??????
