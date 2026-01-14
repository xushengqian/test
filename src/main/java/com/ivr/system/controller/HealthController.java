package com.ivr.system.controller;

import com.ivr.system.esl.FreeSwitchClient;
import com.ivr.system.service.IvrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final FreeSwitchClient freeSwitchClient;
    private final IvrService ivrService;

    /**
     * 健康检查
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("freeswitchConnected", freeSwitchClient.isConnected());

        Map<String, Object> stats = ivrService.getSessionStats();
        health.put("stats", stats);

        return ResponseEntity.ok(health);
    }

    /**
     * 存活检查
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> liveness() {
        Map<String, String> result = new HashMap<>();
        result.put("status", "UP");
        return ResponseEntity.ok(result);
    }

    /**
     * 就绪检查
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> result = new HashMap<>();

        boolean isReady = freeSwitchClient.isConnected();
        result.put("status", isReady ? "UP" : "DOWN");
        result.put("freeswitchConnected", freeSwitchClient.isConnected());

        if (isReady) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(503).body(result);
        }
    }
}
