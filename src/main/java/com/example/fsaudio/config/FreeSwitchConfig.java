package com.example.fsaudio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * FreeSwtich ESL连接配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "freeswitch.esl")
public class FreeSwitchConfig {

    /**
     * FreeSwtich服务器地址
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
     * 连接超时时间(秒)
     */
    private int timeout = 30;

    /**
     * 心跳间隔(秒)
     */
    private int heartbeatInterval = 25;

    /**
     * 重连间隔(秒)
     */
    private int reconnectInterval = 5;

    /**
     * 最大重连次数
     */
    private int maxReconnectAttempts = 10;
}
