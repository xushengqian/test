package com.freeswitch.esl.event;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * FreeSWITCH ESL 事件对象
 * 
 * 封装从 FreeSWITCH 接收到的事件数据
 */
@Data
public class EslEvent {

    /**
     * 事件名称
     */
    private String eventName;

    /**
     * 事件子类
     */
    private String eventSubclass;

    /**
     * 唯一 UUID
     */
    private String uniqueId;

    /**
     * Channel UUID
     */
    private String channelUuid;

    /**
     * 事件头部信息
     */
    private Map<String, String> headers = new HashMap<>();

    /**
     * 事件体内容
     */
    private String body;

    /**
     * 事件原始内容
     */
    private String rawContent;

    /**
     * 事件接收时间戳
     */
    private long timestamp;

    /**
     * 获取事件头部值
     *
     * @param headerName 头部名称
     * @return 头部值
     */
    public String getHeader(String headerName) {
        return headers.get(headerName);
    }

    /**
     * 设置事件头部值
     *
     * @param headerName 头部名称
     * @param value 头部值
     */
    public void setHeader(String headerName, String value) {
        headers.put(headerName, value);
    }

    /**
     * 检查是否包含指定头部
     *
     * @param headerName 头部名称
     * @return 是否存在
     */
    public boolean hasHeader(String headerName) {
        return headers.containsKey(headerName);
    }

    /**
     * 获取呼叫方号码
     *
     * @return 呼叫方号码
     */
    public String getCallerIdNumber() {
        return getHeader("Caller-Caller-ID-Number");
    }

    /**
     * 获取被叫号码
     *
     * @return 被叫号码
     */
    public String getDestinationNumber() {
        return getHeader("Caller-Destination-Number");
    }

    /**
     * 获取呼叫状态
     *
     * @return 呼叫状态
     */
    public String getChannelState() {
        return getHeader("Channel-State");
    }

    /**
     * 获取呼叫回答状态
     *
     * @return 回答状态
     */
    public String getAnswerState() {
        return getHeader("Answer-State");
    }

    /**
     * 获取挂断原因
     *
     * @return 挂断原因
     */
    public String getHangupCause() {
        return getHeader("Hangup-Cause");
    }

    /**
     * 获取变量值
     *
     * @param variableName 变量名
     * @return 变量值
     */
    public String getVariable(String variableName) {
        return getHeader("variable_" + variableName);
    }

    /**
     * 判断是否为特定事件类型
     *
     * @param eventName 事件名称
     * @return 是否匹配
     */
    public boolean isEvent(String eventName) {
        return eventName != null && eventName.equals(this.eventName);
    }

    @Override
    public String toString() {
        return "EslEvent{" +
                "eventName='" + eventName + '\'' +
                ", uniqueId='" + uniqueId + '\'' +
                ", channelUuid='" + channelUuid + '\'' +
                ", headersCount=" + headers.size() +
                '}';
    }
}
