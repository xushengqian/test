package com.example.fsaudio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FreeSWitch实时音频流应用主入口
 * 
 * 功能说明:
 * 1. 通过ESL连接FreeSWitch获取实时音频流
 * 2. 支持WebSocket推送音频数据
 * 3. 支持HTTP接口获取音频流
 */
@SpringBootApplication
@EnableScheduling
public class FsAudioStreamApplication {

    public static void main(String[] args) {
        SpringApplication.run(FsAudioStreamApplication.class, args);
    }
}
