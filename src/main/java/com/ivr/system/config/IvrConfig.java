package com.ivr.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * IVR系统配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ivr")
public class IvrConfig {

    /**
     * 流程定义文件路径
     */
    private String flowPath = "classpath:flows/*.yml";

    /**
     * 默认流程ID
     */
    private String defaultFlow = "default";

    /**
     * 默认输入超时时间（毫秒）
     */
    private long defaultTimeout = 10000;

    /**
     * 默认最大重试次数
     */
    private int defaultMaxRetries = 3;

    /**
     * 无效输入提示语
     */
    private String invalidInputPrompt = "Invalid input, please try again";

    /**
     * 超时提示语
     */
    private String timeoutPrompt = "No input received, please try again";

    /**
     * 系统错误提示语
     */
    private String errorPrompt = "System error, please try again later";

    /**
     * 是否启用ASR
     */
    private boolean asrEnabled = false;

    /**
     * ASR引擎
     */
    private String asrEngine = "default";

    /**
     * 会话超时时间（秒）
     */
    private int sessionTimeout = 300;

    /**
     * 是否启用调试模式
     */
    private boolean debug = false;
}
