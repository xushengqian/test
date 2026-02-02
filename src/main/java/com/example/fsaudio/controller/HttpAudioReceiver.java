package com.example.fsaudio.controller;

import com.example.fsaudio.audio.AudioStreamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;

/**
 * HTTP音频流接收器
 * 用于接收FreeSWitch通过HTTP POST方式推送的音频数据
 */
@Slf4j
@RestController
@RequestMapping("/audio-stream")
public class HttpAudioReceiver {

    @Autowired
    private AudioStreamService audioStreamService;
    
    /**
     * 接收音频数据
     * FreeSWitch可以通过mod_shout或自定义脚本将音频POST到这个接口
     * 
     * POST /audio-stream/{callUuid}
     */
    @PostMapping(value = "/{callUuid}", 
            consumes = {MediaType.APPLICATION_OCTET_STREAM_VALUE, "audio/L16", "audio/pcm"})
    public ResponseEntity<String> receiveAudio(
            @PathVariable String callUuid,
            HttpServletRequest request) {
        
        try (InputStream inputStream = request.getInputStream()) {
            byte[] audioData = inputStream.readAllBytes();
            
            if (audioData.length > 0) {
                audioStreamService.processAudioData(callUuid, audioData);
                log.trace("收到HTTP音频数据 - CallUuid: {}, Size: {} bytes", 
                        callUuid, audioData.length);
            }
            
            return ResponseEntity.ok("OK");
            
        } catch (Exception e) {
            log.error("接收音频数据失败", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
    
    /**
     * 流式接收音频数据
     * 支持长连接持续接收音频流
     * 
     * POST /audio-stream/{callUuid}/stream
     */
    @PostMapping(value = "/{callUuid}/stream",
            consumes = {MediaType.APPLICATION_OCTET_STREAM_VALUE, "audio/L16", "audio/pcm"})
    public ResponseEntity<String> receiveAudioStream(
            @PathVariable String callUuid,
            HttpServletRequest request) {
        
        try (InputStream inputStream = request.getInputStream()) {
            byte[] buffer = new byte[640]; // 20ms @ 8kHz 16bit mono
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (bytesRead > 0) {
                    byte[] audioData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, audioData, 0, bytesRead);
                    
                    audioStreamService.processAudioData(callUuid, audioData);
                }
            }
            
            log.info("音频流接收完成 - CallUuid: {}", callUuid);
            return ResponseEntity.ok("Stream completed");
            
        } catch (Exception e) {
            log.error("接收音频流失败", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
    
    /**
     * 接收multipart音频数据
     * 支持表单方式上传音频
     * 
     * POST /audio-stream/{callUuid}/upload
     */
    @PostMapping(value = "/{callUuid}/upload", 
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadAudio(
            @PathVariable String callUuid,
            @RequestParam("audio") byte[] audioData) {
        
        try {
            if (audioData != null && audioData.length > 0) {
                audioStreamService.processAudioData(callUuid, audioData);
                log.info("收到上传的音频数据 - CallUuid: {}, Size: {} bytes", 
                        callUuid, audioData.length);
            }
            
            return ResponseEntity.ok("OK");
            
        } catch (Exception e) {
            log.error("处理上传音频失败", e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
}
