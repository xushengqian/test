package com.example.fsaudio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 音频流配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "audio.stream")
public class AudioStreamConfig {
    
    /**
     * 采样率 (8000 或 16000 Hz)
     */
    private int sampleRate = 8000;
    
    /**
     * 位深度 (通常为16位)
     */
    private int bitDepth = 16;
    
    /**
     * 声道数 (1:单声道, 2:立体声)
     */
    private int channels = 1;
    
    /**
     * 音频格式
     */
    private String format = "L16";
    
    /**
     * 每个音频包的毫秒数
     */
    private int packetTimeMs = 20;
    
    /**
     * 音频缓冲区大小(字节)
     */
    private int bufferSize = 640;
    
    /**
     * 计算每帧的字节数
     * 公式: 采样率 * 位深度 * 声道数 * 时间(秒) / 8
     */
    public int getBytesPerFrame() {
        return sampleRate * bitDepth * channels * packetTimeMs / 8 / 1000;
    }
}
