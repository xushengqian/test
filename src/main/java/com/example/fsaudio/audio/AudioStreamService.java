package com.example.fsaudio.audio;

import com.example.fsaudio.config.AudioStreamConfig;
import com.example.fsaudio.esl.EslClient;
import com.example.fsaudio.model.AudioFrame;
import com.example.fsaudio.websocket.AudioWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 音频流服务
 * 负责处理实时音频流的接收、处理和分发
 */
@Slf4j
@Service
public class AudioStreamService {

    @Autowired
    private AudioStreamConfig audioConfig;
    
    @Autowired
    private EslClient eslClient;
    
    @Autowired
    private AudioWebSocketHandler webSocketHandler;
    
    @Value("${server.port:8080}")
    private int serverPort;
    
    /**
     * 音频流接收服务器端口
     */
    private static final int AUDIO_SERVER_PORT = 9090;
    
    /**
     * 活跃的音频流会话
     */
    private final Map<String, AudioStreamSession> activeSessions = new ConcurrentHashMap<>();
    
    /**
     * 音频数据监听器
     */
    private final Map<String, Consumer<AudioFrame>> audioListeners = new ConcurrentHashMap<>();
    
    /**
     * TCP音频服务器
     */
    private ServerSocket audioServer;
    private ExecutorService audioServerExecutor;
    private volatile boolean serverRunning = false;
    
    /**
     * 启动音频接收服务器
     * 用于接收FreeSWitch通过socket方式推送的音频流
     */
    public void startAudioServer() {
        if (serverRunning) {
            log.warn("音频服务器已在运行");
            return;
        }
        
        audioServerExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "audio-server-thread");
            t.setDaemon(true);
            return t;
        });
        
        audioServerExecutor.submit(() -> {
            try {
                audioServer = new ServerSocket(AUDIO_SERVER_PORT);
                serverRunning = true;
                log.info("音频接收服务器启动，端口: {}", AUDIO_SERVER_PORT);
                
                while (serverRunning) {
                    try {
                        Socket clientSocket = audioServer.accept();
                        log.info("新的音频连接: {}", clientSocket.getRemoteSocketAddress());
                        
                        // 处理音频连接
                        audioServerExecutor.submit(() -> handleAudioConnection(clientSocket));
                        
                    } catch (Exception e) {
                        if (serverRunning) {
                            log.error("接受音频连接失败", e);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("启动音频服务器失败", e);
            }
        });
    }
    
    /**
     * 处理音频连接
     */
    private void handleAudioConnection(Socket socket) {
        String connectionId = socket.getRemoteSocketAddress().toString();
        
        try (InputStream inputStream = socket.getInputStream()) {
            byte[] buffer = new byte[audioConfig.getBufferSize()];
            int bytesRead;
            
            // 从初始数据中提取UUID(如果有的话)
            String uuid = extractUuidFromConnection(inputStream);
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (bytesRead > 0) {
                    byte[] audioData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, audioData, 0, bytesRead);
                    
                    // 处理音频数据
                    processAudioData(uuid != null ? uuid : connectionId, audioData);
                }
            }
        } catch (Exception e) {
            log.error("处理音频连接失败: {}", connectionId, e);
        } finally {
            try {
                socket.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }
    
    /**
     * 从连接中提取UUID
     */
    private String extractUuidFromConnection(InputStream inputStream) {
        // 实际实现中，可能需要从协议头中解析UUID
        // 这里简化处理
        return null;
    }
    
    /**
     * 停止音频服务器
     */
    public void stopAudioServer() {
        serverRunning = false;
        
        try {
            if (audioServer != null && !audioServer.isClosed()) {
                audioServer.close();
            }
        } catch (Exception e) {
            log.error("关闭音频服务器失败", e);
        }
        
        if (audioServerExecutor != null && !audioServerExecutor.isShutdown()) {
            audioServerExecutor.shutdownNow();
        }
        
        log.info("音频服务器已停止");
    }
    
    /**
     * 为指定通话启动音频流
     * 
     * @param callUuid 通话UUID
     * @param direction 方向: read/write/both
     * @return 是否成功
     */
    public boolean startAudioStreamForCall(String callUuid, String direction) {
        if (activeSessions.containsKey(callUuid)) {
            log.warn("该通话已启用音频流: {}", callUuid);
            return true;
        }
        
        try {
            // 构建音频接收URL
            // 可以使用WebSocket或TCP方式
            String audioServerUrl = String.format("ws://127.0.0.1:%d/audio-stream/%s", serverPort, callUuid);
            
            // 通过ESL命令启动音频流
            boolean success = eslClient.startAudioStream(callUuid, audioServerUrl, direction);
            
            if (success) {
                AudioStreamSession session = new AudioStreamSession(callUuid, direction);
                activeSessions.put(callUuid, session);
                log.info("音频流已启动: {}", callUuid);
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("启动音频流失败: {}", callUuid, e);
            return false;
        }
    }
    
    /**
     * 使用mod_shout方式获取实时音频流
     * 
     * @param callUuid 通话UUID
     * @param httpUrl HTTP服务器地址
     * @return 是否成功
     */
    public boolean startAudioStreamWithShout(String callUuid, String httpUrl) {
        try {
            // 使用record命令配合shout://协议
            String command = String.format("uuid_record %s start shout://%s/%s.mp3", 
                    callUuid, httpUrl, callUuid);
            String result = eslClient.executeApi(command);
            
            log.info("启动shout音频流结果: {}", result);
            return result != null && result.contains("+OK");
            
        } catch (Exception e) {
            log.error("启动shout音频流失败", e);
            return false;
        }
    }
    
    /**
     * 使用mod_audio_stream方式获取实时音频流
     * 需要安装mod_audio_stream模块
     * 
     * @param callUuid 通话UUID
     * @param wsUrl WebSocket服务器地址
     * @return 是否成功
     */
    public boolean startAudioStreamWithModule(String callUuid, String wsUrl) {
        try {
            // 使用audio_stream应用
            String command = String.format("uuid_audio_stream %s start %s mixed", callUuid, wsUrl);
            String result = eslClient.executeApi(command);
            
            if (result != null && result.contains("+OK")) {
                AudioStreamSession session = new AudioStreamSession(callUuid, "both");
                activeSessions.put(callUuid, session);
                log.info("mod_audio_stream音频流已启动: {}", callUuid);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("启动mod_audio_stream失败", e);
            return false;
        }
    }
    
    /**
     * 使用媒体bug方式捕获音频
     * 
     * @param callUuid 通话UUID
     * @param appPath 应用路径
     * @return 是否成功
     */
    public boolean startMediaBugCapture(String callUuid, String appPath) {
        try {
            // 使用uuid_buglist和uuid_debug_media命令
            String command = String.format("uuid_debug_media %s both on", callUuid);
            String result = eslClient.executeApi(command);
            
            log.info("媒体调试模式结果: {}", result);
            return result != null && result.contains("+OK");
            
        } catch (Exception e) {
            log.error("启动媒体捕获失败", e);
            return false;
        }
    }
    
    /**
     * 停止音频流
     * 
     * @param callUuid 通话UUID
     */
    public void stopAudioStream(String callUuid) {
        try {
            eslClient.stopAudioStream(callUuid);
            
            AudioStreamSession session = activeSessions.remove(callUuid);
            if (session != null) {
                session.close();
            }
            
            log.info("音频流已停止: {}", callUuid);
            
        } catch (Exception e) {
            log.error("停止音频流失败: {}", callUuid, e);
        }
    }
    
    /**
     * 处理接收到的音频数据
     * 
     * @param callUuid 通话UUID
     * @param audioData 音频数据
     */
    public void processAudioData(String callUuid, byte[] audioData) {
        AudioStreamSession session = activeSessions.get(callUuid);
        
        // 创建音频帧
        AudioFrame frame = AudioFrame.builder()
                .callUuid(callUuid)
                .audioData(audioData)
                .timestamp(System.currentTimeMillis())
                .sampleRate(audioConfig.getSampleRate())
                .channels(audioConfig.getChannels())
                .sequenceNumber(session != null ? session.getNextSequenceNumber() : 0)
                .build();
        
        // 通过WebSocket推送
        webSocketHandler.sendAudioFrame(callUuid, frame);
        
        // 通知监听器
        Consumer<AudioFrame> listener = audioListeners.get(callUuid);
        if (listener != null) {
            listener.accept(frame);
        }
        
        // 全局监听器
        Consumer<AudioFrame> globalListener = audioListeners.get("*");
        if (globalListener != null) {
            globalListener.accept(frame);
        }
        
        log.trace("处理音频数据 - UUID: {}, 大小: {} bytes", callUuid, audioData.length);
    }
    
    /**
     * 添加音频监听器
     * 
     * @param callUuid 通话UUID，使用"*"表示全局监听
     * @param listener 监听器
     */
    public void addAudioListener(String callUuid, Consumer<AudioFrame> listener) {
        audioListeners.put(callUuid, listener);
    }
    
    /**
     * 移除音频监听器
     * 
     * @param callUuid 通话UUID
     */
    public void removeAudioListener(String callUuid) {
        audioListeners.remove(callUuid);
    }
    
    /**
     * 获取活跃的音频会话
     */
    public Map<String, AudioStreamSession> getActiveSessions() {
        return new ConcurrentHashMap<>(activeSessions);
    }
    
    /**
     * 音频流会话
     */
    public static class AudioStreamSession {
        private final String callUuid;
        private final String direction;
        private final long startTime;
        private final AtomicLong sequenceNumber = new AtomicLong(0);
        private volatile boolean active = true;
        
        public AudioStreamSession(String callUuid, String direction) {
            this.callUuid = callUuid;
            this.direction = direction;
            this.startTime = System.currentTimeMillis();
        }
        
        public String getCallUuid() {
            return callUuid;
        }
        
        public String getDirection() {
            return direction;
        }
        
        public long getStartTime() {
            return startTime;
        }
        
        public long getNextSequenceNumber() {
            return sequenceNumber.incrementAndGet();
        }
        
        public boolean isActive() {
            return active;
        }
        
        public void close() {
            active = false;
        }
    }
}
