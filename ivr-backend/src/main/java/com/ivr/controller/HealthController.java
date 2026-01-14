package com.ivr.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/status")
    public Map<String, String> status() {
        return Map.of("status", "UP", "message", "IVR Backend is running");
    }
}
