package com.example.metaspace.controller;

import com.example.metaspace.service.MemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    @Autowired
    private MemoryService memoryService;

    @GetMapping("/info")
    public Map<String, Object> getMemoryInfo() {
        return memoryService.getMemoryInfo();
    }

    @GetMapping("/metaspace")
    public Map<String, Object> getMetaspaceInfo() {
        return memoryService.getMetaspaceInfo();
    }

    @GetMapping("/trigger-oom")
    public String triggerMetaspaceOOM() {
        return memoryService.triggerMetaspaceOOM();
    }

    @GetMapping("/gc")
    public String forceGarbageCollection() {
        return memoryService.forceGarbageCollection();
    }
}