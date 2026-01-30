package com.freeswitch.esl.util;

import com.freeswitch.esl.client.EslMessage;
import com.freeswitch.esl.event.EslEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * ESL 消息解析工具类
 * 
 * 解析 FreeSWITCH ESL 协议消息
 */
public class EslMessageParser {

    private static final Logger log = LoggerFactory.getLogger(EslMessageParser.class);

    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String CONTENT_LENGTH_HEADER = "Content-Length";

    /**
     * 解析 ESL 消息
     *
     * @param reader 输入读取器
     * @return ESL 消息对象
     */
    public static EslMessage parseMessage(BufferedReader reader) throws IOException {
        EslMessage message = new EslMessage();
        StringBuilder rawContent = new StringBuilder();

        // 读取头部
        String line;
        while ((line = reader.readLine()) != null) {
            rawContent.append(line).append("\n");

            if (line.isEmpty()) {
                break;
            }

            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String name = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                message.addHeader(name, value);

                if (CONTENT_TYPE_HEADER.equals(name)) {
                    message.setContentType(value);
                } else if (CONTENT_LENGTH_HEADER.equals(name)) {
                    try {
                        message.setContentLength(Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid Content-Length: {}", value);
                    }
                }
            }
        }

        // 读取消息体
        int contentLength = message.getContentLength();
        if (contentLength > 0) {
            char[] bodyChars = new char[contentLength];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int read = reader.read(bodyChars, totalRead, contentLength - totalRead);
                if (read == -1) {
                    break;
                }
                totalRead += read;
            }

            String body = new String(bodyChars, 0, totalRead);
            rawContent.append(body);
            message.setBodyLines(body.split("\n"));
        }

        message.setRawContent(rawContent.toString());

        return message;
    }

    /**
     * 将 ESL 消息解析为事件对象
     *
     * @param message ESL 消息
     * @return ESL 事件对象
     */
    public static EslEvent parseEvent(EslMessage message) {
        if (message == null) {
            return null;
        }

        EslEvent event = new EslEvent();
        event.setTimestamp(System.currentTimeMillis());
        event.setRawContent(message.getRawContent());

        String[] bodyLines = message.getBodyLines();
        if (bodyLines != null) {
            StringBuilder bodyContent = new StringBuilder();
            boolean inBody = false;

            for (String line : bodyLines) {
                if (inBody) {
                    bodyContent.append(line).append("\n");
                    continue;
                }

                if (line.isEmpty()) {
                    inBody = true;
                    continue;
                }

                int colonIndex = line.indexOf(':');
                if (colonIndex > 0) {
                    String name = line.substring(0, colonIndex).trim();
                    String value = decodeValue(line.substring(colonIndex + 1).trim());
                    event.setHeader(name, value);

                    // 设置常用字段
                    switch (name) {
                        case "Event-Name":
                            event.setEventName(value);
                            break;
                        case "Event-Subclass":
                            event.setEventSubclass(value);
                            break;
                        case "Unique-ID":
                            event.setUniqueId(value);
                            break;
                        case "Channel-UUID":
                            event.setChannelUuid(value);
                            break;
                    }
                }
            }

            if (bodyContent.length() > 0) {
                event.setBody(bodyContent.toString().trim());
            }
        }

        return event;
    }

    /**
     * URL 解码值
     *
     * @param value 编码值
     * @return 解码后的值
     */
    private static String decodeValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return value;
        }
    }

    /**
     * 解析 API 响应
     *
     * @param response 响应文本
     * @return 解析后的行列表
     */
    public static List<String> parseApiResponse(String response) {
        List<String> lines = new ArrayList<>();
        if (response == null || response.isEmpty()) {
            return lines;
        }

        String[] parts = response.split("\n");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }

        return lines;
    }

    /**
     * 检查响应是否为错误
     *
     * @param response 响应文本
     * @return 是否错误
     */
    public static boolean isErrorResponse(String response) {
        return response != null && response.startsWith("-ERR");
    }

    /**
     * 提取错误信息
     *
     * @param response 响应文本
     * @return 错误信息
     */
    public static String extractErrorMessage(String response) {
        if (response != null && response.startsWith("-ERR")) {
            return response.substring(5).trim();
        }
        return response;
    }
}
