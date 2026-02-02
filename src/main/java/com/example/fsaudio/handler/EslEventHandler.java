package com.example.fsaudio.handler;

import com.example.fsaudio.model.CallSession;
import com.example.fsaudio.service.AudioStreamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ESL事件处理器
 * 
 * 处理FreeSwtich的各种事件:
 * - CHANNEL_CREATE: 通道创建
 * - CHANNEL_ANSWER: 通话接通
 * - CHANNEL_HANGUP: 通话挂断
 * - CUSTOM: 自定义事件 (包含音频流数据)
 */
@Slf4j
@Component
public class EslEventHandler {

    private final AudioStreamService audioStreamService;

    /**
     * 活跃通话会话
     */
    private final ConcurrentHashMap<String, CallSession> activeSessions = new ConcurrentHashMap<>();

    @Autowired
    public EslEventHandler(@Lazy AudioStreamService audioStreamService) {
        this.audioStreamService = audioStreamService;
    }

    /**
     * 处理ESL事件
     */
    public void handleEvent(Map<String, String> eventData) {
        String eventName = eventData.get("Event-Name");
        String uuid = eventData.get("Unique-ID");

        if (eventName == null) {
            return;
        }

        log.debug("收到ESL事件: {} UUID: {}", eventName, uuid);

        switch (eventName) {
            case "CHANNEL_CREATE":
                handleChannelCreate(uuid, eventData);
                break;
            case "CHANNEL_ANSWER":
                handleChannelAnswer(uuid, eventData);
                break;
            case "CHANNEL_HANGUP":
            case "CHANNEL_HANGUP_COMPLETE":
                handleChannelHangup(uuid, eventData);
                break;
            case "CUSTOM":
                handleCustomEvent(uuid, eventData);
                break;
            case "RECORD_START":
                handleRecordStart(uuid, eventData);
                break;
            case "RECORD_STOP":
                handleRecordStop(uuid, eventData);
                break;
            case "MEDIA_BUG_START":
                handleMediaBugStart(uuid, eventData);
                break;
            case "MEDIA_BUG_STOP":
                handleMediaBugStop(uuid, eventData);
                break;
            default:
                // 其他事件暂不处理
                break;
        }
    }

    /**
     * 处理通道创建事件
     */
    private void handleChannelCreate(String uuid, Map<String, String> eventData) {
        log.info("通道创建: {}", uuid);

        CallSession session = CallSession.builder()
                .uuid(uuid)
                .callerNumber(eventData.get("Caller-Caller-ID-Number"))
                .calleeNumber(eventData.get("Caller-Destination-Number"))
                .startTime(LocalDateTime.now())
                .state(CallSession.CallState.INIT)
                .frameSequence(new AtomicLong(0))
                .audioStreamActive(false)
                .build();

        activeSessions.put(uuid, session);
    }

    /**
     * 处理通话接通事件
     */
    private void handleChannelAnswer(String uuid, Map<String, String> eventData) {
        log.info("通话接通: {}", uuid);

        CallSession session = activeSessions.get(uuid);
        if (session != null) {
            session.setState(CallSession.CallState.ANSWERED);
            
            // 通话接通后，可以开始捕获音频流
            // audioStreamService.startAudioStream(uuid);
        }
    }

    /**
     * 处理通话挂断事件
     */
    private void handleChannelHangup(String uuid, Map<String, String> eventData) {
        String hangupCause = eventData.get("Hangup-Cause");
        log.info("通话挂断: {} 原因: {}", uuid, hangupCause);

        CallSession session = activeSessions.remove(uuid);
        if (session != null) {
            session.setState(CallSession.CallState.HANGUP);
            session.setEndTime(LocalDateTime.now());

            // 停止音频流
            if (session.isAudioStreamActive()) {
                audioStreamService.stopAudioStream(uuid);
            }
        }
    }

    /**
     * 处理自定义事件
     */
    private void handleCustomEvent(String uuid, Map<String, String> eventData) {
        String subclass = eventData.get("Event-Subclass");

        if ("audio_stream::audio_data".equals(subclass)) {
            // 处理音频数据事件
            handleAudioData(uuid, eventData);
        } else if ("mod_audio_stream::connect".equals(subclass)) {
            // 音频流连接事件
            log.info("音频流已连接: {}", uuid);
        } else if ("mod_audio_stream::disconnect".equals(subclass)) {
            // 音频流断开事件
            log.info("音频流已断开: {}", uuid);
        }
    }

    /**
     * 处理音频数据
     */
    private void handleAudioData(String uuid, Map<String, String> eventData) {
        String audioDataBase64 = eventData.get("Audio-Data");
        String direction = eventData.get("Audio-Direction");
        String timestampStr = eventData.get("Audio-Timestamp");

        if (audioDataBase64 != null) {
            try {
                byte[] audioData = java.util.Base64.getDecoder().decode(audioDataBase64);
                long timestamp = timestampStr != null ? Long.parseLong(timestampStr) : System.currentTimeMillis();

                CallSession session = activeSessions.get(uuid);
                long sequence = session != null ? session.getNextSequence() : 0;

                audioStreamService.processAudioData(uuid, audioData, direction, timestamp, sequence);
            } catch (Exception e) {
                log.error("处理音频数据异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 处理录音开始事件
     */
    private void handleRecordStart(String uuid, Map<String, String> eventData) {
        String recordFilePath = eventData.get("Record-File-Path");
        log.info("录音开始: {} 文件: {}", uuid, recordFilePath);
    }

    /**
     * 处理录音停止事件
     */
    private void handleRecordStop(String uuid, Map<String, String> eventData) {
        String recordFilePath = eventData.get("Record-File-Path");
        log.info("录音停止: {} 文件: {}", uuid, recordFilePath);
    }

    /**
     * 处理MediaBug开始事件
     */
    private void handleMediaBugStart(String uuid, Map<String, String> eventData) {
        String bugTarget = eventData.get("Media-Bug-Target");
        log.info("MediaBug开始: {} 目标: {}", uuid, bugTarget);

        CallSession session = activeSessions.get(uuid);
        if (session != null) {
            session.setAudioStreamActive(true);
        }
    }

    /**
     * 处理MediaBug停止事件
     */
    private void handleMediaBugStop(String uuid, Map<String, String> eventData) {
        log.info("MediaBug停止: {}", uuid);

        CallSession session = activeSessions.get(uuid);
        if (session != null) {
            session.setAudioStreamActive(false);
        }
    }

    /**
     * 获取活跃会话
     */
    public CallSession getSession(String uuid) {
        return activeSessions.get(uuid);
    }

    /**
     * 获取所有活跃会话
     */
    public Map<String, CallSession> getAllSessions() {
        return new ConcurrentHashMap<>(activeSessions);
    }

    /**
     * 检查会话是否存在
     */
    public boolean hasSession(String uuid) {
        return activeSessions.containsKey(uuid);
    }

    /**
     * 设置会话音频流状态
     */
    public void setAudioStreamActive(String uuid, boolean active) {
        CallSession session = activeSessions.get(uuid);
        if (session != null) {
            session.setAudioStreamActive(active);
        }
    }
}
