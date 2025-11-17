# FreeSWITCH + MRCP Docker镜像
FROM debian:bullseye-slim

LABEL maintainer="freeswitch-mrcp@example.com"
LABEL description="FreeSWITCH with MRCP for real-time audio streaming"

# 设置环境变量
ENV DEBIAN_FRONTEND=noninteractive
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# 安装基础依赖
RUN apt-get update && apt-get install -y \
    wget \
    gnupg2 \
    curl \
    git \
    python3 \
    python3-pip \
    && rm -rf /var/lib/apt/lists/*

# 添加FreeSWITCH仓库
RUN wget -O - https://files.freeswitch.org/repo/deb/debian-release/fsstretch-archive-keyring.asc | apt-key add - \
    && echo "deb http://files.freeswitch.org/repo/deb/debian-release/ bullseye main" > /etc/apt/sources.list.d/freeswitch.list

# 安装FreeSWITCH和MRCP
RUN apt-get update && apt-get install -y \
    freeswitch \
    freeswitch-mod-commands \
    freeswitch-mod-console \
    freeswitch-mod-logfile \
    freeswitch-mod-event-socket \
    freeswitch-mod-unimrcp \
    unimrcp-server \
    libunimrcp-dev \
    && rm -rf /var/lib/apt/lists/*

# 创建工作目录
WORKDIR /app

# 复制项目文件
COPY requirements.txt .
COPY config/ ./config/
COPY src/ ./src/
COPY examples/ ./examples/

# 安装Python依赖
RUN pip3 install --no-cache-dir -r requirements.txt

# 复制FreeSWITCH配置
RUN cp -r config/freeswitch/* /etc/freeswitch/

# 创建日志目录
RUN mkdir -p /var/log/mrcp_stream && chmod 755 /var/log/mrcp_stream

# 暴露端口
# 5060: SIP
# 4000-5000: RTP
# 8021: ESL
# 1544: MRCP
# 8765: WebSocket
EXPOSE 5060/udp 4000-5000/udp 8021/tcp 1544/tcp 8765/tcp

# 启动脚本
COPY docker-entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["freeswitch", "-nonat", "-c"]
