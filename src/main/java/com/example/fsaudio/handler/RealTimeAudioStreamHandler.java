package com.example.fsaudio.handler;

import com.example.fsaudio.config.AudioStreamConfig;
import com.example.fsaudio.model.AudioFrame;
import com.example.fsaudio.websocket.AudioWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 实时音频流处理器
 * 
 * 提供多种获取实时音频流的方式:
 * 1. TCP Socket服务器 - 接收FreeSwtich通过socket发送的音频流
 * 2. UDP接收器 - 接收RTP音频流
 * 3. 管道读取 - 从FreeSwtich的录音管道读取
 */
@Slf4j
@Component
public class RealTimeAudioStreamHandler {

    private final AudioStreamConfig config;
    private final AudioWebSocketHandler webSocketHandler;

    private ServerSocket tcpServer;
    private ExecutorService socketExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 活跃的音频流连接
     */
    private final ConcurrentHashMap<String, AudioStreamConnection> activeConnections = new ConcurrentHashMap<>();

    @Autowired
    public RealTimeAudioStreamHandler(AudioStreamConfig config, AudioWebSocketHandler webSocketHandler) {
        this.config = config;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * 启动TCP Socket服务器
     * 
     * FreeSwtich可以通过以下方式发送音频流到此服务器:
     * - mod_audio_stream模块
     * - uuid_audio_stream命令
     * - 自定义Lua脚本
     * 
     * @param port 监听端口
     */
    public void startTcpServer(int port) {
        if (running.get()) {
            log.warn("TCP服务器已在运行");
            return;
        }

        socketExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "audio-tcp-handler");
            t.setDaemon(true);
            return t;
        });

        running.set(true);

        socketExecutor.submit(() -> {
            try {
                tcpServer = new ServerSocket(port);
                log.info("TCP音频流服务器启动, 监听端口: {}", port);

                while (running.get()) {
                    try {
                        Socket clientSocket = tcpServer.accept();
                        log.info("新的音频流连接: {}", clientSocket.getRemoteSocketAddress());
                        
                        // 为每个连接创建处理任务
                        socketExecutor.submit(() -> handleTcpConnection(clientSocket));
                    } catch (Exception e) {
                        if (running.get()) {
                            log.error("接受连接异常: {}", e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("TCP服务器异常: {}", e.getMessage());
            }
        });
    }

    /**
     * 处理TCP连接
     */
    private void handleTcpConnection(Socket socket) {
        String connectionId = socket.getRemoteSocketAddress().toString();
        AtomicLong frameSequence = new AtomicLong(0);

        try {
            socket.setSoTimeout(30000);
            DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            // 读取初始握手消息 (获取UUID等信息)
            String uuid = readHandshake(input);
            if (uuid == null) {
                uuid = "unknown-" + System.currentTimeMillis();
            }

            log.info("音频流连接建立: uuid={}, connection={}", uuid, connectionId);

            // 创建连接对象
            AudioStreamConnection connection = new AudioStreamConnection(uuid, socket);
            activeConnections.put(uuid, connection);

            // 持续读取音频数据
            byte[] buffer = new byte[config.getBufferSize()];
            
            while (running.get() && !socket.isClosed()) {
                try {
                    // 读取帧头 (如果有协议定义)
                    int bytesRead = input.read(buffer);
                    
                    if (bytesRead == -1) {
                        log.info("音频流连接关闭: uuid={}", uuid);
                        break;
                    }

                    if (bytesRead > 0) {
                        // 复制实际读取的数据
                        byte[] audioData = new byte[bytesRead];
                        System.arraycopy(buffer, 0, audioData, 0, bytesRead);

                        // 构建音频帧
                        AudioFrame frame = AudioFrame.builder()
                                .uuid(uuid)
                                .data(audioData)
                                .timestamp(System.currentTimeMillis())
                                .sampleRate(config.getSampleRate())
                                .channels(config.getChannels())
                                .direction("in")
                                .sequenceNumber(frameSequence.incrementAndGet())
                                .build();

                        // 通过WebSocket推送
                        if (config.isWebsocketEnabled()) {
                            webSocketHandler.sendAudioFrame(uuid, frame);
                        }

                        // 触发回调处理
                        processAudioFrame(frame);
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        log.debug("读取音频数据异常: {}", e.getMessage());
                    }
                    break;
                }
            }
        } catch (Exception e) {
            log.error("处理TCP连接异常: {}", e.getMessage());
        } finally {
            closeConnection(connectionId, socket);
        }
    }

    /**
     * 读取握手消息
     */
    private String readHandshake(DataInputStream input) {
        try {
            // 读取UUID长度
            int uuidLength = input.readInt();
            if (uuidLength > 0 && uuidLength < 100) {
                byte[] uuidBytes = new byte[uuidLength];
                input.readFully(uuidBytes);
                return new String(uuidBytes);
            }
        } catch (Exception e) {
            log.debug("读取握手消息失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 处理音频帧 (可扩展的回调方法)
     */
    protected void processAudioFrame(AudioFrame frame) {
        // 子类可以覆盖此方法添加自定义处理逻辑
        // 例如: VAD检测, ASR识别, 音频存储等
        log.trace("处理音频帧: uuid={}, seq={}, size={}", 
                frame.getUuid(), frame.getSequenceNumber(), frame.getData().length);
    }

    /**
     * 关闭连接
     */
    private void closeConnection(String connectionId, Socket socket) {
        activeConnections.values().removeIf(conn -> conn.socket == socket);
        
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            log.debug("关闭连接异常: {}", e.getMessage());
        }
        
        log.info("音频流连接已关闭: {}", connectionId);
    }

    /**
     * 停止TCP服务器
     */
    public void stopTcpServer() {
        running.set(false);

        try {
            if (tcpServer != null && !tcpServer.isClosed()) {
                tcpServer.close();
            }
        } catch (IOException e) {
            log.error("关闭TCP服务器异常: {}", e.getMessage());
        }

        if (socketExecutor != null) {
            socketExecutor.shutdownNow();
        }

        // 关闭所有活跃连接
        activeConnections.values().forEach(conn -> {
            try {
                conn.socket.close();
            } catch (IOException e) {
                log.debug("关闭连接异常: {}", e.getMessage());
            }
        });
        activeConnections.clear();

        log.info("TCP音频流服务器已停止");
    }

    @PreDestroy
    public void destroy() {
        stopTcpServer();
    }

    /**
     * 从命名管道读取音频流
     * 
     * FreeSwtich可以通过以下命令将音频输出到管道:
     * uuid_record <uuid> start /tmp/audio_pipe
     * 
     * @param pipePath 管道路径
     * @param uuid 通话UUID
     */
    public void readFromPipe(String pipePath, String uuid) {
        socketExecutor.submit(() -> {
            AtomicLong frameSequence = new AtomicLong(0);
            
            try (FileInputStream fis = new FileInputStream(pipePath);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {
                
                log.info("开始从管道读取音频: uuid={}, pipe={}", uuid, pipePath);
                
                byte[] buffer = new byte[config.getBufferSize()];
                
                while (running.get()) {
                    int bytesRead = bis.read(buffer);
                    
                    if (bytesRead == -1) {
                        Thread.sleep(10);
                        continue;
                    }

                    if (bytesRead > 0) {
                        byte[] audioData = new byte[bytesRead];
                        System.arraycopy(buffer, 0, audioData, 0, bytesRead);

                        AudioFrame frame = AudioFrame.builder()
                                .uuid(uuid)
                                .data(audioData)
                                .timestamp(System.currentTimeMillis())
                                .sampleRate(config.getSampleRate())
                                .channels(config.getChannels())
                                .direction("in")
                                .sequenceNumber(frameSequence.incrementAndGet())
                                .build();

                        if (config.isWebsocketEnabled()) {
                            webSocketHandler.sendAudioFrame(uuid, frame);
                        }

                        processAudioFrame(frame);
                    }
                }
            } catch (Exception e) {
                log.error("读取管道异常: {}", e.getMessage());
            }
            
            log.info("管道读取结束: uuid={}", uuid);
        });
    }

    /**
     * 发送音频数据到指定通话 (用于TTS等场景)
     * 
     * @param uuid 通话UUID
     * @param audioData 音频数据
     */
    public boolean sendAudioToCall(String uuid, byte[] audioData) {
        AudioStreamConnection connection = activeConnections.get(uuid);
        if (connection == null) {
            log.warn("未找到音频连接: {}", uuid);
            return false;
        }

        try {
            DataOutputStream output = new DataOutputStream(connection.socket.getOutputStream());
            output.writeInt(audioData.length);
            output.write(audioData);
            output.flush();
            return true;
        } catch (IOException e) {
            log.error("发送音频失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取活跃连接数
     */
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }

    /**
     * 检查连接是否存在
     */
    public boolean hasConnection(String uuid) {
        return activeConnections.containsKey(uuid);
    }

    /**
     * 音频流连接内部类
     */
    private static class AudioStreamConnection {
        final String uuid;
        final Socket socket;

        AudioStreamConnection(String uuid, Socket socket) {
            this.uuid = uuid;
            this.socket = socket;
        }
    }

    /**
     * 将PCM转换为WAV格式
     * 
     * @param pcmData PCM音频数据
     * @param sampleRate 采样率
     * @param channels 通道数
     * @param bitsPerSample 每样本位数
     * @return WAV格式数据
     */
    public static byte[] pcmToWav(byte[] pcmData, int sampleRate, int channels, int bitsPerSample) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        
        ByteBuffer buffer = ByteBuffer.allocate(44 + pcmData.length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // RIFF header
        buffer.put("RIFF".getBytes());
        buffer.putInt(36 + pcmData.length);
        buffer.put("WAVE".getBytes());
        
        // fmt chunk
        buffer.put("fmt ".getBytes());
        buffer.putInt(16);  // chunk size
        buffer.putShort((short) 1);  // audio format (1 = PCM)
        buffer.putShort((short) channels);
        buffer.putInt(sampleRate);
        buffer.putInt(byteRate);
        buffer.putShort((short) blockAlign);
        buffer.putShort((short) bitsPerSample);
        
        // data chunk
        buffer.put("data".getBytes());
        buffer.putInt(pcmData.length);
        buffer.put(pcmData);
        
        return buffer.array();
    }
}
