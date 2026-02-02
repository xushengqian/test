package com.example.fsaudio.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 音频流请求参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioStreamRequest {
    
    /**
     * 通话UUID
     */
    private String callUuid;
    
    /**
     * 音频方向: read(接收)、write(发送)、both(双向)
     */
    private String direction;
    
    /**
     * WebSocket推送地址
     */
    private String websocketUrl;
    
    /**
     * HTTP回调地址
     */
    private String callbackUrl;
    
    /**
     * 采样率
     */
    private Integer sampleRate;
    
    /**
     * 是否启用VAD
     */
    private Boolean enableVad;
}
