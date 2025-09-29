# FreeSWITCH机器人外呼系统

一个基于FreeSWITCH的智能机器人外呼系统，支持人工坐席主动接入功能。

## 系统特性

### 🤖 智能机器人外呼
- 自动语音识别(ASR)和文本转语音(TTS)
- 智能对话逻辑处理
- 支持多种外呼话术模板
- 自动通话录音和记录

### 👥 人工坐席接入
- 坐席可主动接入机器人通话
- 实时查看通话状态和对话记录
- 三方会议模式（客户-机器人-坐席）
- 灵活的状态管理和转接控制

### 📊 管理监控
- 实时通话监控界面
- 通话历史记录查询
- 坐席状态管理
- 统计报表功能

### 🔧 技术架构
- **FreeSWITCH**: 核心通信引擎
- **Python Flask**: API服务和业务逻辑
- **Lua脚本**: FreeSWITCH扩展功能
- **SQLite**: 数据存储
- **Bootstrap**: 前端界面
- **ESL**: FreeSWITCH事件套接字

## 快速开始

### 系统要求
- Ubuntu 18.04+ / Debian 9+ / CentOS 7+
- Python 3.7+
- FreeSWITCH 1.10+
- 至少2GB内存
- SIP网关或运营商线路

### 一键部署
```bash
# 克隆项目
git clone <repository-url>
cd freeswitch-robot-call

# 运行部署脚本（需要root权限）
sudo ./deploy.sh
```

### 手动安装

#### 1. 安装FreeSWITCH
```bash
# 添加官方仓库
wget -O - https://files.freeswitch.org/repo/deb/debian-release/fsstretch-archive-keyring.asc | apt-key add -
echo "deb http://files.freeswitch.org/repo/deb/debian-release/ `lsb_release -sc` main" > /etc/apt/sources.list.d/freeswitch.list

# 安装FreeSWITCH
apt-get update
apt-get install -y freeswitch-meta-all
```

#### 2. 配置FreeSWITCH
```bash
# 复制配置文件
cp dialplan/outbound_robot.xml /etc/freeswitch/dialplan/
cp conf/autoload_configs/conference.conf.xml /etc/freeswitch/autoload_configs/
cp scripts/*.lua /usr/share/freeswitch/scripts/

# 重启FreeSWITCH
systemctl restart freeswitch
```

#### 3. 安装Python依赖
```bash
# 创建虚拟环境
python3 -m venv venv
source venv/bin/activate

# 安装依赖
pip install -r requirements.txt
```

#### 4. 启动API服务
```bash
cd api
python agent_control.py
```

#### 5. 配置Web服务器（可选）
```bash
# 使用Nginx代理
cp nginx.conf /etc/nginx/sites-available/robot-call
ln -s /etc/nginx/sites-available/robot-call /etc/nginx/sites-enabled/
systemctl reload nginx
```

## 使用指南

### 配置网关
编辑 `config/system_config.yaml` 文件，配置您的SIP网关信息：

```yaml
gateways:
  default:
    name: "your_gateway"
    host: "your.sip.provider.com"
    port: 5060
    username: "your_username"
    password: "your_password"
    register: true
```

### 启动机器人外呼

#### 通过API
```bash
curl -X POST http://localhost:8080/api/start_robot_call \
  -H "Content-Type: application/json" \
  -d '{"customer_number": "13800138000"}'
```

#### 通过Web界面
1. 访问 http://localhost/
2. 点击"发起外呼"按钮
3. 输入客户电话号码
4. 选择外呼话术
5. 点击"开始外呼"

### 坐席接入通话

#### 实时监控
- 登录Web管理界面
- 查看"活跃通话列表"
- 实时查看通话状态和对话记录

#### 主动接入
1. 在通话列表中找到目标通话
2. 点击"接入通话"按钮
3. 确认分机号码
4. 点击"确认接入"
5. 系统自动建立三方会议

### 通话状态说明
- **机器人通话中**: 机器人正在与客户对话
- **机器人暂停**: 机器人暂停，等待坐席接入
- **转接中**: 正在转接给人工坐席
- **三方通话**: 客户、机器人、坐席三方通话
- **人工通话**: 机器人已退出，客户与坐席对话

## API接口文档

### 获取活跃通话
```
GET /api/active_calls
```

### 坐席接入通话
```
POST /api/agent_takeover
{
  "session_id": "uuid",
  "agent_id": "agent001",
  "agent_number": "1001"
}
```

### 启动机器人外呼
```
POST /api/start_robot_call
{
  "customer_number": "13800138000",
  "call_script": "default"
}
```

### 更新通话状态
```
POST /api/update_call_state
{
  "session_id": "uuid",
  "call_state": "in_conference",
  "additional_data": {}
}
```

## 目录结构

```
├── api/                    # API服务
│   └── agent_control.py   # 主要API控制器
├── conf/                  # FreeSWITCH配置
│   └── autoload_configs/  # 自动加载配置
├── config/                # 系统配置
│   └── system_config.yaml # 主配置文件
├── dialplan/              # 拨号计划
│   └── outbound_robot.xml # 机器人外呼拨号计划
├── scripts/               # Lua脚本
│   ├── robot_handler.lua  # 机器人处理脚本
│   ├── pause_robot.lua    # 暂停机器人脚本
│   ├── setup_conference.lua # 会议设置脚本
│   └── call_state_manager.lua # 状态管理脚本
├── web/                   # Web界面
│   ├── index.html         # 主页面
│   └── app.js            # 前端JavaScript
├── deploy.sh              # 部署脚本
├── requirements.txt       # Python依赖
└── README.md             # 说明文档
```

## 故障排除

### 常见问题

#### FreeSWITCH无法启动
```bash
# 检查配置文件语法
fs_cli -x "reloadxml"

# 查看错误日志
tail -f /var/log/freeswitch/freeswitch.log
```

#### API服务连接失败
```bash
# 检查服务状态
systemctl status robot-call-api

# 查看服务日志
journalctl -u robot-call-api -f
```

#### 语音识别不工作
```bash
# 检查PocketSphinx安装
which pocketsphinx_continuous

# 检查音频设备
arecord -l
```

#### 外呼无法接通
1. 检查网关配置是否正确
2. 确认网络连接正常
3. 验证SIP账号信息
4. 查看FreeSWITCH日志

### 日志文件位置
- FreeSWITCH日志: `/var/log/freeswitch/`
- 系统日志: `/var/log/robot_calls/`
- API服务日志: `journalctl -u robot-call-api`

### 性能优化
- 调整FreeSWITCH并发数限制
- 优化数据库查询
- 配置负载均衡
- 使用Redis缓存

## 扩展开发

### 添加新的对话逻辑
编辑 `scripts/robot_handler.lua` 中的 `process_conversation` 函数。

### 集成第三方ASR/TTS
修改配置文件中的ASR/TTS引擎设置，并实现相应的接口。

### 自定义Web界面
修改 `web/` 目录下的HTML和JavaScript文件。

### 添加新的API接口
在 `api/agent_control.py` 中添加新的路由和处理函数。

## 许可证
MIT License

## 支持
如有问题，请提交Issue或联系技术支持。

## 更新日志
- v1.0.0: 初始版本，支持基本的机器人外呼和坐席接入功能