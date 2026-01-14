package com.ivr.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * FreeSWITCH配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "freeswitch")
public class FreeSwitchConfig {

    /**
     * FreeSWITCH主机地址
     */
    private String host = "127.0.0.1";

    /**
     * ESL端口
     */
    private int port = 8021;

    /**
     * ESL密码
     */
    private String password = "ClueCon";

    /**
     * 连接超时时间（毫秒）
     */
    private int timeout = 30000;

    /**
     * 是否自动连接
     */
    private boolean autoConnect = true;

    /**
     * 重连间隔（毫秒）
     */
    private int reconnectInterval = 5000;

    /**
     * 最大重连次数
     */
    private int maxReconnectAttempts = 10;

    /**
     * TTS引擎
     */
    private String ttsEngine = "flite";

    /**
     * TTS声音
     */
    private String ttsVoice = "kal";

    /**
     * 默认网关
     */
    private String defaultGateway = "default";

    /**
     * 录音文件路径
     */
    private String recordingPath = "/tmp/recordings";

    /**
     * 是否启用事件日志
     */
    private boolean eventLogging = false;
}
