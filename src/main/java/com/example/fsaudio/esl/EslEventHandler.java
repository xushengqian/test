package com.example.fsaudio.esl;

import com.example.fsaudio.audio.AudioStreamService;
import com.example.fsaudio.model.CallSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ESL事件处理器
 * 处理FreeSWitch推送的各类事件
 */
@Slf4j
@Component
public class EslEventHandler {

    @Autowired
    private EslClient eslClient;
    
    @Autowired
    @Lazy
    private AudioStreamService audioStreamService;
    
    /**
     * 活跃的通话会话
     */
    private final Map<String, CallSession> activeSessions = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        // 注册事件监听器
        registerEventListeners();
    }
    
    /**
     * 注册事件监听器
     */
    private void registerEventListeners() {
        // 通道创建事件
        eslClient.addEventListener("CHANNEL_CREATE", this::handleChannelCreate);
        
        // 通道应答事件
        eslClient.addEventListener("CHANNEL_ANSWER", this::handleChannelAnswer);
        
        // 通道挂断事件
        eslClient.addEventListener("CHANNEL_HANGUP", this::handleChannelHangup);
        
        // 通道挂断完成事件
        eslClient.addEventListener("CHANNEL_HANGUP_COMPLETE", this::handleChannelHangupComplete);
        
        // DTMF按键事件
        eslClient.addEventListener("DTMF", this::handleDtmf);
        
        // 自定义事件
        eslClient.addEventListener("CUSTOM", this::handleCustomEvent);
        
        // 媒体bug事件(用于音频流)
        eslClient.addEventListener("MEDIA_BUG_START", this::handleMediaBugStart);
        eslClient.addEventListener("MEDIA_BUG_STOP", this::handleMediaBugStop);
        
        // 录音事件
        eslClient.addEventListener("RECORD_START", this::handleRecordStart);
        eslClient.addEventListener("RECORD_STOP", this::handleRecordStop);
        
        log.info("ESL事件监听器已注册");
    }
    
    /**
     * 处理通道创建事件
     */
    private void handleChannelCreate(Map<String, String> event) {
        String uuid = event.get("Unique-ID");
        String callerNumber = event.get("Caller-Caller-ID-Number");
        String calleeNumber = event.get("Caller-Destination-Number");
        String direction = event.get("Call-Direction");
        
        log.info("通道创建 - UUID: {}, 主叫: {}, 被叫: {}, 方向: {}", 
                uuid, callerNumber, calleeNumber, direction);
        
        CallSession session = CallSession.builder()
                .callUuid(uuid)
                .callerNumber(callerNumber)
                .calleeNumber(calleeNumber)
                .direction(direction)
                .state(CallSession.CallState.INIT)
                .startTime(LocalDateTime.now())
                .build();
        
        activeSessions.put(uuid, session);
    }
    
    /**
     * 处理通道应答事件
     */
    private void handleChannelAnswer(Map<String, String> event) {
        String uuid = event.get("Unique-ID");
        
        log.info("通道应答 - UUID: {}", uuid);
        
        CallSession session = activeSessions.get(uuid);
        if (session != null) {
            session.setState(CallSession.CallState.ANSWERED);
            
            // 自动启动音频流(可根据需要配置)
            // audioStreamService.startAudioStreamForCall(uuid);
        }
    }
    
    /**
     * 处理通道挂断事件
     */
    private void handleChannelHangup(Map<String, String> event) {
        String uuid = event.get("Unique-ID");
        String hangupCause = event.get("Hangup-Cause");
        
        log.info("通道挂断 - UUID: {}, 原因: {}", uuid, hangupCause);
        
        CallSession session = activeSessions.get(uuid);
        if (session != null) {
            session.setState(CallSession.CallState.HANGUP);
            session.setEndTime(LocalDateTime.now());
            
            // 停止音频流
            if (session.isAudioStreamEnabled()) {
                audioStreamService.stopAudioStream(uuid);
            }
        }
    }
    
    /**
     * 处理通道挂断完成事件
     */
    private void handleChannelHangupComplete(Map<String, String> event) {
        String uuid = event.get("Unique-ID");
        
        log.info("通道挂断完成 - UUID: {}", uuid);
        
        // 清理会话
        activeSessions.remove(uuid);
    }
    
    /**
     * 处理DTMF按键事件
     */
    private void handleDtmf(Map<String, String> event) {
        String uuid = event.get("Unique-ID");
        String digit = event.get("DTMF-Digit");
        String duration = event.get("DTMF-Duration");
        
        log.info("DTMF按键 - UUID: {}, 按键: {}, 时长: {}", uuid, digit, duration);
    }
    
    /**
     * 处理自定义事件
     */
    private void handleCustomEvent(Map<String, String> event) {
        String eventSubclass = event.get("Event-Subclass");
        String uuid = event.get("Unique-ID");
        
        log.debug("自定义事件 - UUID: {}, 子类: {}", uuid, eventSubclass);
        
        // 处理音频流相关的自定义事件
        if ("audio_stream::data".equals(eventSubclass)) {
            handleAudioStreamData(event);
        }
    }
    
    /**
     * 处理音频流数据事件
     */
    private void handleAudioStreamData(Map<String, String> event) {
        String uuid = event.get("Unique-ID");
        String audioData = event.get("Audio-Data");
        
        if (audioData != null && !audioData.isEmpty()) {
            // 解码Base64音频数据并转发
            byte[] data = java.util.Base64.getDecoder().decode(audioData);
            audioStreamService.processAudioData(uuid, data);
        }
    }
    
    /**
     * 处理媒体bug启动事件
     */
    private void handleMediaBugStart(Map<String, String> event) {
        String uuid = event.get("Unique-ID");
        String bugTarget = event.get("Media-Bug-Target");
        
        log.info("媒体Bug启动 - UUID: {}, Target: {}", uuid, bugTarget);
        
        CallSession session = activeSessions.get(uuid);
        if (session != null) {
            session.setAudioStreamEnabled(true);
        }
    }
    
    /**
     * 处理媒体bug停止事件
     */
    private void handleMediaBugStop(Map<String, String> event) {
        String uuid = event.get("Unique-ID");
        
        log.info("媒体Bug停止 - UUID: {}", uuid);
        
        CallSession session = activeSessions.get(uuid);
        if (session != null) {
            session.setAudioStreamEnabled(false);
        }
    }
    
    /**
     * 处理录音开始事件
     */
    private void handleRecordStart(Map<String, String> event) {
        String uuid = event.get("Unique-ID");
        String recordFilePath = event.get("Record-File-Path");
        
        log.info("录音开始 - UUID: {}, 文件: {}", uuid, recordFilePath);
        
        CallSession session = activeSessions.get(uuid);
        if (session != null) {
            session.setRecording(true);
        }
    }
    
    /**
     * 处理录音停止事件
     */
    private void handleRecordStop(Map<String, String> event) {
        String uuid = event.get("Unique-ID");
        String recordFilePath = event.get("Record-File-Path");
        
        log.info("录音停止 - UUID: {}, 文件: {}", uuid, recordFilePath);
        
        CallSession session = activeSessions.get(uuid);
        if (session != null) {
            session.setRecording(false);
        }
    }
    
    /**
     * 获取会话信息
     */
    public CallSession getSession(String uuid) {
        return activeSessions.get(uuid);
    }
    
    /**
     * 获取所有活跃会话
     */
    public Map<String, CallSession> getActiveSessions() {
        return new ConcurrentHashMap<>(activeSessions);
    }
    
    /**
     * 初始化并连接ESL
     */
    public void connectEsl() {
        if (!eslClient.isConnected()) {
            boolean connected = eslClient.connect();
            if (connected) {
                // 订阅需要的事件
                eslClient.subscribeEvents(
                    "CHANNEL_CREATE",
                    "CHANNEL_ANSWER",
                    "CHANNEL_HANGUP",
                    "CHANNEL_HANGUP_COMPLETE",
                    "DTMF",
                    "CUSTOM",
                    "MEDIA_BUG_START",
                    "MEDIA_BUG_STOP",
                    "RECORD_START",
                    "RECORD_STOP"
                );
            }
        }
    }
}
