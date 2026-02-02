package com.example.fsaudio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * FreeSWitch ESL配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "freeswitch.esl")
public class FreeSwitchConfig {
    
    /**
     * ESL服务器地址
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
     * 连接超时时间(毫秒)
     */
    private int timeout = 30000;
    
    /**
     * 心跳间隔(秒)
     */
    private int heartbeatInterval = 25;
}
