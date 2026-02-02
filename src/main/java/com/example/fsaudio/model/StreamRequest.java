package com.example.fsaudio.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 音频流请求模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamRequest {

    /**
     * 通话UUID
     */
    private String uuid;

    /**
     * 流方向: both=双向, in=仅入方向, out=仅出方向
     */
    private String direction;

    /**
     * WebSocket回调地址 (用于推送音频流)
     */
    private String websocketUrl;

    /**
     * HTTP回调地址 (用于推送音频数据)
     */
    private String callbackUrl;

    /**
     * 是否保存到文件
     */
    private boolean saveToFile;

    /**
     * 采样率
     */
    private Integer sampleRate;

    /**
     * 通道数
     */
    private Integer channels;
}
