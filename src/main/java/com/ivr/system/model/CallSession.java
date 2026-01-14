package com.ivr.system.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 呼叫会话 - 代表一次IVR通话
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallSession {

    /**
     * 会话唯一标识（通常是FreeSWITCH的UUID）
     */
    private String sessionId;

    /**
     * 主叫号码
     */
    private String callerNumber;

    /**
     * 被叫号码
     */
    private String calledNumber;

    /**
     * 呼叫方向
     */
    private CallDirection direction;

    /**
     * 当前IVR流程ID
     */
    private String flowId;

    /**
     * 当前节点ID
     */
    private String currentNodeId;

    /**
     * 会话状态
     */
    private SessionState state;

    /**
     * 当前节点重试次数
     */
    private int retryCount;

    /**
     * 收集到的DTMF输入
     */
    private String collectedDtmf;

    /**
     * 会话开始时间
     */
    private LocalDateTime startTime;

    /**
     * 会话结束时间
     */
    private LocalDateTime endTime;

    /**
     * 转接的坐席ID
     */
    private String transferredAgentId;

    /**
     * 会话变量存储
     */
    @Builder.Default
    private Map<String, Object> variables = new HashMap<>();

    /**
     * 会话扩展属性
     */
    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();

    /**
     * 呼叫方向枚举
     */
    public enum CallDirection {
        /**
         * 呼入
         */
        INBOUND,

        /**
         * 呼出
         */
        OUTBOUND
    }

    /**
     * 会话状态枚举
     */
    public enum SessionState {
        /**
         * 初始化
         */
        INIT,

        /**
         * 运行中
         */
        RUNNING,

        /**
         * 等待输入
         */
        WAITING_INPUT,

        /**
         * 转接中
         */
        TRANSFERRING,

        /**
         * 队列等待中
         */
        IN_QUEUE,

        /**
         * 与坐席通话中
         */
        WITH_AGENT,

        /**
         * 已完成
         */
        COMPLETED,

        /**
         * 已挂断
         */
        HANGUP,

        /**
         * 错误
         */
        ERROR
    }

    /**
     * 设置会话变量
     */
    public void setVariable(String key, Object value) {
        if (variables == null) {
            variables = new HashMap<>();
        }
        variables.put(key, value);
    }

    /**
     * 获取会话变量
     */
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key) {
        if (variables == null) {
            return null;
        }
        return (T) variables.get(key);
    }

    /**
     * 设置属性
     */
    public void setAttribute(String key, Object value) {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        attributes.put(key, value);
    }

    /**
     * 获取属性
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        if (attributes == null) {
            return null;
        }
        return (T) attributes.get(key);
    }
}
