package com.example.fsaudio.websocket;

import com.alibaba.fastjson.JSON;
import com.example.fsaudio.model.AudioFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket处理器
 * 
 * 用于实时推送音频流数据到客户端
 */
@Slf4j
@Component
public class AudioWebSocketHandler extends TextWebSocketHandler {

    /**
     * 所有连接的WebSocket会话
     */
    private final Set<WebSocketSession> allSessions = new CopyOnWriteArraySet<>();

    /**
     * 按UUID分组的WebSocket会话 (订阅特定通话的客户端)
     */
    private final Map<String, Set<WebSocketSession>> uuidSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        allSessions.add(session);
        log.info("WebSocket连接建立: sessionId={}", session.getId());
        
        // 发送欢迎消息
        session.sendMessage(new TextMessage("{\"type\":\"connected\",\"message\":\"WebSocket连接成功\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("收到WebSocket消息: {}", payload);

        try {
            Map<String, Object> msg = JSON.parseObject(payload, Map.class);
            String type = (String) msg.get("type");

            switch (type) {
                case "subscribe":
                    // 订阅特定通话的音频流
                    handleSubscribe(session, msg);
                    break;
                case "unsubscribe":
                    // 取消订阅
                    handleUnsubscribe(session, msg);
                    break;
                case "ping":
                    // 心跳
                    session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
                    break;
                default:
                    log.warn("未知消息类型: {}", type);
            }
        } catch (Exception e) {
            log.error("处理WebSocket消息异常: {}", e.getMessage());
            session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"" + e.getMessage() + "\"}"));
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        // 处理二进制消息 (如果客户端发送音频数据)
        ByteBuffer buffer = message.getPayload();
        log.debug("收到二进制消息: {} bytes", buffer.remaining());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输错误: sessionId={}, error={}", session.getId(), exception.getMessage());
        removeSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket连接关闭: sessionId={}, status={}", session.getId(), status);
        removeSession(session);
    }

    /**
     * 处理订阅请求
     */
    private void handleSubscribe(WebSocketSession session, Map<String, Object> msg) throws IOException {
        String uuid = (String) msg.get("uuid");
        if (uuid != null && !uuid.isEmpty()) {
            uuidSessions.computeIfAbsent(uuid, k -> new CopyOnWriteArraySet<>()).add(session);
            session.sendMessage(new TextMessage(
                    String.format("{\"type\":\"subscribed\",\"uuid\":\"%s\"}", uuid)));
            log.info("WebSocket订阅: sessionId={}, uuid={}", session.getId(), uuid);
        }
    }

    /**
     * 处理取消订阅请求
     */
    private void handleUnsubscribe(WebSocketSession session, Map<String, Object> msg) throws IOException {
        String uuid = (String) msg.get("uuid");
        if (uuid != null) {
            Set<WebSocketSession> sessions = uuidSessions.get(uuid);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    uuidSessions.remove(uuid);
                }
            }
            session.sendMessage(new TextMessage(
                    String.format("{\"type\":\"unsubscribed\",\"uuid\":\"%s\"}", uuid)));
            log.info("WebSocket取消订阅: sessionId={}, uuid={}", session.getId(), uuid);
        }
    }

    /**
     * 移除会话
     */
    private void removeSession(WebSocketSession session) {
        allSessions.remove(session);
        
        // 从所有UUID订阅中移除
        uuidSessions.values().forEach(sessions -> sessions.remove(session));
        
        // 清理空的订阅列表
        uuidSessions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * 发送音频帧到订阅的客户端
     * 
     * @param uuid 通话UUID
     * @param frame 音频帧
     */
    public void sendAudioFrame(String uuid, AudioFrame frame) {
        Set<WebSocketSession> sessions = uuidSessions.get(uuid);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        // 构建消息
        Map<String, Object> message = new ConcurrentHashMap<>();
        message.put("type", "audio");
        message.put("uuid", uuid);
        message.put("timestamp", frame.getTimestamp());
        message.put("sequence", frame.getSequenceNumber());
        message.put("direction", frame.getDirection());
        message.put("sampleRate", frame.getSampleRate());
        message.put("channels", frame.getChannels());
        message.put("data", Base64.getEncoder().encodeToString(frame.getData()));

        String jsonMessage = JSON.toJSONString(message);
        TextMessage textMessage = new TextMessage(jsonMessage);

        // 发送给所有订阅该UUID的客户端
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.error("发送音频帧失败: sessionId={}, error={}", session.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * 发送二进制音频数据
     * 
     * @param uuid 通话UUID
     * @param audioData 音频数据
     */
    public void sendBinaryAudio(String uuid, byte[] audioData) {
        Set<WebSocketSession> sessions = uuidSessions.get(uuid);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        BinaryMessage binaryMessage = new BinaryMessage(audioData);

        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(binaryMessage);
                    }
                } catch (IOException e) {
                    log.error("发送二进制音频失败: sessionId={}, error={}", session.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * 广播消息给所有连接的客户端
     */
    public void broadcast(String message) {
        TextMessage textMessage = new TextMessage(message);
        
        for (WebSocketSession session : allSessions) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.error("广播消息失败: sessionId={}, error={}", session.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * 发送事件通知
     */
    public void sendEvent(String uuid, String eventType, Map<String, Object> data) {
        Map<String, Object> message = new ConcurrentHashMap<>();
        message.put("type", "event");
        message.put("uuid", uuid);
        message.put("event", eventType);
        message.put("data", data);

        String jsonMessage = JSON.toJSONString(message);

        Set<WebSocketSession> sessions = uuidSessions.get(uuid);
        if (sessions != null) {
            TextMessage textMessage = new TextMessage(jsonMessage);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        synchronized (session) {
                            session.sendMessage(textMessage);
                        }
                    } catch (IOException e) {
                        log.error("发送事件失败: {}", e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 获取当前连接数
     */
    public int getConnectionCount() {
        return allSessions.size();
    }

    /**
     * 获取订阅特定UUID的连接数
     */
    public int getSubscriberCount(String uuid) {
        Set<WebSocketSession> sessions = uuidSessions.get(uuid);
        return sessions != null ? sessions.size() : 0;
    }
}
