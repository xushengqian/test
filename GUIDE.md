# FreeSWITCH VAD (语音活动检测) 与降噪配置指南

本文档介绍了如何在 FreeSWITCH 中配置 VAD 和降噪功能，包括配置文件示例和模块安装说明。

## 目录
1. [VAD (语音活动检测)](#1-vad-语音活动检测)
2. [降噪 (Noise Reduction)](#2-降噪-noise-reduction)
3. [配置文件说明](#3-配置文件说明)

---

## 1. VAD (语音活动检测)

VAD 主要用于检测语音是否存在，可用于节省带宽或控制业务逻辑。

### 1.1 开启 RTP VAD (节省带宽)
通过设置通道变量 `vad_enable`，FreeSWITCH 可以在检测到静音时不发送 RTP 包（或发送 CNG 舒适噪音包）。

**Dialplan 配置:**
```xml
<action application="set" data="vad_enable=true"/>
<!-- 可选参数 -->
<action application="set" data="vad_silence_ms=500"/> <!-- 静音多少毫秒判定为静音 -->
<action application="set" data="vad_threshold=500"/>  <!-- 能量阈值 -->
```

### 1.2 业务逻辑 VAD (detect_silence)
如果你需要在检测到用户不说话时执行操作（如自动挂断、跳转 IVR），使用 `detect_silence` APP。

**Dialplan 配置:**
```xml
<!-- 检测 3000ms 的静音，阈值为 200 -->
<action application="detect_silence" data="3000 200"/>
```

---

## 2. 降噪 (Noise Reduction)

FreeSWITCH 核心不带高级 AI 降噪，推荐使用模块扩展。

### 2.1 mod_rnnoise (推荐)
`mod_rnnoise` 基于 RNN (循环神经网络)，对语音背景噪音有很好的抑制效果。

**安装步骤 (示例):**
由于它是第三方模块，通常需要手动编译。

1. **安装依赖:**
   ```bash
   sudo apt-get install autoconf libtool
   ```

2. **获取源码并编译:**
   （注：有很多 fork 版本，请选择适合你 FreeSWITCH 版本的）
   ```bash
   git clone https://github.com/wavyphon/mod_rnnoise.git
   cd mod_rnnoise
   ./bootstrap.sh
   ./configure
   make
   sudo make install
   ```

3. **加载模块:**
   在 `conf/autoload_configs/modules.conf.xml` 中添加：
   ```xml
   <load module="mod_rnnoise"/>
   ```

4. **Dialplan 中使用:**
   ```xml
   <action application="set" data="execute_on_answer=rnnoise_start"/>
   ```

### 2.2 mod_webrtc
如果使用 WebRTC 客户端，或者是较新的 FreeSWITCH 版本，可以利用 WebRTC 栈的降噪。

**配置:**
```xml
<load module="mod_webrtc"/>
<!-- Dialplan -->
<action application="set" data="webrtc_enable_noise_suppression=true"/>
```

---

## 3. 配置文件说明

本项目提供了以下示例文件：

- `conf/autoload_configs/modules.conf.xml`: 展示了在哪里加载 `mod_rnnoise`。
- `conf/dialplan/default/vad_noise_test.xml`:
    - Extension `9001`: 演示 `vad_enable`。
    - Extension `9002`: 演示 `detect_silence`。
    - Extension `9003`: 演示 `mod_rnnoise` 降噪。
    - Extension `9004`: 演示 `mod_webrtc` 降噪。

### 如何部署
将 `conf` 目录下的文件复制到你的 FreeSWITCH 配置目录（通常是 `/usr/local/freeswitch/conf/` 或 `/etc/freeswitch/`）。
