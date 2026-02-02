package com.example.fsaudio.esl;

import com.example.fsaudio.config.FreeSwitchConfig;
import com.example.fsaudio.handler.EslEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FreeSwtich ESL连接管理器
 * 
 * 负责:
 * 1. 建立和维护与FreeSwtich的ESL连接
 * 2. 发送ESL命令
 * 3. 处理ESL事件
 * 4. 自动重连机制
 */
@Slf4j
@Component
public class EslConnectionManager {

    private final FreeSwitchConfig config;
    private final EslEventHandler eventHandler;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger reconnectCount = new AtomicInteger(0);

    private ExecutorService eventReaderExecutor;
    private ScheduledExecutorService heartbeatExecutor;

    private final BlockingQueue<String> commandResponseQueue = new LinkedBlockingQueue<>();

    @Autowired
    public EslConnectionManager(FreeSwitchConfig config, EslEventHandler eventHandler) {
        this.config = config;
        this.eventHandler = eventHandler;
    }

    @PostConstruct
    public void init() {
        eventReaderExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "esl-event-reader");
            t.setDaemon(true);
            return t;
        });

        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "esl-heartbeat");
            t.setDaemon(true);
            return t;
        });

        // 异步初始化连接
        CompletableFuture.runAsync(this::connect);
    }

    @PreDestroy
    public void destroy() {
        running.set(false);
        disconnect();
        
        if (eventReaderExecutor != null) {
            eventReaderExecutor.shutdownNow();
        }
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }
    }

    /**
     * 建立ESL连接
     */
    public synchronized void connect() {
        if (connected.get()) {
            log.debug("ESL已连接,跳过连接");
            return;
        }

        try {
            log.info("正在连接FreeSwtich ESL: {}:{}", config.getHost(), config.getPort());

            socket = new Socket(config.getHost(), config.getPort());
            socket.setSoTimeout(config.getTimeout() * 1000);
            socket.setKeepAlive(true);

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // 读取欢迎消息
            Map<String, String> welcomeMsg = readMessage();
            log.debug("ESL欢迎消息: {}", welcomeMsg);

            // 认证
            if (!authenticate()) {
                log.error("ESL认证失败");
                disconnect();
                return;
            }

            connected.set(true);
            reconnectCount.set(0);
            log.info("ESL连接成功");

            // 订阅事件
            subscribeEvents();

            // 启动事件读取线程
            startEventReader();

            // 启动心跳
            startHeartbeat();

        } catch (Exception e) {
            log.error("ESL连接失败: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    /**
     * ESL认证
     */
    private boolean authenticate() throws IOException {
        sendCommand("auth " + config.getPassword());
        Map<String, String> response = readMessage();
        
        String replyText = response.get("Reply-Text");
        if (replyText != null && replyText.contains("+OK")) {
            log.info("ESL认证成功");
            return true;
        }
        log.error("ESL认证失败: {}", replyText);
        return false;
    }

    /**
     * 订阅ESL事件
     */
    private void subscribeEvents() {
        try {
            // 订阅所有事件
            sendCommand("event plain ALL");
            Map<String, String> response = readMessage();
            log.info("订阅事件响应: {}", response.get("Reply-Text"));

            // 启用myevents用于获取当前通道的所有事件
            // sendCommand("myevents");
            
        } catch (Exception e) {
            log.error("订阅事件失败: {}", e.getMessage());
        }
    }

    /**
     * 启动事件读取线程
     */
    private void startEventReader() {
        eventReaderExecutor.submit(() -> {
            while (running.get() && connected.get()) {
                try {
                    Map<String, String> event = readMessage();
                    if (event != null && !event.isEmpty()) {
                        String contentType = event.get("Content-Type");
                        if ("text/event-plain".equals(contentType)) {
                            // 读取事件体
                            String contentLengthStr = event.get("Content-Length");
                            if (contentLengthStr != null) {
                                int contentLength = Integer.parseInt(contentLengthStr);
                                String eventBody = readEventBody(contentLength);
                                Map<String, String> eventData = parseEventBody(eventBody);
                                eventHandler.handleEvent(eventData);
                            }
                        } else if ("api/response".equals(contentType)) {
                            // API响应
                            commandResponseQueue.offer(event.toString());
                        }
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        log.error("读取ESL事件异常: {}", e.getMessage());
                        handleDisconnect();
                    }
                    break;
                }
            }
        });
    }

    /**
     * 启动心跳定时任务
     */
    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (connected.get()) {
                try {
                    String response = executeApi("status");
                    if (response == null || response.isEmpty()) {
                        log.warn("心跳检测失败");
                        handleDisconnect();
                    }
                } catch (Exception e) {
                    log.warn("心跳异常: {}", e.getMessage());
                }
            }
        }, config.getHeartbeatInterval(), config.getHeartbeatInterval(), TimeUnit.SECONDS);
    }

    /**
     * 发送ESL命令
     */
    private void sendCommand(String command) {
        if (writer != null) {
            writer.println(command);
            writer.println();
            writer.flush();
            log.debug("发送ESL命令: {}", command);
        }
    }

    /**
     * 读取ESL消息
     */
    private Map<String, String> readMessage() throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;
        
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                break;
            }
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                headers.put(key, value);
            }
        }
        
        return headers;
    }

    /**
     * 读取事件体
     */
    private String readEventBody(int contentLength) throws IOException {
        char[] buffer = new char[contentLength];
        int totalRead = 0;
        while (totalRead < contentLength) {
            int read = reader.read(buffer, totalRead, contentLength - totalRead);
            if (read == -1) {
                break;
            }
            totalRead += read;
        }
        return new String(buffer, 0, totalRead);
    }

    /**
     * 解析事件体
     */
    private Map<String, String> parseEventBody(String body) {
        Map<String, String> data = new HashMap<>();
        String[] lines = body.split("\n");
        for (String line : lines) {
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                // URL解码
                try {
                    value = java.net.URLDecoder.decode(value, "UTF-8");
                } catch (Exception e) {
                    // 保持原值
                }
                data.put(key, value);
            }
        }
        return data;
    }

    /**
     * 执行API命令
     */
    public synchronized String executeApi(String command) {
        if (!connected.get()) {
            log.warn("ESL未连接，无法执行命令: {}", command);
            return null;
        }

        try {
            sendCommand("api " + command);
            Map<String, String> response = readMessage();
            
            String contentLengthStr = response.get("Content-Length");
            if (contentLengthStr != null) {
                int contentLength = Integer.parseInt(contentLengthStr);
                return readEventBody(contentLength);
            }
            
            return response.get("Reply-Text");
        } catch (Exception e) {
            log.error("执行API命令失败: {} - {}", command, e.getMessage());
            return null;
        }
    }

    /**
     * 执行bgapi命令 (后台异步执行)
     */
    public void executeBgApi(String command) {
        if (!connected.get()) {
            log.warn("ESL未连接，无法执行命令: {}", command);
            return;
        }
        sendCommand("bgapi " + command);
    }

    /**
     * 处理断开连接
     */
    private void handleDisconnect() {
        connected.set(false);
        scheduleReconnect();
    }

    /**
     * 计划重连
     */
    private void scheduleReconnect() {
        int count = reconnectCount.incrementAndGet();
        if (count > config.getMaxReconnectAttempts()) {
            log.error("已达到最大重连次数 {}, 停止重连", config.getMaxReconnectAttempts());
            return;
        }

        log.info("将在 {}秒后进行第 {} 次重连", config.getReconnectInterval(), count);
        
        CompletableFuture.delayedExecutor(config.getReconnectInterval(), TimeUnit.SECONDS)
                .execute(this::connect);
    }

    /**
     * 断开连接
     */
    public synchronized void disconnect() {
        connected.set(false);
        
        try {
            if (writer != null) {
                writer.close();
            }
            if (reader != null) {
                reader.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            log.error("关闭ESL连接异常: {}", e.getMessage());
        }
        
        log.info("ESL连接已断开");
    }

    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * 定时检查连接状态
     */
    @Scheduled(fixedDelay = 60000)
    public void checkConnection() {
        if (!connected.get() && running.get() && reconnectCount.get() <= config.getMaxReconnectAttempts()) {
            log.info("检测到ESL未连接，尝试重连...");
            connect();
        }
    }
}
