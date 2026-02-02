package com.example.fsaudio.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 通话会话模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallSession {
    
    /**
     * 通话UUID
     */
    private String callUuid;
    
    /**
     * 主叫号码
     */
    private String callerNumber;
    
    /**
     * 被叫号码
     */
    private String calleeNumber;
    
    /**
     * 通话方向
     */
    private String direction;
    
    /**
     * 通话状态
     */
    private CallState state;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 是否正在录音
     */
    private boolean recording;
    
    /**
     * 是否启用音频流
     */
    private boolean audioStreamEnabled;
    
    /**
     * 通话状态枚举
     */
    public enum CallState {
        /**
         * 初始化
         */
        INIT,
        /**
         * 振铃中
         */
        RINGING,
        /**
         * 已接听
         */
        ANSWERED,
        /**
         * 已挂断
         */
        HANGUP,
        /**
         * 失败
         */
        FAILED
    }
}
