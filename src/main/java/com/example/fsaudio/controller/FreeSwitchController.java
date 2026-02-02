package com.example.fsaudio.controller;

import com.example.fsaudio.model.CallSession;
import com.example.fsaudio.service.FreeSwitchApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * FreeSwtich控制器
 * 
 * 提供FreeSwtich操作的REST API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/freeswitch")
public class FreeSwitchController {

    private final FreeSwitchApiService freeSwitchApiService;

    @Autowired
    public FreeSwitchController(FreeSwitchApiService freeSwitchApiService) {
        this.freeSwitchApiService = freeSwitchApiService;
    }

    /**
     * 获取FreeSwtich状态
     * 
     * GET /api/freeswitch/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> result = new HashMap<>();
        
        boolean connected = freeSwitchApiService.isConnected();
        result.put("connected", connected);
        
        if (connected) {
            String status = freeSwitchApiService.getStatus();
            result.put("freeswitchStatus", status);
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取活跃通道列表
     * 
     * GET /api/freeswitch/channels
     */
    @GetMapping("/channels")
    public ResponseEntity<Map<String, Object>> getChannels() {
        Map<String, Object> result = new HashMap<>();
        
        String channels = freeSwitchApiService.showChannels();
        result.put("success", true);
        result.put("channels", channels);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取活跃会话列表
     * 
     * GET /api/freeswitch/sessions
     */
    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getSessions() {
        Map<String, Object> result = new HashMap<>();
        
        Map<String, CallSession> sessions = freeSwitchApiService.getAllSessions();
        result.put("success", true);
        result.put("count", sessions.size());
        result.put("sessions", sessions);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取单个会话信息
     * 
     * GET /api/freeswitch/session/{uuid}
     */
    @GetMapping("/session/{uuid}")
    public ResponseEntity<Map<String, Object>> getSession(@PathVariable String uuid) {
        Map<String, Object> result = new HashMap<>();
        
        CallSession session = freeSwitchApiService.getCallSession(uuid);
        
        if (session != null) {
            result.put("success", true);
            result.put("session", session);
            return ResponseEntity.ok(result);
        } else {
            result.put("success", false);
            result.put("message", "会话不存在");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }
    }

    /**
     * 发起呼叫
     * 
     * POST /api/freeswitch/originate
     */
    @PostMapping("/originate")
    public ResponseEntity<Map<String, Object>> originate(@RequestParam String callerNumber,
                                                         @RequestParam String calleeNumber,
                                                         @RequestParam String gateway) {
        log.info("发起呼叫请求: caller={}, callee={}, gateway={}", callerNumber, calleeNumber, gateway);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String uuid = freeSwitchApiService.originate(callerNumber, calleeNumber, gateway);
            
            if (uuid != null) {
                result.put("success", true);
                result.put("uuid", uuid);
                result.put("message", "呼叫已发起");
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("message", "呼叫发起失败");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
            }
        } catch (Exception e) {
            log.error("发起呼叫异常: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 挂断通话
     * 
     * POST /api/freeswitch/hangup
     */
    @PostMapping("/hangup")
    public ResponseEntity<Map<String, Object>> hangup(@RequestParam String uuid,
                                                      @RequestParam(required = false) String cause) {
        log.info("挂断通话请求: uuid={}, cause={}", uuid, cause);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            freeSwitchApiService.hangup(uuid, cause);
            result.put("success", true);
            result.put("message", "通话已挂断");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("挂断通话异常: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 通话转接
     * 
     * POST /api/freeswitch/bridge
     */
    @PostMapping("/bridge")
    public ResponseEntity<Map<String, Object>> bridge(@RequestParam String uuid,
                                                      @RequestParam String destination) {
        log.info("通话转接请求: uuid={}, destination={}", uuid, destination);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            freeSwitchApiService.bridge(uuid, destination);
            result.put("success", true);
            result.put("message", "通话转接已执行");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("通话转接异常: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 播放音频
     * 
     * POST /api/freeswitch/playback
     */
    @PostMapping("/playback")
    public ResponseEntity<Map<String, Object>> playback(@RequestParam String uuid,
                                                        @RequestParam String audioFile) {
        log.info("播放音频请求: uuid={}, audioFile={}", uuid, audioFile);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            freeSwitchApiService.playback(uuid, audioFile);
            result.put("success", true);
            result.put("message", "音频播放已开始");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("播放音频异常: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 发送DTMF
     * 
     * POST /api/freeswitch/dtmf
     */
    @PostMapping("/dtmf")
    public ResponseEntity<Map<String, Object>> sendDtmf(@RequestParam String uuid,
                                                        @RequestParam String digits) {
        log.info("发送DTMF请求: uuid={}, digits={}", uuid, digits);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            freeSwitchApiService.sendDtmf(uuid, digits);
            result.put("success", true);
            result.put("message", "DTMF已发送");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("发送DTMF异常: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 执行自定义命令
     * 
     * POST /api/freeswitch/command
     */
    @PostMapping("/command")
    public ResponseEntity<Map<String, Object>> executeCommand(@RequestParam String command) {
        log.info("执行命令请求: command={}", command);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String response = freeSwitchApiService.executeCommand(command);
            result.put("success", true);
            result.put("response", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("执行命令异常: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}
