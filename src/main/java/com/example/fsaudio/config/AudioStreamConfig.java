package com.example.fsaudio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 音频流配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "audio.stream")
public class AudioStreamConfig {

    /**
     * 音频采样率 (Hz)
     */
    private int sampleRate = 8000;

    /**
     * 音频通道数 (1=单声道, 2=立体声)
     */
    private int channels = 1;

    /**
     * 每帧采样数
     */
    private int samplesPerFrame = 160;

    /**
     * 音频格式
     */
    private String format = "L16";

    /**
     * 缓冲区大小
     */
    private int bufferSize = 4096;

    /**
     * 是否启用WebSocket输出
     */
    private boolean websocketEnabled = true;

    /**
     * 音频文件保存路径
     */
    private String savePath = "/tmp/fs-audio";
}
