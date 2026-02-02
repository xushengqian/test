package com.example.fsaudio.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 音频帧数据模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioFrame {
    
    /**
     * 通话UUID
     */
    private String callUuid;
    
    /**
     * 音频数据(PCM格式)
     */
    private byte[] audioData;
    
    /**
     * 时间戳(毫秒)
     */
    private long timestamp;
    
    /**
     * 采样率
     */
    private int sampleRate;
    
    /**
     * 声道数
     */
    private int channels;
    
    /**
     * 音频方向: inbound(来电方) / outbound(外呼方)
     */
    private String direction;
    
    /**
     * 序列号
     */
    private long sequenceNumber;
}
