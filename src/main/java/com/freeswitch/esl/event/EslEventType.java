package com.freeswitch.esl.event;

/**
 * FreeSWITCH 事件类型枚举
 * 
 * 定义常用的 FreeSWITCH 事件类型
 */
public enum EslEventType {

    /**
     * 通道创建事件
     */
    CHANNEL_CREATE("CHANNEL_CREATE"),

    /**
     * 通道应答事件
     */
    CHANNEL_ANSWER("CHANNEL_ANSWER"),

    /**
     * 通道挂断事件
     */
    CHANNEL_HANGUP("CHANNEL_HANGUP"),

    /**
     * 通道挂断完成事件
     */
    CHANNEL_HANGUP_COMPLETE("CHANNEL_HANGUP_COMPLETE"),

    /**
     * 通道桥接事件
     */
    CHANNEL_BRIDGE("CHANNEL_BRIDGE"),

    /**
     * 通道解除桥接事件
     */
    CHANNEL_UNBRIDGE("CHANNEL_UNBRIDGE"),

    /**
     * 通道状态变化事件
     */
    CHANNEL_STATE("CHANNEL_STATE"),

    /**
     * 通道执行事件
     */
    CHANNEL_EXECUTE("CHANNEL_EXECUTE"),

    /**
     * 通道执行完成事件
     */
    CHANNEL_EXECUTE_COMPLETE("CHANNEL_EXECUTE_COMPLETE"),

    /**
     * 通道进度事件
     */
    CHANNEL_PROGRESS("CHANNEL_PROGRESS"),

    /**
     * 通道进度媒体事件
     */
    CHANNEL_PROGRESS_MEDIA("CHANNEL_PROGRESS_MEDIA"),

    /**
     * 通道呼出事件
     */
    CHANNEL_OUTGOING("CHANNEL_OUTGOING"),

    /**
     * 通道发起事件
     */
    CHANNEL_ORIGINATE("CHANNEL_ORIGINATE"),

    /**
     * 通道UUID事件
     */
    CHANNEL_UUID("CHANNEL_UUID"),

    /**
     * DTMF 事件
     */
    DTMF("DTMF"),

    /**
     * 录音开始事件
     */
    RECORD_START("RECORD_START"),

    /**
     * 录音停止事件
     */
    RECORD_STOP("RECORD_STOP"),

    /**
     * 播放开始事件
     */
    PLAYBACK_START("PLAYBACK_START"),

    /**
     * 播放停止事件
     */
    PLAYBACK_STOP("PLAYBACK_STOP"),

    /**
     * 自定义事件
     */
    CUSTOM("CUSTOM"),

    /**
     * 后台任务事件
     */
    BACKGROUND_JOB("BACKGROUND_JOB"),

    /**
     * API 响应事件
     */
    API("API"),

    /**
     * 心跳事件
     */
    HEARTBEAT("HEARTBEAT"),

    /**
     * 重新调度事件
     */
    RE_SCHEDULE("RE_SCHEDULE"),

    /**
     * 检测语音事件
     */
    DETECTED_SPEECH("DETECTED_SPEECH"),

    /**
     * 检测音调事件
     */
    DETECTED_TONE("DETECTED_TONE"),

    /**
     * 媒体BUG开始事件
     */
    MEDIA_BUG_START("MEDIA_BUG_START"),

    /**
     * 媒体BUG停止事件
     */
    MEDIA_BUG_STOP("MEDIA_BUG_STOP"),

    /**
     * 呼叫更新事件
     */
    CALL_UPDATE("CALL_UPDATE"),

    /**
     * 会议数据查询事件
     */
    CONFERENCE_DATA_QUERY("CONFERENCE_DATA_QUERY"),

    /**
     * 会议数据事件
     */
    CONFERENCE_DATA("CONFERENCE_DATA"),

    /**
     * 所有事件
     */
    ALL("all");

    private final String value;

    EslEventType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 根据事件名称获取枚举
     *
     * @param eventName 事件名称
     * @return 事件类型枚举
     */
    public static EslEventType fromString(String eventName) {
        for (EslEventType type : values()) {
            if (type.value.equalsIgnoreCase(eventName)) {
                return type;
            }
        }
        return CUSTOM;
    }
}
