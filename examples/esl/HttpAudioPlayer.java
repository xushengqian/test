package com.example.freeswitch;

import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.inbound.InboundConnectionFailure;
import org.freeswitch.esl.client.transport.message.EslMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * FreeSWITCH ESL HTTP 音频播放器
 * 
 * 通过 ESL (Event Socket Library) 从 HTTP 服务器拉取音频流播放
 * 
 * 依赖：
 * - org.freeswitch.esl:org.freeswitch.esl.client
 * 
 * Maven 依赖：
 * <dependency>
 *     <groupId>org.freeswitch.esl</groupId>
 *     <artifactId>org.freeswitch.esl.client</artifactId>
 *     <version>0.10.1</version>
 * </dependency>
 */
public class HttpAudioPlayer {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpAudioPlayer.class);
    
    private final String host;
    private final int port;
    private final String password;
    private Client eslClient;
    private final ExecutorService executor;
    
    /**
     * 构造函数
     * 
     * @param host FreeSWITCH 主机地址
     * @param port ESL 端口（默认 8021）
     * @param password ESL 密码
     */
    public HttpAudioPlayer(String host, int port, String password) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.executor = Executors.newCachedThreadPool();
    }
    
    /**
     * 连接到 FreeSWITCH
     */
    public boolean connect() {
        try {
            eslClient = new Client();
            eslClient.connect(host, port, password, 10);
            logger.info("Connected to FreeSWITCH at {}:{}", host, port);
            return true;
        } catch (InboundConnectionFailure e) {
            logger.error("Failed to connect to FreeSWITCH: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        if (eslClient != null) {
            eslClient.close();
            logger.info("Disconnected from FreeSWITCH");
        }
        executor.shutdown();
    }
    
    /**
     * 判断是否已连接
     */
    public boolean isConnected() {
        return eslClient != null && eslClient.canSend();
    }
    
    /**
     * 根据音频 URL 获取播放协议
     * 
     * @param audioUrl 原始音频 URL
     * @return 带协议前缀的播放 URL
     */
    private String getPlaybackUrl(String audioUrl) {
        String lowerUrl = audioUrl.toLowerCase();
        
        // MP3 格式使用 shout 协议
        if (lowerUrl.endsWith(".mp3") || lowerUrl.contains(".mp3?")) {
            return "shout://" + audioUrl;
        }
        
        // 其他格式使用 http_cache
        if (audioUrl.startsWith("http://") || audioUrl.startsWith("https://")) {
            return "http_cache://" + audioUrl;
        }
        
        // 默认添加 http:// 前缀
        return "http_cache://http://" + audioUrl;
    }
    
    /**
     * 在指定通道播放 HTTP 音频
     * 
     * @param uuid 通道 UUID
     * @param audioUrl HTTP 音频 URL
     * @param leg 播放方向：aleg, bleg, both
     * @return 播放结果
     */
    public PlaybackResult playAudio(String uuid, String audioUrl, String leg) {
        if (!isConnected()) {
            return new PlaybackResult(false, "Not connected to FreeSWITCH", null);
        }
        
        String playbackUrl = getPlaybackUrl(audioUrl);
        String command = String.format("uuid_broadcast %s %s %s", uuid, playbackUrl, leg);
        
        logger.info("Executing command: {}", command);
        
        EslMessage response = eslClient.sendSyncApiCommand(command, "");
        String body = response != null ? String.join("\n", response.getBodyLines()) : "";
        
        boolean success = !body.contains("-ERR");
        
        if (success) {
            logger.info("Playback started successfully for UUID: {}", uuid);
        } else {
            logger.warn("Playback failed for UUID: {}, response: {}", uuid, body);
        }
        
        return new PlaybackResult(success, body, playbackUrl);
    }
    
    /**
     * 在指定通道播放 HTTP 音频（默认双向播放）
     */
    public PlaybackResult playAudio(String uuid, String audioUrl) {
        return playAudio(uuid, audioUrl, "both");
    }
    
    /**
     * 停止当前播放
     * 
     * @param uuid 通道 UUID
     * @return 执行结果
     */
    public PlaybackResult stopPlayback(String uuid) {
        if (!isConnected()) {
            return new PlaybackResult(false, "Not connected to FreeSWITCH", null);
        }
        
        String command = String.format("uuid_break %s all", uuid);
        
        logger.info("Stopping playback for UUID: {}", uuid);
        
        EslMessage response = eslClient.sendSyncApiCommand(command, "");
        String body = response != null ? String.join("\n", response.getBodyLines()) : "";
        
        boolean success = !body.contains("-ERR");
        
        return new PlaybackResult(success, body, null);
    }
    
    /**
     * 叠加播放音频（混音，不中断当前音频）
     * 
     * @param uuid 通道 UUID
     * @param audioUrl HTTP 音频 URL
     * @param volume 音量调整 (-4 到 4)
     * @return 执行结果
     */
    public PlaybackResult overlayAudio(String uuid, String audioUrl, int volume) {
        if (!isConnected()) {
            return new PlaybackResult(false, "Not connected to FreeSWITCH", null);
        }
        
        String playbackUrl = getPlaybackUrl(audioUrl);
        String command = String.format("uuid_displace %s start %s %d mux", uuid, playbackUrl, volume);
        
        logger.info("Overlay audio for UUID: {}", uuid);
        
        EslMessage response = eslClient.sendSyncApiCommand(command, "");
        String body = response != null ? String.join("\n", response.getBodyLines()) : "";
        
        boolean success = !body.contains("-ERR");
        
        return new PlaybackResult(success, body, playbackUrl);
    }
    
    /**
     * 停止叠加音频
     * 
     * @param uuid 通道 UUID
     * @param audioUrl 要停止的音频 URL
     * @return 执行结果
     */
    public PlaybackResult stopOverlay(String uuid, String audioUrl) {
        if (!isConnected()) {
            return new PlaybackResult(false, "Not connected to FreeSWITCH", null);
        }
        
        String playbackUrl = getPlaybackUrl(audioUrl);
        String command = String.format("uuid_displace %s stop %s", uuid, playbackUrl);
        
        EslMessage response = eslClient.sendSyncApiCommand(command, "");
        String body = response != null ? String.join("\n", response.getBodyLines()) : "";
        
        boolean success = !body.contains("-ERR");
        
        return new PlaybackResult(success, body, null);
    }
    
    /**
     * 异步播放音频
     * 
     * @param uuid 通道 UUID
     * @param audioUrl HTTP 音频 URL
     * @return Future 对象
     */
    public Future<PlaybackResult> playAudioAsync(String uuid, String audioUrl) {
        return executor.submit(() -> playAudio(uuid, audioUrl));
    }
    
    /**
     * 播放音频列表
     * 
     * @param uuid 通道 UUID
     * @param audioUrls 音频 URL 列表
     * @param intervalMs 音频之间的间隔（毫秒）
     */
    public void playPlaylist(String uuid, String[] audioUrls, long intervalMs) {
        executor.submit(() -> {
            for (String audioUrl : audioUrls) {
                if (!isConnected()) {
                    logger.warn("Connection lost, stopping playlist");
                    break;
                }
                
                PlaybackResult result = playAudio(uuid, audioUrl);
                if (!result.isSuccess()) {
                    logger.warn("Failed to play: {}", audioUrl);
                }
                
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }
    
    /**
     * 设置通道变量
     * 
     * @param uuid 通道 UUID
     * @param name 变量名
     * @param value 变量值
     */
    public boolean setVariable(String uuid, String name, String value) {
        if (!isConnected()) {
            return false;
        }
        
        String command = String.format("uuid_setvar %s %s %s", uuid, name, value);
        EslMessage response = eslClient.sendSyncApiCommand(command, "");
        String body = response != null ? String.join("\n", response.getBodyLines()) : "";
        
        return !body.contains("-ERR");
    }
    
    /**
     * 获取通道变量
     * 
     * @param uuid 通道 UUID
     * @param name 变量名
     * @return 变量值
     */
    public String getVariable(String uuid, String name) {
        if (!isConnected()) {
            return null;
        }
        
        String command = String.format("uuid_getvar %s %s", uuid, name);
        EslMessage response = eslClient.sendSyncApiCommand(command, "");
        
        if (response != null && !response.getBodyLines().isEmpty()) {
            String body = response.getBodyLines().get(0);
            if (!body.contains("-ERR")) {
                return body.trim();
            }
        }
        
        return null;
    }
    
    /**
     * 发起呼叫
     * 
     * @param destination 目标号码
     * @param callerIdNumber 主叫号码
     * @param callerIdName 主叫名称
     * @return 通道 UUID
     */
    public String originate(String destination, String callerIdNumber, String callerIdName) {
        if (!isConnected()) {
            return null;
        }
        
        String command = String.format(
            "originate {origination_caller_id_number=%s,origination_caller_id_name=%s}sofia/gateway/default/%s &park",
            callerIdNumber,
            callerIdName,
            destination
        );
        
        EslMessage response = eslClient.sendSyncApiCommand(command, "");
        String body = response != null ? String.join("\n", response.getBodyLines()) : "";
        
        if (!body.contains("-ERR") && body.startsWith("+OK")) {
            // 返回 UUID
            return body.replace("+OK", "").trim();
        }
        
        logger.error("Originate failed: {}", body);
        return null;
    }
    
    /**
     * 挂断通道
     * 
     * @param uuid 通道 UUID
     * @param cause 挂断原因
     */
    public boolean hangup(String uuid, String cause) {
        if (!isConnected()) {
            return false;
        }
        
        String command = String.format("uuid_kill %s %s", uuid, cause);
        EslMessage response = eslClient.sendSyncApiCommand(command, "");
        String body = response != null ? String.join("\n", response.getBodyLines()) : "";
        
        return !body.contains("-ERR");
    }
    
    /**
     * 播放结果类
     */
    public static class PlaybackResult {
        private final boolean success;
        private final String response;
        private final String playbackUrl;
        
        public PlaybackResult(boolean success, String response, String playbackUrl) {
            this.success = success;
            this.response = response;
            this.playbackUrl = playbackUrl;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getResponse() {
            return response;
        }
        
        public String getPlaybackUrl() {
            return playbackUrl;
        }
        
        @Override
        public String toString() {
            return "PlaybackResult{" +
                    "success=" + success +
                    ", response='" + response + '\'' +
                    ", playbackUrl='" + playbackUrl + '\'' +
                    '}';
        }
    }
    
    /**
     * 使用示例
     */
    public static void main(String[] args) {
        HttpAudioPlayer player = new HttpAudioPlayer("127.0.0.1", 8021, "ClueCon");
        
        try {
            // 连接到 FreeSWITCH
            if (!player.connect()) {
                System.err.println("Failed to connect to FreeSWITCH");
                return;
            }
            
            // 示例：播放 HTTP 音频
            String uuid = "your-channel-uuid-here";
            String audioUrl = "http://audio.example.com/welcome.mp3";
            
            PlaybackResult result = player.playAudio(uuid, audioUrl);
            System.out.println("Playback result: " + result);
            
            // 示例：播放音频列表
            String[] playlist = {
                "http://audio.example.com/audio1.mp3",
                "http://audio.example.com/audio2.mp3",
                "http://audio.example.com/audio3.mp3"
            };
            player.playPlaylist(uuid, playlist, 1000);
            
            // 等待一段时间
            Thread.sleep(5000);
            
            // 停止播放
            player.stopPlayback(uuid);
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            player.disconnect();
        }
    }
}
