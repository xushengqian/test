package com.example.fsaudio.websocket;

import com.example.fsaudio.audio.AudioStreamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FreeSWitch音频流接收WebSocket服务器
 * 用于接收FreeSWitch通过mod_audio_stream推送的音频数据
 */
@Slf4j
@Component
public class AudioStreamWebSocketServer extends AbstractWebSocketHandler {

    @Autowired
    private AudioStreamService audioStreamService;
    
    /**
     * FreeSWitch连接的会话
     * key: callUuid
     * value: WebSocketSession
     */
    private final Map<String, WebSocketSession> freeswitchSessions = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String callUuid = extractCallUuid(session);
        
        if (callUuid != null) {
            freeswitchSessions.put(callUuid, session);
            log.info("FreeSWitch音频流连接建立 - CallUuid: {}", callUuid);
        } else {
            log.warn("FreeSWitch连接未提供CallUuid - SessionId: {}", session.getId());
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String callUuid = extractCallUuid(session);
        
        if (callUuid != null) {
            freeswitchSessions.remove(callUuid);
            log.info("FreeSWitch音频流连接关闭 - CallUuid: {}, Status: {}", callUuid, status);
        }
    }
    
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        String callUuid = extractCallUuid(session);
        
        if (callUuid != null) {
            ByteBuffer buffer = message.getPayload();
            byte[] audioData = new byte[buffer.remaining()];
            buffer.get(audioData);
            
            // 处理接收到的音频数据
            audioStreamService.processAudioData(callUuid, audioData);
            
            log.trace("收到FreeSWitch音频数据 - CallUuid: {}, Size: {} bytes", 
                    callUuid, audioData.length);
        }
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // FreeSWitch可能会发送一些控制消息
        String callUuid = extractCallUuid(session);
        log.debug("收到FreeSWitch文本消息 - CallUuid: {}, Message: {}", 
                callUuid, message.getPayload());
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String callUuid = extractCallUuid(session);
        log.error("FreeSWitch连接传输错误 - CallUuid: {}", callUuid, exception);
    }
    
    /**
     * 从WebSocket会话中提取callUuid
     */
    private String extractCallUuid(WebSocketSession session) {
        String path = session.getUri().getPath();
        // 路径格式: /audio-stream/{callUuid}
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("audio-stream".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        
        // 尝试从查询参数获取
        String query = session.getUri().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=");
                if (kv.length == 2 && "uuid".equals(kv[0])) {
                    return kv[1];
                }
            }
        }
        
        return null;
    }
    
    /**
     * 检查指定通话是否有FreeSWitch连接
     */
    public boolean hasConnection(String callUuid) {
        return freeswitchSessions.containsKey(callUuid);
    }
    
    /**
     * 获取活跃连接数
     */
    public int getActiveConnectionCount() {
        return freeswitchSessions.size();
    }
}
