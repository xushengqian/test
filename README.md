# FreeSWITCH 机器人呼出转人工系统

这是一个基于 FreeSWITCH 的智能外呼系统，支持机器人自动呼出，并能识别用户的转人工意图，自动转接到人工坐席。

## 功能特性

- 🤖 机器人自动外呼
- 🎤 语音识别（ASR）
- 🧠 意图识别（转人工意图判断）
- 📞 智能转接人工坐席
- 📊 通话记录和状态跟踪

## 系统架构

```
┌─────────────┐     ┌──────────────┐     ┌───────────┐
│  FreeSWITCH │────▶│  Python App  │────▶│    ASR    │
│   Server    │◀────│   (ESL)      │◀────│  Service  │
└─────────────┘     └──────────────┘     └───────────┘
       │                    │
       │                    │
       ▼                    ▼
┌─────────────┐     ┌──────────────┐
│   客户      │     │  人工坐席    │
└─────────────┘     └──────────────┘
```

## 目录结构

```
.
├── README.md
├── requirements.txt
├── config/
│   ├── config.yaml              # 系统配置
│   └── freeswitch/              # FreeSWITCH 配置文件
│       ├── dialplan.xml         # 拨号计划
│       └── sip_profiles.xml     # SIP 配置
├── src/
│   ├── main.py                  # 主程序入口
│   ├── outbound_bot.py          # 外呼机器人
│   ├── asr_service.py           # 语音识别服务
│   ├── intent_detector.py       # 意图识别
│   ├── transfer_handler.py      # 转接处理
│   └── utils/
│       ├── logger.py            # 日志工具
│       └── database.py          # 数据库操作
└── tests/                        # 测试文件
```

## 快速开始

### 1. 安装依赖

```bash
pip install -r requirements.txt
```

### 2. 配置系统

编辑 `config/config.yaml` 文件，配置 FreeSWITCH 连接信息和其他参数。

### 3. 启动服务

```bash
python src/main.py
```

### 4. 测试外呼

```bash
python src/test_call.py --number 13800138000
```

## 配置说明

详见 `config/config.yaml` 文件中的注释。

## 开发说明

- Python 版本: 3.8+
- FreeSWITCH 版本: 1.10+
- 依赖库见 requirements.txt

## License

MIT