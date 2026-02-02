package com.example.fsaudio.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

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
    private String uuid;

    /**
     * 主叫号码
     */
    private String callerNumber;

    /**
     * 被叫号码
     */
    private String calleeNumber;

    /**
     * 通话开始时间
     */
    private LocalDateTime startTime;

    /**
     * 通话结束时间
     */
    private LocalDateTime endTime;

    /**
     * 通话状态
     */
    private CallState state;

    /**
     * 音频帧序列号生成器
     */
    private AtomicLong frameSequence;

    /**
     * 是否正在录制音频流
     */
    private boolean audioStreamActive;

    /**
     * 通话状态枚举
     */
    public enum CallState {
        /** 初始化 */
        INIT,
        /** 振铃中 */
        RINGING,
        /** 已接通 */
        ANSWERED,
        /** 已挂断 */
        HANGUP,
        /** 错误 */
        ERROR
    }

    /**
     * 获取下一个帧序列号
     */
    public long getNextSequence() {
        if (frameSequence == null) {
            frameSequence = new AtomicLong(0);
        }
        return frameSequence.incrementAndGet();
    }
}
