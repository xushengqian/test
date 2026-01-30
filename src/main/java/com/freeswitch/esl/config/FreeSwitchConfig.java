package com.freeswitch.esl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * FreeSWITCH ESL 连接配置类
 * 
 * 用于配置与 FreeSWITCH Event Socket Layer 的连接参数
 */
@Data
@Component
@ConfigurationProperties(prefix = "freeswitch.esl")
public class FreeSwitchConfig {

    /**
     * FreeSWITCH 服务器主机地址
     */
    private String host = "127.0.0.1";

    /**
     * ESL 端口号 (默认 8021)
     */
    private int port = 8021;

    /**
     * ESL 认证密码 (默认 ClueCon)
     */
    private String password = "ClueCon";

    /**
     * 连接超时时间 (毫秒)
     */
    private int connectTimeout = 5000;

    /**
     * 读取超时时间 (毫秒)
     */
    private int readTimeout = 30000;

    /**
     * 心跳间隔时间 (秒)
     */
    private int heartbeatInterval = 25;

    /**
     * 是否自动重连
     */
    private boolean autoReconnect = true;

    /**
     * 重连间隔时间 (毫秒)
     */
    private int reconnectInterval = 5000;

    /**
     * 最大重连次数 (0 表示无限重试)
     */
    private int maxReconnectAttempts = 0;

    /**
     * 连接池大小
     */
    private int poolSize = 5;

    /**
     * 连接池最大等待时间 (毫秒)
     */
    private int poolMaxWait = 3000;

    /**
     * 是否启用 SSL
     */
    private boolean sslEnabled = false;

    /**
     * 事件订阅类型
     * 可选值: all, plain, xml, json
     */
    private String eventFormat = "plain";

    /**
     * 需要订阅的事件类型列表
     */
    private String[] subscribeEvents = {"all"};

    /**
     * 是否启用事件过滤
     */
    private boolean eventFilterEnabled = false;

    /**
     * 事件过滤器表达式
     */
    private String eventFilter;
}
