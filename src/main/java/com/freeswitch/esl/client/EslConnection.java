package com.freeswitch.esl.client;

import com.freeswitch.esl.config.FreeSwitchConfig;
import com.freeswitch.esl.event.EslEvent;
import com.freeswitch.esl.event.EslEventHandler;
import com.freeswitch.esl.exception.EslAuthenticationException;
import com.freeswitch.esl.exception.EslCommandException;
import com.freeswitch.esl.exception.EslConnectionException;
import com.freeswitch.esl.util.EslMessageParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FreeSWITCH ESL 连接类
 * 
 * 管理与 FreeSWITCH Event Socket 的 TCP 连接
 */
public class EslConnection implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EslConnection.class);

    private final FreeSwitchConfig config;
    private final EslEventHandler eventHandler;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);

    private ExecutorService eventReaderExecutor;
    private ScheduledExecutorService heartbeatExecutor;

    private final BlockingQueue<EslMessage> commandResponseQueue = new LinkedBlockingQueue<>();

    /**
     * 连接状态监听器
     */
    private ConnectionStateListener connectionStateListener;

    public EslConnection(FreeSwitchConfig config, EslEventHandler eventHandler) {
        this.config = config;
        this.eventHandler = eventHandler;
    }

    /**
     * 建立连接
     *
     * @throws EslConnectionException 连接失败
     */
    public synchronized void connect() throws EslConnectionException {
        if (connected.get()) {
            log.warn("Already connected to FreeSWITCH");
            return;
        }

        try {
            log.info("Connecting to FreeSWITCH at {}:{}", config.getHost(), config.getPort());

            socket = new Socket();
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.setSoTimeout(config.getReadTimeout());

            socket.connect(
                    new InetSocketAddress(config.getHost(), config.getPort()),
                    config.getConnectTimeout()
            );

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            connected.set(true);
            reconnectAttempts.set(0);

            // 读取认证请求
            EslMessage authRequest = readMessage();
            if (authRequest == null || !"auth/request".equals(authRequest.getContentType())) {
                throw new EslConnectionException("Unexpected response from FreeSWITCH");
            }

            // 发送认证
            authenticate();

            // 启动事件读取线程
            startEventReader();

            // 启动心跳
            startHeartbeat();

            // 订阅事件
            subscribeEvents();

            log.info("Successfully connected and authenticated to FreeSWITCH");

            if (connectionStateListener != null) {
                connectionStateListener.onConnected();
            }

        } catch (IOException e) {
            connected.set(false);
            throw new EslConnectionException("Failed to connect to FreeSWITCH: " + e.getMessage(), e);
        }
    }

    /**
     * 认证
     */
    private void authenticate() throws EslAuthenticationException {
        try {
            sendCommand("auth " + config.getPassword());
            EslMessage response = readMessage();

            if (response == null || !response.isOk()) {
                String errorMsg = response != null ? response.getReplyText() : "No response";
                throw new EslAuthenticationException("Authentication failed: " + errorMsg);
            }

            authenticated.set(true);
            log.info("ESL authentication successful");

        } catch (IOException e) {
            throw new EslAuthenticationException("Authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * 订阅事件
     */
    private void subscribeEvents() {
        try {
            String eventFormat = config.getEventFormat();
            String[] events = config.getSubscribeEvents();

            StringBuilder cmd = new StringBuilder("event ");
            cmd.append(eventFormat).append(" ");
            cmd.append(String.join(" ", events));

            EslMessage response = sendSyncCommand(cmd.toString());
            if (response != null && response.isOk()) {
                log.info("Subscribed to events: {}", String.join(", ", events));
            }
        } catch (Exception e) {
            log.error("Failed to subscribe events: {}", e.getMessage());
        }
    }

    /**
     * 启动事件读取线程
     */
    private void startEventReader() {
        eventReaderExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "esl-event-reader");
            t.setDaemon(true);
            return t;
        });

        eventReaderExecutor.submit(() -> {
            while (connected.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    EslMessage message = readMessage();
                    if (message != null) {
                        processMessage(message);
                    }
                } catch (SocketTimeoutException e) {
                    // 读取超时，继续循环
                } catch (IOException e) {
                    if (connected.get()) {
                        log.error("Error reading from FreeSWITCH: {}", e.getMessage());
                        handleDisconnect();
                    }
                    break;
                }
            }
        });
    }

    /**
     * 启动心跳
     */
    private void startHeartbeat() {
        if (config.getHeartbeatInterval() <= 0) {
            return;
        }

        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "esl-heartbeat");
            t.setDaemon(true);
            return t;
        });

        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                if (connected.get() && authenticated.get()) {
                    sendCommand("api status");
                }
            } catch (Exception e) {
                log.warn("Heartbeat failed: {}", e.getMessage());
            }
        }, config.getHeartbeatInterval(), config.getHeartbeatInterval(), TimeUnit.SECONDS);
    }

    /**
     * 处理接收到的消息
     */
    private void processMessage(EslMessage message) {
        String contentType = message.getContentType();

        if ("command/reply".equals(contentType) || "api/response".equals(contentType)) {
            // 命令响应放入队列
            commandResponseQueue.offer(message);
        } else if ("text/event-plain".equals(contentType) ||
                "text/event-json".equals(contentType) ||
                "text/event-xml".equals(contentType)) {
            // 事件消息
            EslEvent event = EslMessageParser.parseEvent(message);
            if (event != null && eventHandler != null) {
                eventHandler.handleEvent(event);
            }
        } else if ("text/disconnect-notice".equals(contentType)) {
            log.info("Received disconnect notice from FreeSWITCH");
            handleDisconnect();
        }
    }

    /**
     * 读取消息
     */
    private EslMessage readMessage() throws IOException {
        return EslMessageParser.parseMessage(reader);
    }

    /**
     * 发送命令
     */
    private void sendCommand(String command) {
        if (writer != null) {
            writer.println(command);
            writer.println();
            writer.flush();
        }
    }

    /**
     * 发送同步命令并等待响应
     *
     * @param command 命令
     * @return 响应消息
     */
    public EslMessage sendSyncCommand(String command) throws EslCommandException {
        return sendSyncCommand(command, config.getReadTimeout());
    }

    /**
     * 发送同步命令并等待响应
     *
     * @param command 命令
     * @param timeout 超时时间（毫秒）
     * @return 响应消息
     */
    public EslMessage sendSyncCommand(String command, long timeout) throws EslCommandException {
        if (!connected.get() || !authenticated.get()) {
            throw new EslCommandException(command, "Not connected or not authenticated");
        }

        try {
            // 清空之前的响应
            commandResponseQueue.clear();

            sendCommand(command);

            EslMessage response = commandResponseQueue.poll(timeout, TimeUnit.MILLISECONDS);
            if (response == null) {
                throw new EslCommandException(command, "Command timeout");
            }

            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EslCommandException(command, "Command interrupted", e);
        }
    }

    /**
     * 发送异步命令（bgapi）
     *
     * @param command API 命令
     * @return Job UUID
     */
    public String sendAsyncCommand(String command) throws EslCommandException {
        EslMessage response = sendSyncCommand("bgapi " + command);
        if (response != null && response.isOk()) {
            return response.getJobUuid();
        }
        throw new EslCommandException(command, "Failed to execute async command: " +
                (response != null ? response.getReplyText() : "No response"));
    }

    /**
     * 发送 API 命令
     *
     * @param command API 命令
     * @return 响应内容
     */
    public String sendApiCommand(String command) throws EslCommandException {
        EslMessage response = sendSyncCommand("api " + command);
        if (response != null) {
            return response.getBodyText();
        }
        return null;
    }

    /**
     * 处理断开连接
     */
    private void handleDisconnect() {
        connected.set(false);
        authenticated.set(false);

        if (connectionStateListener != null) {
            connectionStateListener.onDisconnected();
        }

        if (config.isAutoReconnect()) {
            scheduleReconnect();
        }
    }

    /**
     * 调度重连
     */
    private void scheduleReconnect() {
        int maxAttempts = config.getMaxReconnectAttempts();
        int attempts = reconnectAttempts.incrementAndGet();

        if (maxAttempts > 0 && attempts > maxAttempts) {
            log.error("Max reconnect attempts ({}) reached, giving up", maxAttempts);
            return;
        }

        log.info("Scheduling reconnect attempt {} in {}ms", attempts, config.getReconnectInterval());

        CompletableFuture.delayedExecutor(config.getReconnectInterval(), TimeUnit.MILLISECONDS)
                .execute(() -> {
                    try {
                        connect();
                    } catch (EslConnectionException e) {
                        log.error("Reconnect attempt {} failed: {}", attempts, e.getMessage());
                        scheduleReconnect();
                    }
                });
    }

    /**
     * 检查连接状态
     *
     * @return 是否已连接
     */
    public boolean isConnected() {
        return connected.get() && authenticated.get();
    }

    /**
     * 关闭连接
     */
    @Override
    public synchronized void close() {
        log.info("Closing ESL connection");

        connected.set(false);
        authenticated.set(false);

        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
        }

        if (eventReaderExecutor != null) {
            eventReaderExecutor.shutdownNow();
        }

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
            log.warn("Error closing connection: {}", e.getMessage());
        }

        log.info("ESL connection closed");
    }

    /**
     * 设置连接状态监听器
     */
    public void setConnectionStateListener(ConnectionStateListener listener) {
        this.connectionStateListener = listener;
    }

    /**
     * 连接状态监听器接口
     */
    public interface ConnectionStateListener {
        void onConnected();
        void onDisconnected();
    }
}
