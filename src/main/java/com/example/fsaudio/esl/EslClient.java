package com.example.fsaudio.esl;

import com.example.fsaudio.config.FreeSwitchConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * FreeSWitch ESL客户端
 * 用于连接FreeSWitch并执行ESL命令
 */
@Slf4j
@Component
public class EslClient {

    @Autowired
    private FreeSwitchConfig config;

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    
    private ExecutorService eventExecutor;
    private ScheduledExecutorService heartbeatExecutor;
    
    private final Map<String, Consumer<Map<String, String>>> eventListeners = new ConcurrentHashMap<>();
    private Consumer<byte[]> audioDataListener;
    
    /**
     * 初始化连接
     */
    @PostConstruct
    public void init() {
        eventExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "esl-event-thread");
            t.setDaemon(true);
            return t;
        });
        
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "esl-heartbeat-thread");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * 连接到FreeSWitch ESL
     */
    public synchronized boolean connect() {
        if (connected.get()) {
            log.warn("已经连接到FreeSWitch ESL");
            return true;
        }
        
        try {
            log.info("正在连接FreeSWitch ESL: {}:{}", config.getHost(), config.getPort());
            
            socket = new Socket(config.getHost(), config.getPort());
            socket.setSoTimeout(config.getTimeout());
            socket.setKeepAlive(true);
            
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            
            // 读取欢迎消息
            Map<String, String> welcomeMsg = readResponse();
            if (welcomeMsg != null && "text/rude-rejection".equals(welcomeMsg.get("Content-Type"))) {
                log.error("连接被拒绝");
                disconnect();
                return false;
            }
            
            connected.set(true);
            
            // 认证
            if (authenticate()) {
                // 启动事件监听
                startEventListener();
                // 启动心跳
                startHeartbeat();
                log.info("FreeSWitch ESL连接成功");
                return true;
            } else {
                disconnect();
                return false;
            }
            
        } catch (Exception e) {
            log.error("连接FreeSWitch ESL失败", e);
            disconnect();
            return false;
        }
    }
    
    /**
     * 认证
     */
    private boolean authenticate() {
        try {
            sendCommand("auth " + config.getPassword());
            Map<String, String> response = readResponse();
            
            if (response != null && "command/reply".equals(response.get("Content-Type"))) {
                String replyText = response.get("Reply-Text");
                if (replyText != null && replyText.startsWith("+OK")) {
                    authenticated.set(true);
                    log.info("FreeSWitch ESL认证成功");
                    return true;
                }
            }
            
            log.error("FreeSWitch ESL认证失败");
            return false;
            
        } catch (Exception e) {
            log.error("认证过程发生异常", e);
            return false;
        }
    }
    
    /**
     * 发送命令
     */
    public synchronized void sendCommand(String command) throws IOException {
        if (!connected.get()) {
            throw new IOException("未连接到FreeSWitch ESL");
        }
        
        writer.write(command);
        writer.write("\n\n");
        writer.flush();
        
        log.debug("发送ESL命令: {}", command);
    }
    
    /**
     * 读取响应
     */
    private Map<String, String> readResponse() throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;
        int contentLength = 0;
        
        // 读取头部
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                headers.put(key, value);
                
                if ("Content-Length".equalsIgnoreCase(key)) {
                    contentLength = Integer.parseInt(value);
                }
            }
        }
        
        // 读取内容体
        if (contentLength > 0) {
            char[] body = new char[contentLength];
            int read = reader.read(body, 0, contentLength);
            if (read > 0) {
                headers.put("Body", new String(body, 0, read));
            }
        }
        
        return headers;
    }
    
    /**
     * 订阅事件
     */
    public void subscribeEvents(String... events) {
        try {
            String eventList = String.join(" ", events);
            sendCommand("event plain " + eventList);
            Map<String, String> response = readResponse();
            log.info("订阅事件响应: {}", response);
        } catch (Exception e) {
            log.error("订阅事件失败", e);
        }
    }
    
    /**
     * 执行API命令
     */
    public String executeApi(String command) {
        try {
            sendCommand("api " + command);
            Map<String, String> response = readResponse();
            return response.get("Body");
        } catch (Exception e) {
            log.error("执行API命令失败: {}", command, e);
            return null;
        }
    }
    
    /**
     * 执行bgapi命令(后台执行)
     */
    public String executeBgApi(String command) {
        try {
            sendCommand("bgapi " + command);
            Map<String, String> response = readResponse();
            return response.get("Job-UUID");
        } catch (Exception e) {
            log.error("执行bgapi命令失败: {}", command, e);
            return null;
        }
    }
    
    /**
     * 启动音频流
     * 使用uuid_audio_stream命令获取实时音频
     */
    public boolean startAudioStream(String uuid, String serverUrl, String direction) {
        try {
            // uuid_audio_stream命令格式:
            // uuid_audio_stream <uuid> start <url> [<flags>]
            // flags: -r (read/接收方向) -w (write/发送方向) -b (both/双向)
            String flags = "-b"; // 默认双向
            if ("read".equalsIgnoreCase(direction)) {
                flags = "-r";
            } else if ("write".equalsIgnoreCase(direction)) {
                flags = "-w";
            }
            
            String command = String.format("uuid_audio_stream %s start %s %s", uuid, serverUrl, flags);
            String result = executeApi(command);
            
            log.info("启动音频流结果: {}", result);
            return result != null && result.contains("+OK");
            
        } catch (Exception e) {
            log.error("启动音频流失败", e);
            return false;
        }
    }
    
    /**
     * 停止音频流
     */
    public boolean stopAudioStream(String uuid) {
        try {
            String command = String.format("uuid_audio_stream %s stop", uuid);
            String result = executeApi(command);
            
            log.info("停止音频流结果: {}", result);
            return result != null && result.contains("+OK");
            
        } catch (Exception e) {
            log.error("停止音频流失败", e);
            return false;
        }
    }
    
    /**
     * 使用record_session开始录音并获取音频
     */
    public boolean startRecordSession(String uuid, String filePath) {
        try {
            String command = String.format("uuid_record %s start %s", uuid, filePath);
            String result = executeApi(command);
            
            log.info("开始录音结果: {}", result);
            return result != null && result.contains("+OK");
            
        } catch (Exception e) {
            log.error("开始录音失败", e);
            return false;
        }
    }
    
    /**
     * 停止录音
     */
    public boolean stopRecordSession(String uuid) {
        try {
            String command = String.format("uuid_record %s stop all", uuid);
            String result = executeApi(command);
            
            log.info("停止录音结果: {}", result);
            return result != null && result.contains("+OK");
            
        } catch (Exception e) {
            log.error("停止录音失败", e);
            return false;
        }
    }
    
    /**
     * 添加事件监听器
     */
    public void addEventListener(String eventName, Consumer<Map<String, String>> listener) {
        eventListeners.put(eventName, listener);
    }
    
    /**
     * 设置音频数据监听器
     */
    public void setAudioDataListener(Consumer<byte[]> listener) {
        this.audioDataListener = listener;
    }
    
    /**
     * 启动事件监听线程
     */
    private void startEventListener() {
        eventExecutor.submit(() -> {
            while (connected.get()) {
                try {
                    Map<String, String> event = readResponse();
                    if (event != null && !event.isEmpty()) {
                        String eventName = event.get("Event-Name");
                        if (eventName != null) {
                            log.debug("收到事件: {}", eventName);
                            
                            Consumer<Map<String, String>> listener = eventListeners.get(eventName);
                            if (listener != null) {
                                listener.accept(event);
                            }
                            
                            // 全局事件监听
                            Consumer<Map<String, String>> allListener = eventListeners.get("*");
                            if (allListener != null) {
                                allListener.accept(event);
                            }
                        }
                    }
                } catch (Exception e) {
                    if (connected.get()) {
                        log.error("事件监听异常", e);
                    }
                }
            }
        });
    }
    
    /**
     * 启动心跳
     */
    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                if (connected.get() && authenticated.get()) {
                    String result = executeApi("status");
                    if (result == null) {
                        log.warn("心跳检测失败，尝试重连");
                        reconnect();
                    }
                }
            } catch (Exception e) {
                log.error("心跳异常", e);
            }
        }, config.getHeartbeatInterval(), config.getHeartbeatInterval(), TimeUnit.SECONDS);
    }
    
    /**
     * 重新连接
     */
    public void reconnect() {
        disconnect();
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        connect();
    }
    
    /**
     * 断开连接
     */
    @PreDestroy
    public synchronized void disconnect() {
        connected.set(false);
        authenticated.set(false);
        
        try {
            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            log.error("断开连接异常", e);
        }
        
        if (eventExecutor != null && !eventExecutor.isShutdown()) {
            eventExecutor.shutdownNow();
        }
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdownNow();
        }
        
        log.info("已断开FreeSWitch ESL连接");
    }
    
    /**
     * 是否已连接
     */
    public boolean isConnected() {
        return connected.get() && authenticated.get();
    }
}
