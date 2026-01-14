package com.ivr.system.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * IVR事件 - 表示IVR系统中发生的事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IvrEvent {

    /**
     * 事件ID
     */
    private String eventId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 事件类型
     */
    private EventType type;

    /**
     * 事件时间
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 事件数据
     */
    private Map<String, Object> data;

    /**
     * DTMF输入（如果是DTMF事件）
     */
    private String dtmf;

    /**
     * 事件来源
     */
    private String source;

    /**
     * 事件类型枚举
     */
    public enum EventType {
        /**
         * 呼叫进入
         */
        CALL_INIT,

        /**
         * 呼叫应答
         */
        CALL_ANSWER,

        /**
         * 呼叫挂断
         */
        CALL_HANGUP,

        /**
         * DTMF按键输入
         */
        DTMF,

        /**
         * 语音播放开始
         */
        PLAYBACK_START,

        /**
         * 语音播放结束
         */
        PLAYBACK_COMPLETE,

        /**
         * 录音开始
         */
        RECORD_START,

        /**
         * 录音结束
         */
        RECORD_STOP,

        /**
         * 语音识别结果
         */
        ASR_RESULT,

        /**
         * 转接开始
         */
        TRANSFER_START,

        /**
         * 转接成功
         */
        TRANSFER_SUCCESS,

        /**
         * 转接失败
         */
        TRANSFER_FAILED,

        /**
         * 进入队列
         */
        QUEUE_ENTER,

        /**
         * 离开队列
         */
        QUEUE_LEAVE,

        /**
         * 坐席接听
         */
        AGENT_ANSWER,

        /**
         * 超时
         */
        TIMEOUT,

        /**
         * 错误
         */
        ERROR,

        /**
         * 节点变更
         */
        NODE_CHANGE,

        /**
         * 自定义事件
         */
        CUSTOM
    }
}
