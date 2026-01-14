package com.ivr.system.esl;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * ESL响应对象
 */
@Data
public class EslResponse {

    private final Map<String, String> headers;
    private final String body;

    public EslResponse(Map<String, String> headers, String body) {
        this.headers = headers != null ? headers : new HashMap<>();
        this.body = body;
    }

    /**
     * 获取头部值
     */
    public String getHeader(String name) {
        return headers.get(name);
    }

    /**
     * 检查响应是否成功
     */
    public boolean isSuccess() {
        String replyText = headers.get("Reply-Text");
        if (replyText != null) {
            return replyText.startsWith("+OK");
        }

        String contentType = headers.get("Content-Type");
        if (contentType != null) {
            return contentType.contains("api/response") ||
                   contentType.contains("command/reply") ||
                   contentType.contains("auth/request");
        }

        return !headers.isEmpty();
    }

    /**
     * 获取事件名称
     */
    public String getEventName() {
        return headers.get("Event-Name");
    }

    /**
     * 获取UUID
     */
    public String getUniqueId() {
        return headers.get("Unique-ID");
    }

    /**
     * 获取DTMF数字
     */
    public String getDtmfDigit() {
        return headers.get("DTMF-Digit");
    }

    /**
     * 获取呼叫方向
     */
    public String getCallDirection() {
        return headers.get("Call-Direction");
    }

    /**
     * 获取主叫号码
     */
    public String getCallerIdNumber() {
        return headers.get("Caller-Caller-ID-Number");
    }

    /**
     * 获取被叫号码
     */
    public String getDestinationNumber() {
        return headers.get("Caller-Destination-Number");
    }

    /**
     * 获取挂断原因
     */
    public String getHangupCause() {
        return headers.get("Hangup-Cause");
    }

    /**
     * 检查是否为事件
     */
    public boolean isEvent() {
        String contentType = headers.get("Content-Type");
        return contentType != null && contentType.contains("text/event-plain");
    }

    @Override
    public String toString() {
        return "EslResponse{" +
                "headers=" + headers +
                ", body='" + (body != null ? body.substring(0, Math.min(100, body.length())) : null) + '\'' +
                '}';
    }
}
