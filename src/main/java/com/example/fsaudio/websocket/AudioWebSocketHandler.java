package com.example.fsaudio.websocket;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.fsaudio.model.AudioFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 音频流WebSocket处理器
 * 负责处理WebSocket连接和音频数据的实时推送
 */
@Slf4j
@Component
public class AudioWebSocketHandler extends AbstractWebSocketHandler {

    /**
     * 活跃的WebSocket会话
     * key: sessionId
     * value: WebSocketSession
     */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    /**
     * 会话订阅的通话UUID
     * key: sessionId
     * value: Set<callUuid>
     */
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();
    
    /**
     * 通话UUID对应的订阅会话
     * key: callUuid
     * value: Set<sessionId>
     */
    private final Map<String, Set<String>> callSubscribers = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        sessionSubscriptions.put(sessionId, ConcurrentHashMap.newKeySet());
        
        // 从URL路径中提取callUuid
        String callUuid = extractCallUuidFromPath(session);
        if (callUuid != null) {
            subscribe(sessionId, callUuid);
        }
        
        log.info("WebSocket连接建立 - SessionId: {}, CallUuid: {}", sessionId, callUuid);
        
        // 发送连接成功消息
        sendTextMessage(session, createMessage("connected", 
                "sessionId", sessionId,
                "callUuid", callUuid));
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        
        // 清理订阅关系
        Set<String> subscriptions = sessionSubscriptions.remove(sessionId);
        if (subscriptions != null) {
            for (String callUuid : subscriptions) {
                Set<String> subscribers = callSubscribers.get(callUuid);
                if (subscribers != null) {
                    subscribers.remove(sessionId);
                    if (subscribers.isEmpty()) {
                        callSubscribers.remove(callUuid);
                    }
                }
            }
        }
        
        sessions.remove(sessionId);
        
        log.info("WebSocket连接关闭 - SessionId: {}, Status: {}", sessionId, status);
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String payload = message.getPayload();
        
        log.debug("收到文本消息 - SessionId: {}, Payload: {}", sessionId, payload);
        
        try {
            JSONObject json = JSON.parseObject(payload);
            String action = json.getString("action");
            
            switch (action) {
                case "subscribe":
                    // 订阅指定通话的音频流
                    String callUuid = json.getString("callUuid");
                    if (callUuid != null) {
                        subscribe(sessionId, callUuid);
                        sendTextMessage(session, createMessage("subscribed", "callUuid", callUuid));
                    }
                    break;
                    
                case "unsubscribe":
                    // 取消订阅
                    String unsubUuid = json.getString("callUuid");
                    if (unsubUuid != null) {
                        unsubscribe(sessionId, unsubUuid);
                        sendTextMessage(session, createMessage("unsubscribed", "callUuid", unsubUuid));
                    }
                    break;
                    
                case "subscribe_all":
                    // 订阅所有通话
                    subscribe(sessionId, "*");
                    sendTextMessage(session, createMessage("subscribed", "callUuid", "*"));
                    break;
                    
                case "ping":
                    // 心跳
                    sendTextMessage(session, createMessage("pong", "timestamp", System.currentTimeMillis()));
                    break;
                    
                case "get_subscriptions":
                    // 获取当前订阅列表
                    Set<String> subs = sessionSubscriptions.get(sessionId);
                    sendTextMessage(session, createMessage("subscriptions", 
                            "list", subs != null ? subs : Collections.emptySet()));
                    break;
                    
                default:
                    log.warn("未知的action: {}", action);
                    sendTextMessage(session, createMessage("error", "message", "Unknown action: " + action));
            }
            
        } catch (Exception e) {
            log.error("处理消息失败", e);
            sendTextMessage(session, createMessage("error", "message", e.getMessage()));
        }
    }
    
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        // 如果客户端发送音频数据，可以在这里处理
        // 通常客户端只接收音频，不发送
        log.debug("收到二进制消息 - SessionId: {}, Size: {} bytes", 
                session.getId(), message.getPayload().remaining());
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket传输错误 - SessionId: {}", session.getId(), exception);
    }
    
    /**
     * 发送音频帧到订阅者
     * 
     * @param callUuid 通话UUID
     * @param frame 音频帧
     */
    public void sendAudioFrame(String callUuid, AudioFrame frame) {
        // 获取订阅该通话的会话
        Set<String> subscribers = new HashSet<>();
        
        // 特定通话的订阅者
        Set<String> callSubs = callSubscribers.get(callUuid);
        if (callSubs != null) {
            subscribers.addAll(callSubs);
        }
        
        // 订阅所有通话的会话
        Set<String> allSubs = callSubscribers.get("*");
        if (allSubs != null) {
            subscribers.addAll(allSubs);
        }
        
        if (subscribers.isEmpty()) {
            return;
        }
        
        // 发送二进制音频数据
        BinaryMessage binaryMessage = new BinaryMessage(ByteBuffer.wrap(frame.getAudioData()));
        
        // 同时发送元数据(JSON格式)
        String metadata = JSON.toJSONString(Map.of(
                "type", "audio_frame",
                "callUuid", callUuid,
                "timestamp", frame.getTimestamp(),
                "sequenceNumber", frame.getSequenceNumber(),
                "sampleRate", frame.getSampleRate(),
                "channels", frame.getChannels(),
                "size", frame.getAudioData().length
        ));
        TextMessage metaMessage = new TextMessage(metadata);
        
        for (String sessionId : subscribers) {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null && session.isOpen()) {
                try {
                    // 先发送元数据，再发送音频数据
                    session.sendMessage(metaMessage);
                    session.sendMessage(binaryMessage);
                } catch (IOException e) {
                    log.error("发送音频帧失败 - SessionId: {}", sessionId, e);
                }
            }
        }
    }
    
    /**
     * 发送文本消息
     */
    public void sendTextToSubscribers(String callUuid, String message) {
        Set<String> subscribers = new HashSet<>();
        
        Set<String> callSubs = callSubscribers.get(callUuid);
        if (callSubs != null) {
            subscribers.addAll(callSubs);
        }
        
        Set<String> allSubs = callSubscribers.get("*");
        if (allSubs != null) {
            subscribers.addAll(allSubs);
        }
        
        TextMessage textMessage = new TextMessage(message);
        
        for (String sessionId : subscribers) {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    log.error("发送文本消息失败 - SessionId: {}", sessionId, e);
                }
            }
        }
    }
    
    /**
     * 订阅通话
     */
    private void subscribe(String sessionId, String callUuid) {
        sessionSubscriptions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                .add(callUuid);
        callSubscribers.computeIfAbsent(callUuid, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
        
        log.info("订阅成功 - SessionId: {}, CallUuid: {}", sessionId, callUuid);
    }
    
    /**
     * 取消订阅
     */
    private void unsubscribe(String sessionId, String callUuid) {
        Set<String> subscriptions = sessionSubscriptions.get(sessionId);
        if (subscriptions != null) {
            subscriptions.remove(callUuid);
        }
        
        Set<String> subscribers = callSubscribers.get(callUuid);
        if (subscribers != null) {
            subscribers.remove(sessionId);
            if (subscribers.isEmpty()) {
                callSubscribers.remove(callUuid);
            }
        }
        
        log.info("取消订阅 - SessionId: {}, CallUuid: {}", sessionId, callUuid);
    }
    
    /**
     * 从WebSocket路径中提取callUuid
     */
    private String extractCallUuidFromPath(WebSocketSession session) {
        String path = session.getUri().getPath();
        // 路径格式: /ws/audio-stream/{callUuid}
        String[] parts = path.split("/");
        if (parts.length > 3 && "audio-stream".equals(parts[2])) {
            return parts[3];
        }
        return null;
    }
    
    /**
     * 发送文本消息
     */
    private void sendTextMessage(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (IOException e) {
            log.error("发送消息失败", e);
        }
    }
    
    /**
     * 创建JSON消息
     */
    private String createMessage(String type, Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("timestamp", System.currentTimeMillis());
        
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            map.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        
        return JSON.toJSONString(map);
    }
    
    /**
     * 获取活跃会话数量
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
    
    /**
     * 获取指定通话的订阅者数量
     */
    public int getSubscriberCount(String callUuid) {
        Set<String> subscribers = callSubscribers.get(callUuid);
        return subscribers != null ? subscribers.size() : 0;
    }
}
