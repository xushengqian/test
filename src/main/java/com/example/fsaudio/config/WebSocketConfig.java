package com.example.fsaudio.config;

import com.example.fsaudio.websocket.AudioWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket配置类
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private AudioWebSocketHandler audioWebSocketHandler;
    
    @Value("${websocket.allowed-origins:*}")
    private String allowedOrigins;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册音频流WebSocket处理器
        registry.addHandler(audioWebSocketHandler, "/ws/audio-stream/{callUuid}")
                .setAllowedOrigins(allowedOrigins.split(","));
        
        // 也支持不带UUID的连接，之后通过消息指定
        registry.addHandler(audioWebSocketHandler, "/ws/audio-stream")
                .setAllowedOrigins(allowedOrigins.split(","));
    }
}
