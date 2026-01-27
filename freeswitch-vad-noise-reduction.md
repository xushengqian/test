# FreeSWITCH VAD（语音活动检测）与降噪配置指南

## 目录

1. [VAD 概述](#1-vad-概述)
2. [FreeSWITCH 内置 VAD 配置](#2-freeswitch-内置-vad-配置)
3. [降噪模块配置](#3-降噪模块配置)
4. [mod_oreka 与 VAD](#4-mod_oreka-与-vad)
5. [WebRTC 集成中的 VAD](#5-webrtc-集成中的-vad)
6. [实战配置示例](#6-实战配置示例)
7. [性能优化建议](#7-性能优化建议)
8. [故障排除](#8-故障排除)

---

## 1. VAD 概述

### 1.1 什么是 VAD

VAD（Voice Activity Detection，语音活动检测）是一种用于检测音频信号中是否存在人声的技术。在 VoIP 和电话系统中，VAD 主要用于：

- **带宽优化**：在静音期间不传输数据包
- **录音优化**：只录制有语音的部分
- **语音识别**：确定语音的起止点
- **回声消除**：辅助判断语音方向

### 1.2 VAD 工作原理

VAD 通常基于以下特征进行检测：

- **能量检测**：语音信号的能量高于背景噪声
- **过零率**：语音信号的过零率特征
- **频谱特征**：语音的频谱分布与噪声不同
- **统计模型**：基于 GMM、HMM 或深度学习的模型

---

## 2. FreeSWITCH 内置 VAD 配置

### 2.1 通道变量方式启用 VAD

在 dialplan 或 ESL 中设置以下变量：

```xml
<!-- 在 dialplan 中启用 VAD -->
<action application="set" data="vad_thresh=50"/>
<action application="set" data="vad_mode=2"/>
<action application="set" data="suppress_cng=true"/>
```

### 2.2 VAD 相关通道变量

| 变量名 | 说明 | 取值范围 |
|--------|------|----------|
| `vad_thresh` | VAD 能量阈值 | 0-100，默认 25 |
| `vad_mode` | VAD 模式 | 0-3，越大越激进 |
| `suppress_cng` | 抑制舒适噪声生成 | true/false |
| `send_silence_when_idle` | 静音时发送静音包 | true/false |

### 2.3 SIP Profile 中的 VAD 配置

在 `conf/sip_profiles/internal.xml` 或 `external.xml` 中：

```xml
<profile name="internal">
  <settings>
    <!-- 启用 VAD -->
    <param name="vad" value="both"/>
    <!-- VAD 模式: in, out, both, none -->
    
    <!-- 舒适噪声配置 -->
    <param name="suppress-cng" value="true"/>
    
    <!-- 静音检测超时（秒） -->
    <param name="rtp-timeout-sec" value="300"/>
    <param name="rtp-hold-timeout-sec" value="1800"/>
  </settings>
</profile>
```

### 2.4 ESL 命令控制 VAD

通过 ESL 动态控制通道的 VAD：

```bash
# 启用 VAD
uuid_setvar <uuid> vad_thresh 50
uuid_setvar <uuid> vad_mode 2

# 检测语音活动事件
uuid_setvar <uuid> fire_vad_events true
```

Java ESL 示例：

```java
// 设置 VAD 参数
eslClient.sendAsyncApiCommand("uuid_setvar " + uuid + " vad_thresh 50", null);
eslClient.sendAsyncApiCommand("uuid_setvar " + uuid + " vad_mode 2", null);
eslClient.sendAsyncApiCommand("uuid_setvar " + uuid + " fire_vad_events true", null);

// 订阅 VAD 事件
eslClient.setEventSubscriptions(ESLClientManager.MYEVENTS, "CUSTOM vad::start vad::stop");
```

---

## 3. 降噪模块配置

### 3.1 mod_speex 降噪

FreeSWITCH 使用 Speex 编解码器提供内置降噪功能。

**启用 mod_speex：**

```xml
<!-- conf/autoload_configs/modules.conf.xml -->
<load module="mod_speex"/>
```

**配置降噪参数：**

```xml
<!-- conf/autoload_configs/speex.conf.xml -->
<configuration name="speex.conf" description="Speex Configuration">
  <settings>
    <!-- 预处理器设置 -->
    <param name="quality" value="8"/>
    <param name="complexity" value="2"/>
    <param name="vbr" value="true"/>
    <param name="vad" value="true"/>
    <param name="abr" value="true"/>
    
    <!-- 降噪设置 -->
    <param name="denoise" value="true"/>
    <param name="noise-suppress" value="-30"/>
    
    <!-- 自动增益控制 -->
    <param name="agc" value="true"/>
    <param name="agc-level" value="8000"/>
  </settings>
</configuration>
```

### 3.2 mod_opus 降噪（推荐）

Opus 编解码器提供更好的音质和降噪能力：

```xml
<!-- conf/autoload_configs/opus.conf.xml -->
<configuration name="opus.conf" description="Opus Configuration">
  <settings>
    <param name="use-vbr" value="1"/>
    <param name="use-dtx" value="1"/>  <!-- 非连续传输，类似 VAD -->
    <param name="complexity" value="10"/>
    <param name="packet-loss-percent" value="15"/>
    <param name="keep-fec-enabled" value="1"/>
    <param name="use-jb-lookahead" value="1"/>
    
    <!-- 最大带宽设置 -->
    <param name="maxaveragebitrate" value="64000"/>
    <param name="maxplaybackrate" value="48000"/>
    <param name="sprop-maxcapturerate" value="16000"/>
  </settings>
</configuration>
```

### 3.3 使用 RNNoise 深度学习降噪

RNNoise 是一个基于深度学习的实时降噪库。虽然 FreeSWITCH 没有原生支持，但可以通过以下方式集成：

**方案一：使用 mod_audio_fork 外部处理**

```lua
-- Lua 脚本：将音频转发到外部 RNNoise 处理服务
session:execute("audio_fork", "start ws://rnnoise-server:8080/denoise")
```

**方案二：使用 mod_portaudio 配合 PulseAudio 降噪**

```bash
# 安装 PulseAudio 和 RNNoise 插件
apt install pulseaudio-module-rnnoise

# 配置 PulseAudio
pactl load-module module-rnnoise
```

### 3.4 mod_dptools 音频处理

使用内置的音频处理应用：

```xml
<!-- 降噪应用 -->
<action application="denoise" data=""/>

<!-- 自动增益控制 -->
<action application="agc" data=""/>

<!-- 音量调整 -->
<action application="set" data="read_vol=2"/>
<action application="set" data="write_vol=2"/>
```

---

## 4. mod_oreka 与 VAD

mod_oreka 是 FreeSWITCH 的录音模块，支持 VAD 触发录音：

```xml
<!-- conf/autoload_configs/oreka.conf.xml -->
<configuration name="oreka.conf" description="Oreka Recording">
  <settings>
    <param name="sip-server-addr" value="oreka-server"/>
    <param name="sip-server-port" value="5060"/>
    <param name="enable-vad" value="true"/>
    <param name="vad-thresh" value="30"/>
  </settings>
</configuration>
```

---

## 5. WebRTC 集成中的 VAD

### 5.1 mod_verto VAD 配置

```xml
<!-- conf/autoload_configs/verto.conf.xml -->
<configuration name="verto.conf" description="Verto Configuration">
  <settings>
    <param name="debug" value="0"/>
  </settings>
  <profiles>
    <profile name="default">
      <param name="bind-local" value="0.0.0.0:8081"/>
      <param name="bind-local" value="0.0.0.0:8082" secure="true"/>
      
      <!-- WebRTC VAD 配置 -->
      <param name="rtp-ip" value="$${local_ip_v4}"/>
      <param name="ext-rtp-ip" value="$${external_rtp_ip}"/>
      <param name="outbound-codec-string" value="opus"/>
      <param name="inbound-codec-string" value="opus"/>
      
      <!-- 启用 VAD -->
      <param name="apply-candidate-acl" value="rfc1918.auto"/>
    </profile>
  </profiles>
</configuration>
```

### 5.2 JavaScript 客户端 VAD

使用 hark.js 或 WebRTC VAD：

```javascript
// 客户端 VAD 检测
import hark from 'hark';

const speechEvents = hark(audioStream, {
    threshold: -50,       // 语音检测阈值 (dB)
    interval: 50,         // 检测间隔 (ms)
    history: 10           // 历史帧数
});

speechEvents.on('speaking', () => {
    console.log('检测到语音开始');
    // 通知 FreeSWITCH
});

speechEvents.on('stopped_speaking', () => {
    console.log('检测到语音结束');
    // 通知 FreeSWITCH
});
```

---

## 6. 实战配置示例

### 6.1 外呼机器人场景（VAD + ASR）

```xml
<!-- dialplan/default/robot_outbound.xml -->
<extension name="robot_outbound">
  <condition field="destination_number" expression="^robot_(.*)$">
    <!-- 设置 VAD 参数 -->
    <action application="set" data="vad_thresh=40"/>
    <action application="set" data="vad_mode=2"/>
    <action application="set" data="fire_vad_events=true"/>
    
    <!-- 降噪设置 -->
    <action application="denoise" data=""/>
    <action application="agc" data=""/>
    
    <!-- 设置编解码器（优先 Opus） -->
    <action application="export" data="nolocal:absolute_codec_string=opus@48000h@20i,PCMU"/>
    
    <!-- 启用语音识别 -->
    <action application="set" data="asr_engine=funasr"/>
    <action application="set" data="asr_grammar=default"/>
    
    <!-- 开始呼叫 -->
    <action application="bridge" data="sofia/gateway/pstn/${destination_number}"/>
  </condition>
</extension>
```

### 6.2 会议场景降噪配置

```xml
<!-- dialplan/default/conference.xml -->
<extension name="conference_with_denoise">
  <condition field="destination_number" expression="^3000$">
    <!-- 进入会议前的音频处理 -->
    <action application="answer"/>
    <action application="denoise" data=""/>
    <action application="agc" data=""/>
    
    <!-- VAD 配置（减少会议噪音） -->
    <action application="set" data="vad_thresh=50"/>
    <action application="set" data="vad_mode=3"/>
    
    <!-- 加入会议 -->
    <action application="conference" data="myconference@default+flags{mute}"/>
  </condition>
</extension>
```

### 6.3 ESL 控制的动态 VAD 配置

```java
package com.example.freeswitch;

import org.freeswitch.esl.client.IEslEventListener;
import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.transport.event.EslEvent;

public class VadController implements IEslEventListener {
    
    private final Client eslClient;
    
    public VadController(Client eslClient) {
        this.eslClient = eslClient;
    }
    
    /**
     * 为通道启用 VAD 和降噪
     */
    public void enableVadAndDenoise(String uuid) {
        // 设置 VAD 参数
        sendCommand("uuid_setvar " + uuid + " vad_thresh 45");
        sendCommand("uuid_setvar " + uuid + " vad_mode 2");
        sendCommand("uuid_setvar " + uuid + " fire_vad_events true");
        
        // 启用降噪
        sendCommand("uuid_broadcast " + uuid + " denoise::on aleg");
        
        // 启用自动增益控制
        sendCommand("uuid_broadcast " + uuid + " agc::on aleg");
    }
    
    /**
     * 调整 VAD 灵敏度
     */
    public void adjustVadSensitivity(String uuid, int threshold, int mode) {
        sendCommand("uuid_setvar " + uuid + " vad_thresh " + threshold);
        sendCommand("uuid_setvar " + uuid + " vad_mode " + mode);
    }
    
    /**
     * 处理 VAD 事件
     */
    @Override
    public void eventReceived(EslEvent event) {
        String eventName = event.getEventName();
        
        if ("CUSTOM".equals(eventName)) {
            String subclass = event.getEventSubclass();
            String uuid = event.getEventHeaders().get("Unique-ID");
            
            if ("vad::start".equals(subclass)) {
                System.out.println("语音开始: " + uuid);
                // 触发 ASR 开始
                startAsr(uuid);
            } else if ("vad::stop".equals(subclass)) {
                System.out.println("语音结束: " + uuid);
                // 触发 ASR 结束
                stopAsr(uuid);
            }
        }
    }
    
    private void sendCommand(String command) {
        eslClient.sendAsyncApiCommand(command, null);
    }
    
    private void startAsr(String uuid) {
        // ASR 开始逻辑
    }
    
    private void stopAsr(String uuid) {
        // ASR 结束逻辑
    }
    
    @Override
    public void backgroundJobResultReceived(EslEvent event) {
        // 后台任务结果处理
    }
}
```

### 6.4 Lua 脚本中的 VAD 控制

```lua
-- scripts/vad_control.lua

-- VAD 配置参数
local VAD_THRESH = 45
local VAD_MODE = 2
local DENOISE_ENABLED = true

-- 初始化 VAD 和降噪
function initVadAndDenoise(session)
    -- 设置 VAD 参数
    session:setVariable("vad_thresh", tostring(VAD_THRESH))
    session:setVariable("vad_mode", tostring(VAD_MODE))
    session:setVariable("fire_vad_events", "true")
    
    -- 启用降噪
    if DENOISE_ENABLED then
        session:execute("denoise", "")
        session:execute("agc", "")
    end
    
    freeswitch.consoleLog("INFO", "VAD and denoise initialized\n")
end

-- 动态调整 VAD 阈值
function adjustVadThreshold(session, threshold)
    session:setVariable("vad_thresh", tostring(threshold))
    freeswitch.consoleLog("INFO", "VAD threshold adjusted to: " .. threshold .. "\n")
end

-- VAD 事件回调
function vadCallback(session, type, obj)
    if type == "vad" then
        if obj.status == "start" then
            freeswitch.consoleLog("INFO", "Speech started\n")
            -- 在这里可以触发 ASR 或其他逻辑
        elseif obj.status == "stop" then
            freeswitch.consoleLog("INFO", "Speech stopped\n")
            -- 在这里可以处理语音结束逻辑
        end
    end
    return true
end

-- 主函数
function main(session)
    session:answer()
    
    -- 初始化
    initVadAndDenoise(session)
    
    -- 设置 VAD 回调
    session:setInputCallback("vadCallback")
    
    -- 播放欢迎语
    session:streamFile("/usr/local/freeswitch/sounds/welcome.wav")
    
    -- 等待并处理语音
    while session:ready() do
        session:sleep(100)
    end
end

-- 执行
main(session)
```

---

## 7. 性能优化建议

### 7.1 VAD 参数调优

| 场景 | vad_thresh | vad_mode | 说明 |
|------|-----------|----------|------|
| 安静环境 | 30-40 | 1-2 | 更灵敏，捕获轻声 |
| 嘈杂环境 | 50-70 | 2-3 | 更激进，过滤噪声 |
| 会议场景 | 50-60 | 3 | 高阈值减少串扰 |
| ASR 场景 | 35-45 | 2 | 平衡灵敏度和准确性 |

### 7.2 编解码器选择

**推荐优先级：**

1. **Opus** - 最佳音质，内置 VAD/DTX，适合 WebRTC
2. **G.722** - 宽带编码，良好音质
3. **PCMU/PCMA** - 兼容性最好，音质一般
4. **Speex** - 支持 VAD 和降噪，但已过时

```xml
<!-- 编解码器优先级配置 -->
<action application="set" data="absolute_codec_string=^^:opus@48000h@20i:PCMU:PCMA"/>
```

### 7.3 RTP 优化

```xml
<!-- conf/autoload_configs/switch.conf.xml -->
<settings>
  <!-- RTP 包大小 -->
  <param name="rtp-packet-size" value="20"/>
  
  <!-- jitter buffer -->
  <param name="rtp-start-port" value="16384"/>
  <param name="rtp-end-port" value="32768"/>
  
  <!-- 启用 RTCP -->
  <param name="rtcp-audio-interval-msec" value="5000"/>
</settings>
```

### 7.4 内存和 CPU 优化

```xml
<!-- 限制同时处理的通道数 -->
<param name="max-sessions" value="1000"/>
<param name="sessions-per-second" value="30"/>

<!-- 减少日志级别提高性能 -->
<param name="loglevel" value="WARNING"/>
```

---

## 8. 故障排除

### 8.1 常见问题

**问题 1：VAD 检测不灵敏**

```bash
# 检查 VAD 事件是否触发
fs_cli> events plain CUSTOM vad::start vad::stop

# 降低阈值
uuid_setvar <uuid> vad_thresh 30
uuid_setvar <uuid> vad_mode 1
```

**问题 2：VAD 过于敏感，误触发多**

```bash
# 提高阈值
uuid_setvar <uuid> vad_thresh 60
uuid_setvar <uuid> vad_mode 3
```

**问题 3：降噪后音质变差**

- 检查采样率是否匹配
- 尝试降低降噪强度
- 使用更高质量的编解码器（如 Opus）

**问题 4：VAD 事件延迟**

- 检查网络延迟
- 减少 VAD 检测间隔
- 考虑使用客户端 VAD

### 8.2 调试命令

```bash
# 查看通道变量
fs_cli> uuid_getvar <uuid> vad_thresh
fs_cli> uuid_getvar <uuid> vad_mode

# 查看 RTP 状态
fs_cli> uuid_debug_media <uuid> read on
fs_cli> uuid_debug_media <uuid> write on

# 查看编解码器信息
fs_cli> uuid_codec <uuid>

# 实时监控事件
fs_cli> /log 7
fs_cli> /event plain ALL
```

### 8.3 日志分析

```bash
# 查看 VAD 相关日志
grep -i "vad" /var/log/freeswitch/freeswitch.log

# 查看音频处理日志
grep -i "denoise\|agc" /var/log/freeswitch/freeswitch.log

# 查看编解码器协商
grep -i "codec" /var/log/freeswitch/freeswitch.log
```

---

## 附录：参考资源

- [FreeSWITCH 官方文档](https://freeswitch.org/confluence/)
- [Opus 编解码器配置](https://freeswitch.org/confluence/display/FREESWITCH/mod_opus)
- [Speex 编解码器配置](https://freeswitch.org/confluence/display/FREESWITCH/mod_speex)
- [WebRTC VAD](https://webrtc.googlesource.com/src/+/refs/heads/main/common_audio/vad/)
- [RNNoise 项目](https://github.com/xiph/rnnoise)

---

## 更新日志

| 日期 | 版本 | 描述 |
|------|------|------|
| 2026-01-27 | 1.0 | 初始版本 |
