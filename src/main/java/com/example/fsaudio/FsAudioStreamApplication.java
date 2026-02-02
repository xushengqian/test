package com.example.fsaudio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FreeSwtich实时音频流获取应用
 * 
 * 主要功能:
 * 1. 通过ESL连接FreeSwtich
 * 2. 监听通话事件
 * 3. 获取实时音频流数据
 * 4. 通过WebSocket推送音频数据
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class FsAudioStreamApplication {

    public static void main(String[] args) {
        SpringApplication.run(FsAudioStreamApplication.class, args);
    }
}
