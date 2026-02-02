package com.example.fsaudio.controller;

import com.example.fsaudio.model.CallSession;
import com.example.fsaudio.model.StreamRequest;
import com.example.fsaudio.service.AudioStreamService;
import com.example.fsaudio.service.FreeSwitchApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 音频流控制器
 * 
 * 提供音频流管理的REST API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/audio")
public class AudioStreamController {

    private final AudioStreamService audioStreamService;
    private final FreeSwitchApiService freeSwitchApiService;

    @Autowired
    public AudioStreamController(AudioStreamService audioStreamService,
                                 FreeSwitchApiService freeSwitchApiService) {
        this.audioStreamService = audioStreamService;
        this.freeSwitchApiService = freeSwitchApiService;
    }

    /**
     * 启动音频流
     * 
     * POST /api/audio/stream/start
     * 
     * @param request 流请求参数
     */
    @PostMapping("/stream/start")
    public ResponseEntity<Map<String, Object>> startStream(@RequestBody StreamRequest request) {
        log.info("启动音频流请求: uuid={}", request.getUuid());
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            boolean success = audioStreamService.startAudioStream(request);
            
            if (success) {
                result.put("success", true);
                result.put("message", "音频流已启动");
                result.put("uuid", request.getUuid());
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("message", "启动音频流失败");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
            }
        } catch (Exception e) {
            log.error("启动音频流异常: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 停止音频流
     * 
     * POST /api/audio/stream/stop
     */
    @PostMapping("/stream/stop")
    public ResponseEntity<Map<String, Object>> stopStream(@RequestParam String uuid) {
        log.info("停止音频流请求: uuid={}", uuid);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            audioStreamService.stopAudioStream(uuid);
            result.put("success", true);
            result.put("message", "音频流已停止");
            result.put("uuid", uuid);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("停止音频流异常: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 获取音频数据
     * 
     * GET /api/audio/data/{uuid}
     */
    @GetMapping("/data/{uuid}")
    public ResponseEntity<byte[]> getAudioData(@PathVariable String uuid,
                                                @RequestParam(defaultValue = "100") int maxFrames) {
        byte[] audioData = audioStreamService.getAudioData(uuid, maxFrames);
        
        if (audioData.length == 0) {
            return ResponseEntity.noContent().build();
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", uuid + ".pcm");
        
        return new ResponseEntity<>(audioData, headers, HttpStatus.OK);
    }

    /**
     * 获取活跃的音频流列表
     * 
     * GET /api/audio/streams
     */
    @GetMapping("/streams")
    public ResponseEntity<Map<String, Object>> getActiveStreams() {
        Set<String> streams = audioStreamService.getActiveStreams();
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", streams.size());
        result.put("streams", streams);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 检查音频流状态
     * 
     * GET /api/audio/stream/{uuid}/status
     */
    @GetMapping("/stream/{uuid}/status")
    public ResponseEntity<Map<String, Object>> getStreamStatus(@PathVariable String uuid) {
        Map<String, Object> result = new HashMap<>();
        
        boolean active = audioStreamService.isStreamActive(uuid);
        CallSession session = freeSwitchApiService.getCallSession(uuid);
        
        result.put("success", true);
        result.put("uuid", uuid);
        result.put("streamActive", active);
        
        if (session != null) {
            result.put("callState", session.getState());
            result.put("callerNumber", session.getCallerNumber());
            result.put("calleeNumber", session.getCalleeNumber());
            result.put("startTime", session.getStartTime());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 开始录音
     * 
     * POST /api/audio/record/start
     */
    @PostMapping("/record/start")
    public ResponseEntity<Map<String, Object>> startRecord(@RequestParam String uuid,
                                                           @RequestParam String filePath) {
        log.info("开始录音请求: uuid={}, filePath={}", uuid, filePath);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            freeSwitchApiService.startRecord(uuid, filePath);
            result.put("success", true);
            result.put("message", "录音已开始");
            result.put("uuid", uuid);
            result.put("filePath", filePath);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("开始录音异常: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 停止录音
     * 
     * POST /api/audio/record/stop
     */
    @PostMapping("/record/stop")
    public ResponseEntity<Map<String, Object>> stopRecord(@RequestParam String uuid,
                                                          @RequestParam String filePath) {
        log.info("停止录音请求: uuid={}, filePath={}", uuid, filePath);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            freeSwitchApiService.stopRecord(uuid, filePath);
            result.put("success", true);
            result.put("message", "录音已停止");
            result.put("uuid", uuid);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("停止录音异常: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}
