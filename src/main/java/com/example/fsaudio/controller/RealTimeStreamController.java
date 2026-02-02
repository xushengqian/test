package com.example.fsaudio.controller;

import com.example.fsaudio.handler.RealTimeAudioStreamHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 实时音频流控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/realtime")
public class RealTimeStreamController {

    private final RealTimeAudioStreamHandler streamHandler;

    @Autowired
    public RealTimeStreamController(RealTimeAudioStreamHandler streamHandler) {
        this.streamHandler = streamHandler;
    }

    /**
     * 启动TCP音频流服务器
     * 
     * POST /api/realtime/tcp/start
     */
    @PostMapping("/tcp/start")
    public ResponseEntity<Map<String, Object>> startTcpServer(
            @RequestParam(defaultValue = "9000") int port) {
        
        log.info("启动TCP音频流服务器请求: port={}", port);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            streamHandler.startTcpServer(port);
            result.put("success", true);
            result.put("message", "TCP服务器已启动");
            result.put("port", port);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("启动TCP服务器异常: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 停止TCP音频流服务器
     * 
     * POST /api/realtime/tcp/stop
     */
    @PostMapping("/tcp/stop")
    public ResponseEntity<Map<String, Object>> stopTcpServer() {
        log.info("停止TCP音频流服务器请求");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            streamHandler.stopTcpServer();
            result.put("success", true);
            result.put("message", "TCP服务器已停止");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("停止TCP服务器异常: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 从管道读取音频流
     * 
     * POST /api/realtime/pipe/start
     */
    @PostMapping("/pipe/start")
    public ResponseEntity<Map<String, Object>> startPipeReader(
            @RequestParam String pipePath,
            @RequestParam String uuid) {
        
        log.info("启动管道读取请求: uuid={}, pipePath={}", uuid, pipePath);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            streamHandler.readFromPipe(pipePath, uuid);
            result.put("success", true);
            result.put("message", "管道读取已启动");
            result.put("uuid", uuid);
            result.put("pipePath", pipePath);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("启动管道读取异常: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 获取活跃连接状态
     * 
     * GET /api/realtime/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> result = new HashMap<>();
        
        result.put("success", true);
        result.put("activeConnections", streamHandler.getActiveConnectionCount());
        
        return ResponseEntity.ok(result);
    }

    /**
     * 检查特定UUID的连接
     * 
     * GET /api/realtime/connection/{uuid}
     */
    @GetMapping("/connection/{uuid}")
    public ResponseEntity<Map<String, Object>> checkConnection(@PathVariable String uuid) {
        Map<String, Object> result = new HashMap<>();
        
        result.put("success", true);
        result.put("uuid", uuid);
        result.put("connected", streamHandler.hasConnection(uuid));
        
        return ResponseEntity.ok(result);
    }
}
