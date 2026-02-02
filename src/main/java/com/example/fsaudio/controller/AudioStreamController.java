package com.example.fsaudio.controller;

import com.example.fsaudio.audio.AudioStreamService;
import com.example.fsaudio.esl.EslClient;
import com.example.fsaudio.esl.EslEventHandler;
import com.example.fsaudio.model.AudioStreamRequest;
import com.example.fsaudio.model.CallSession;
import com.example.fsaudio.websocket.AudioWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 音频流控制器
 * 提供REST API管理音频流
 */
@Slf4j
@RestController
@RequestMapping("/api/audio")
public class AudioStreamController {

    @Autowired
    private AudioStreamService audioStreamService;
    
    @Autowired
    private EslClient eslClient;
    
    @Autowired
    private EslEventHandler eslEventHandler;
    
    @Autowired
    private AudioWebSocketHandler webSocketHandler;
    
    /**
     * 启动音频流
     * 
     * POST /api/audio/stream/start
     * Body: {"callUuid": "xxx", "direction": "both", "websocketUrl": "ws://..."}
     */
    @PostMapping("/stream/start")
    public ResponseEntity<Map<String, Object>> startAudioStream(
            @RequestBody AudioStreamRequest request) {
        
        log.info("启动音频流请求: {}", request);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean success = audioStreamService.startAudioStreamForCall(
                    request.getCallUuid(), 
                    request.getDirection() != null ? request.getDirection() : "both");
            
            if (success) {
                result.put("success", true);
                result.put("message", "音频流已启动");
                result.put("callUuid", request.getCallUuid());
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("message", "启动音频流失败");
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("启动音频流异常", e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
    
    /**
     * 使用mod_audio_stream方式启动音频流
     * 
     * POST /api/audio/stream/start-ws
     */
    @PostMapping("/stream/start-ws")
    public ResponseEntity<Map<String, Object>> startAudioStreamWithWs(
            @RequestBody AudioStreamRequest request) {
        
        log.info("使用WebSocket启动音频流请求: {}", request);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String wsUrl = request.getWebsocketUrl();
            if (wsUrl == null || wsUrl.isEmpty()) {
                wsUrl = "ws://127.0.0.1:8080/ws/audio-stream/" + request.getCallUuid();
            }
            
            boolean success = audioStreamService.startAudioStreamWithModule(
                    request.getCallUuid(), wsUrl);
            
            if (success) {
                result.put("success", true);
                result.put("message", "音频流已启动(WebSocket模式)");
                result.put("callUuid", request.getCallUuid());
                result.put("wsUrl", wsUrl);
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("message", "启动音频流失败");
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("启动音频流异常", e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
    
    /**
     * 停止音频流
     * 
     * POST /api/audio/stream/stop
     */
    @PostMapping("/stream/stop")
    public ResponseEntity<Map<String, Object>> stopAudioStream(
            @RequestParam String callUuid) {
        
        log.info("停止音频流请求: callUuid={}", callUuid);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            audioStreamService.stopAudioStream(callUuid);
            
            result.put("success", true);
            result.put("message", "音频流已停止");
            result.put("callUuid", callUuid);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("停止音频流异常", e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
    
    /**
     * 获取活跃的音频流会话
     * 
     * GET /api/audio/sessions
     */
    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getActiveSessions() {
        Map<String, Object> result = new HashMap<>();
        
        result.put("success", true);
        result.put("audioSessions", audioStreamService.getActiveSessions());
        result.put("callSessions", eslEventHandler.getActiveSessions());
        result.put("wsSessionCount", webSocketHandler.getActiveSessionCount());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取指定通话的会话信息
     * 
     * GET /api/audio/session/{callUuid}
     */
    @GetMapping("/session/{callUuid}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String callUuid) {
        Map<String, Object> result = new HashMap<>();
        
        CallSession session = eslEventHandler.getSession(callUuid);
        
        if (session != null) {
            result.put("success", true);
            result.put("session", session);
            result.put("subscriberCount", webSocketHandler.getSubscriberCount(callUuid));
            return ResponseEntity.ok(result);
        } else {
            result.put("success", false);
            result.put("message", "会话不存在");
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 连接FreeSWitch ESL
     * 
     * POST /api/audio/esl/connect
     */
    @PostMapping("/esl/connect")
    public ResponseEntity<Map<String, Object>> connectEsl() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            eslEventHandler.connectEsl();
            
            result.put("success", true);
            result.put("connected", eslClient.isConnected());
            result.put("message", eslClient.isConnected() ? "ESL连接成功" : "ESL连接失败");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("连接ESL异常", e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
    
    /**
     * 断开FreeSWitch ESL
     * 
     * POST /api/audio/esl/disconnect
     */
    @PostMapping("/esl/disconnect")
    public ResponseEntity<Map<String, Object>> disconnectEsl() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            eslClient.disconnect();
            
            result.put("success", true);
            result.put("message", "ESL已断开");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("断开ESL异常", e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
    
    /**
     * 获取ESL状态
     * 
     * GET /api/audio/esl/status
     */
    @GetMapping("/esl/status")
    public ResponseEntity<Map<String, Object>> getEslStatus() {
        Map<String, Object> result = new HashMap<>();
        
        result.put("connected", eslClient.isConnected());
        
        if (eslClient.isConnected()) {
            String status = eslClient.executeApi("status");
            result.put("status", status);
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 执行ESL API命令
     * 
     * POST /api/audio/esl/api
     */
    @PostMapping("/esl/api")
    public ResponseEntity<Map<String, Object>> executeEslApi(
            @RequestParam String command) {
        
        Map<String, Object> result = new HashMap<>();
        
        if (!eslClient.isConnected()) {
            result.put("success", false);
            result.put("message", "ESL未连接");
            return ResponseEntity.badRequest().body(result);
        }
        
        try {
            String response = eslClient.executeApi(command);
            
            result.put("success", true);
            result.put("response", response);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("执行ESL命令异常", e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
    
    /**
     * 开始录音
     * 
     * POST /api/audio/record/start
     */
    @PostMapping("/record/start")
    public ResponseEntity<Map<String, Object>> startRecording(
            @RequestParam String callUuid,
            @RequestParam(required = false) String filePath) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (filePath == null || filePath.isEmpty()) {
                filePath = "/tmp/recordings/" + callUuid + "_" + System.currentTimeMillis() + ".wav";
            }
            
            boolean success = eslClient.startRecordSession(callUuid, filePath);
            
            if (success) {
                result.put("success", true);
                result.put("message", "录音已开始");
                result.put("filePath", filePath);
            } else {
                result.put("success", false);
                result.put("message", "开始录音失败");
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("开始录音异常", e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
    
    /**
     * 停止录音
     * 
     * POST /api/audio/record/stop
     */
    @PostMapping("/record/stop")
    public ResponseEntity<Map<String, Object>> stopRecording(
            @RequestParam String callUuid) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean success = eslClient.stopRecordSession(callUuid);
            
            result.put("success", success);
            result.put("message", success ? "录音已停止" : "停止录音失败");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("停止录音异常", e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}
