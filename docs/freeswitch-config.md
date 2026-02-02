# FreeSwtich 配置指南

本文档说明如何配置 FreeSwtich 以支持实时音频流获取。

## 1. ESL 配置

### event_socket.conf.xml

编辑 `/usr/local/freeswitch/conf/autoload_configs/event_socket.conf.xml`:

```xml
<configuration name="event_socket.conf" description="Socket Client">
  <settings>
    <!-- ESL监听地址，0.0.0.0允许远程连接 -->
    <param name="nat-map" value="false"/>
    <param name="listen-ip" value="0.0.0.0"/>
    <param name="listen-port" value="8021"/>
    <!-- ESL认证密码 -->
    <param name="password" value="ClueCon"/>
    <!-- 允许ACL -->
    <param name="apply-inbound-acl" value="loopback.auto"/>
  </settings>
</configuration>
```

## 2. 音频流模块配置

### 方法一: 使用 mod_audio_stream (推荐)

如果使用 mod_audio_stream 模块进行音频流传输，需要编译并加载该模块。

#### 编译 mod_audio_stream

```bash
cd /usr/local/src/freeswitch/src/mod/applications/mod_audio_stream
make && make install
```

#### 加载模块

编辑 `/usr/local/freeswitch/conf/autoload_configs/modules.conf.xml`:

```xml
<load module="mod_audio_stream"/>
```

#### 使用示例

在拨号计划或通过ESL使用:

```xml
<!-- 拨号计划中启动音频流 -->
<action application="audio_stream" data="ws://192.168.1.100:8080/ws/audio both"/>
```

或通过ESL命令:

```
uuid_audio_stream <uuid> start ws://192.168.1.100:8080/ws/audio both
uuid_audio_stream <uuid> stop
```

### 方法二: 使用 uuid_record 录音到管道

#### 创建命名管道

```bash
mkfifo /tmp/audio_pipe
```

#### 拨号计划配置

```xml
<!-- 录音到管道 -->
<action application="record" data="/tmp/audio_pipe"/>
```

或通过ESL:

```
uuid_record <uuid> start /tmp/audio_pipe
```

### 方法三: 使用 mod_oreka 进行音频镜像

#### oreka.conf.xml

```xml
<configuration name="oreka.conf" description="Roles">
  <settings>
    <param name="spool-dir" value="/var/spool/oreka"/>
    <param name="silencethreshold" value="200"/>
    <param name="mux-stereo" value="true"/>
  </settings>
</configuration>
```

## 3. 音频格式配置

### 采样率设置

在 vars.xml 中设置默认采样率:

```xml
<X-PRE-PROCESS cmd="set" data="default_sample_rate=8000"/>
```

### 编解码器配置

在 vars.xml 中配置编解码器:

```xml
<X-PRE-PROCESS cmd="set" data="global_codec_prefs=PCMU,PCMA,G729"/>
<X-PRE-PROCESS cmd="set" data="outbound_codec_prefs=PCMU,PCMA"/>
```

## 4. 拨号计划示例

### 启动实时音频流的拨号计划

编辑 `/usr/local/freeswitch/conf/dialplan/default.xml`:

```xml
<extension name="audio_stream_demo">
  <condition field="destination_number" expression="^8000$">
    <!-- 接听呼叫 -->
    <action application="answer"/>
    
    <!-- 设置音频参数 -->
    <action application="set" data="RECORD_STEREO=true"/>
    
    <!-- 启动音频流 (WebSocket方式) -->
    <action application="audio_stream" data="ws://192.168.1.100:8080/ws/audio both"/>
    
    <!-- 或使用录音到文件 -->
    <!-- <action application="record" data="/tmp/recordings/${uuid}.wav"/> -->
    
    <!-- 播放提示音 -->
    <action application="playback" data="ivr/ivr-welcome.wav"/>
    
    <!-- 等待用户输入或执行其他操作 -->
    <action application="park"/>
  </condition>
</extension>
```

### 转接到人工时启动音频流

```xml
<extension name="transfer_to_agent">
  <condition field="destination_number" expression="^9(\d+)$">
    <action application="answer"/>
    
    <!-- 开始录音 -->
    <action application="set" data="RECORD_STEREO=true"/>
    <action application="record_session" data="/tmp/recordings/${uuid}.wav"/>
    
    <!-- 启动实时音频流 -->
    <action application="export" data="nolocal:execute_on_answer=uuid_audio_stream ${uuid} start ws://192.168.1.100:8080/ws/audio both"/>
    
    <!-- 转接到坐席 -->
    <action application="bridge" data="user/$1@${domain_name}"/>
  </condition>
</extension>
```

## 5. Lua 脚本示例

### 实时音频处理脚本

保存为 `/usr/local/freeswitch/scripts/audio_stream.lua`:

```lua
-- audio_stream.lua
-- 获取通话UUID
local uuid = session:getVariable("uuid")
local ws_url = "ws://192.168.1.100:8080/ws/audio"

-- 接听呼叫
session:answer()

-- 启动音频流
freeswitch.API():execute("uuid_audio_stream", uuid .. " start " .. ws_url .. " both")

-- 等待通话结束
while session:ready() do
    session:sleep(1000)
end

-- 停止音频流
freeswitch.API():execute("uuid_audio_stream", uuid .. " stop")
```

在拨号计划中调用:

```xml
<action application="lua" data="audio_stream.lua"/>
```

## 6. 故障排查

### 检查ESL连接

```bash
# 使用fs_cli测试
fs_cli -x "status"

# 或使用telnet
telnet 127.0.0.1 8021
auth ClueCon
api status
```

### 检查模块加载

```bash
fs_cli -x "module_exists mod_audio_stream"
```

### 查看活跃通道

```bash
fs_cli -x "show channels"
```

### 查看录音状态

```bash
fs_cli -x "uuid_buglist <uuid>"
```

## 7. 性能优化

### 音频缓冲区设置

```xml
<param name="rtp-rewrite-timestamps" value="true"/>
<param name="rtp-timeout-sec" value="300"/>
<param name="jitterbuffer-msec" value="60"/>
```

### 线程池配置

在 switch.conf.xml 中:

```xml
<param name="core-db-dsn" value=""/>
<param name="max-sessions" value="5000"/>
<param name="sessions-per-second" value="1000"/>
<param name="loglevel" value="warning"/>
```
