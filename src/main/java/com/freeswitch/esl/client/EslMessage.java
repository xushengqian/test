package com.freeswitch.esl.client;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * ESL 消息对象
 * 
 * 封装 ESL 命令响应或事件消息
 */
@Data
public class EslMessage {

    /**
     * 消息头部
     */
    private Map<String, String> headers = new HashMap<>();

    /**
     * 消息体行列表
     */
    private String[] bodyLines;

    /**
     * 原始消息内容
     */
    private String rawContent;

    /**
     * Content-Type
     */
    private String contentType;

    /**
     * Content-Length
     */
    private int contentLength;

    /**
     * 添加头部
     *
     * @param name  头部名称
     * @param value 头部值
     */
    public void addHeader(String name, String value) {
        headers.put(name, value);
    }

    /**
     * 获取头部值
     *
     * @param name 头部名称
     * @return 头部值
     */
    public String getHeaderValue(String name) {
        return headers.get(name);
    }

    /**
     * 检查是否包含指定头部
     *
     * @param name 头部名称
     * @return 是否存在
     */
    public boolean hasHeader(String name) {
        return headers.containsKey(name);
    }

    /**
     * 获取消息体文本
     *
     * @return 消息体文本
     */
    public String getBodyText() {
        if (bodyLines == null || bodyLines.length == 0) {
            return "";
        }
        return String.join("\n", bodyLines);
    }

    /**
     * 检查是否为成功响应
     *
     * @return 是否成功
     */
    public boolean isOk() {
        String replyText = getHeaderValue("Reply-Text");
        return replyText != null && replyText.startsWith("+OK");
    }

    /**
     * 检查是否为错误响应
     *
     * @return 是否错误
     */
    public boolean isError() {
        String replyText = getHeaderValue("Reply-Text");
        return replyText != null && replyText.startsWith("-ERR");
    }

    /**
     * 获取响应文本
     *
     * @return 响应文本
     */
    public String getReplyText() {
        return getHeaderValue("Reply-Text");
    }

    /**
     * 获取 Job-UUID
     *
     * @return Job UUID
     */
    public String getJobUuid() {
        return getHeaderValue("Job-UUID");
    }

    @Override
    public String toString() {
        return "EslMessage{" +
                "contentType='" + contentType + '\'' +
                ", headersCount=" + headers.size() +
                ", isOk=" + isOk() +
                '}';
    }
}
