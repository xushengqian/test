# mod_unimrcp - FreeSWITCH UniMRCP Module

mod_unimrcp 是一个 FreeSWITCH 模块，提供通过 MRCP（媒体资源控制协议）进行语音识别(ASR)和语音合成(TTS)的功能。

## 功能特性

- **ASR (自动语音识别)**: 通过 MRCP 协议连接语音识别服务器
- **TTS (语音合成)**: 通过 MRCP 协议连接语音合成服务器
- **MRCPv1 和 MRCPv2 支持**: 兼容不同版本的 MRCP 服务器
- **多配置文件支持**: 可同时配置多个 MRCP 服务器
- **灵活的音频编解码器**: 支持 PCMU, PCMA, L16 等编解码器

## 目录结构

```
mod_unimrcp/
├── src/
│   ├── mod_unimrcp.c    # 主模块代码
│   ├── asr.c            # ASR 接口实现
│   └── tts.c            # TTS 接口实现
├── include/
│   └── mod_unimrcp.h    # 头文件
├── conf/
│   ├── unimrcp.conf.xml     # FreeSWITCH 模块配置
│   └── unimrcp_client.xml   # UniMRCP 客户端配置
├── Makefile
└── README.md
```

## 依赖项

### 编译依赖

- FreeSWITCH 开发头文件
- UniMRCP 客户端库 (libunimrcp)
- Apache Portable Runtime (APR)
- APR-Util

### 安装依赖 (Debian/Ubuntu)

```bash
# 安装 APR
sudo apt-get install libapr1-dev libaprutil1-dev

# 安装 UniMRCP (需要从源码编译)
# 参见: https://www.unimrcp.org/
```

### 安装 UniMRCP

```bash
# 下载 UniMRCP 源码
wget https://www.unimrcp.org/project/component-view/unimrcp-1.8.0-tar-gz/download -O unimrcp-1.8.0.tar.gz
tar -xzf unimrcp-1.8.0.tar.gz
cd unimrcp-1.8.0

# 配置和编译
./configure --prefix=/usr/local/unimrcp
make
sudo make install
```

## 编译安装

```bash
# 编译模块
make

# 安装模块和配置文件
sudo make install

# 或者指定自定义路径
make FREESWITCH_PREFIX=/opt/freeswitch UNIMRCP_PREFIX=/opt/unimrcp
sudo make install FREESWITCH_PREFIX=/opt/freeswitch
```

## 配置

### 1. 启用模块

在 FreeSWITCH 的 `conf/autoload_configs/modules.conf.xml` 中添加:

```xml
<load module="mod_unimrcp"/>
```

### 2. 配置 MRCP 服务器

编辑 `conf/autoload_configs/unimrcp.conf.xml`:

```xml
<configuration name="unimrcp.conf" description="UniMRCP Module Configuration">
  <settings>
    <param name="default-asr-profile" value="default"/>
    <param name="default-tts-profile" value="default"/>
  </settings>

  <profiles>
    <profile name="default">
      <param name="server-ip" value="your-mrcp-server-ip"/>
      <param name="server-port" value="8060"/>
      <param name="mrcp-version" value="2"/>
      <param name="codec" value="PCMU"/>
      <param name="sample-rate" value="8000"/>
    </profile>
  </profiles>
</configuration>
```

## 使用方法

### ASR (语音识别)

在拨号计划中使用:

```xml
<!-- 使用默认语法进行语音识别 -->
<action application="play_and_detect_speech" 
        data="say:请说出您的需求 detect:unimrcp builtin:speech/transcribe"/>

<!-- 使用 SRGS 语法 -->
<action application="play_and_detect_speech" 
        data="say:请说是或否 detect:unimrcp {start-input-timers=false}builtin:grammar/boolean"/>
```

Lua 脚本示例:

```lua
-- 打开 ASR 会话
session:setVariable("asr_engine", "unimrcp")

-- 执行语音识别
local result = session:playAndDetectSpeech(
    "say:请说出您想办理的业务",
    "unimrcp",
    "builtin:speech/transcribe"
)

if result then
    freeswitch.consoleLog("INFO", "识别结果: " .. result)
end
```

### TTS (语音合成)

在拨号计划中使用:

```xml
<!-- 基本 TTS -->
<action application="speak" data="unimrcp|default|您好，欢迎致电客服中心"/>

<!-- 使用 SSML -->
<action application="speak" data="unimrcp|default|<speak>您好，<break time='500ms'/>欢迎致电客服中心</speak>"/>
```

Lua 脚本示例:

```lua
-- 设置 TTS 引擎
session:set_tts_params("unimrcp", "default")

-- 播放合成语音
session:speak("您好，欢迎致电客服中心")

-- 使用 SSML
session:speak("<speak>您好，<prosody rate='slow'>请稍等</prosody></speak>")
```

### API 命令

```bash
# 查看模块状态
fs_cli -x "unimrcp status"

# 列出所有配置文件
fs_cli -x "unimrcp profile"

# 查看特定配置文件详情
fs_cli -x "unimrcp profile default"

# 重新加载配置
fs_cli -x "unimrcp reload"
```

## MRCP 服务器兼容性

本模块支持以下 MRCP 服务器:

| 服务器 | MRCPv1 | MRCPv2 | 备注 |
|--------|--------|--------|------|
| Nuance | ✅ | ✅ | 完全支持 |
| Lumenvox | ✅ | ✅ | 完全支持 |
| Google Cloud Speech | - | ✅ | 需要 MRCP 网关 |
| 百度语音 | - | ✅ | 需要 MRCP 网关 |
| 阿里云语音 | - | ✅ | 需要 MRCP 网关 |
| 讯飞语音 | - | ✅ | 需要 MRCP 网关 |

## 常见问题

### 1. 模块加载失败

确保 UniMRCP 库路径在 LD_LIBRARY_PATH 中:

```bash
export LD_LIBRARY_PATH=/usr/local/unimrcp/lib:$LD_LIBRARY_PATH
```

或者添加到 `/etc/ld.so.conf.d/unimrcp.conf`:

```
/usr/local/unimrcp/lib
```

然后运行: `sudo ldconfig`

### 2. 连接 MRCP 服务器失败

- 检查服务器 IP 和端口配置
- 确保防火墙允许 SIP (默认 8060) 和 RTP (4000-5000) 端口
- 查看 FreeSWITCH 日志获取详细错误信息

### 3. ASR 无法识别

- 确保音频格式正确 (采样率、编解码器)
- 检查语法是否正确加载
- 调整 VAD (语音活动检测) 参数

## 日志调试

启用详细日志:

```xml
<!-- 在 unimrcp.conf.xml 中设置 -->
<param name="log-level" value="debug"/>
```

查看日志:

```bash
# 实时查看日志
fs_cli -x "console loglevel debug"
tail -f /usr/local/freeswitch/log/freeswitch.log | grep unimrcp
```

## 许可证

Apache License 2.0

## 参考资料

- [UniMRCP 官方文档](https://www.unimrcp.org/documentation)
- [FreeSWITCH 文档](https://freeswitch.org/confluence/display/FREESWITCH/)
- [MRCP 协议规范 (RFC 6787)](https://tools.ietf.org/html/rfc6787)
