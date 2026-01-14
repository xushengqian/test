package com.ivr.system.esl;

import com.ivr.system.config.FreeSwitchConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * FreeSWITCH ESL客户端
 * 实现与FreeSWITCH的Event Socket Library通信
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FreeSwitchClient {

    private final FreeSwitchConfig config;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private volatile boolean connected = false;

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<String, CompletableFuture<EslResponse>> pendingCommands = new ConcurrentHashMap<>();
    private final Map<String, EslEventListener> eventListeners = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (config.isAutoConnect()) {
            connect();
        }
    }

    @PreDestroy
    public void destroy() {
        disconnect();
        executorService.shutdown();
    }

    /**
     * 连接到FreeSWITCH
     */
    public synchronized boolean connect() {
        if (connected) {
            return true;
        }

        try {
            log.info("Connecting to FreeSWITCH at {}:{}", config.getHost(), config.getPort());

            socket = new Socket(config.getHost(), config.getPort());
            socket.setSoTimeout(config.getTimeout());
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            EslResponse authResponse = readResponse();
            if (authResponse == null || !authResponse.isSuccess()) {
                log.error("Failed to receive auth challenge");
                return false;
            }

            sendCommand("auth " + config.getPassword());
            EslResponse loginResponse = readResponse();

            if (loginResponse != null && loginResponse.isSuccess()) {
                connected = true;
                log.info("Successfully connected to FreeSWITCH");

                subscribeEvents("all");

                startEventListener();
                return true;
            } else {
                log.error("Authentication failed");
                disconnect();
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to connect to FreeSWITCH: {}", e.getMessage());
            disconnect();
            return false;
        }
    }

    /**
     * 断开连接
     */
    public synchronized void disconnect() {
        connected = false;
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            log.error("Error disconnecting: {}", e.getMessage());
        }
        log.info("Disconnected from FreeSWITCH");
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
     * 读取响应
     */
    private EslResponse readResponse() throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;

        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int colonPos = line.indexOf(':');
            if (colonPos > 0) {
                String key = line.substring(0, colonPos).trim();
                String value = line.substring(colonPos + 1).trim();
                headers.put(key, value);
            }
        }

        String body = null;
        if (headers.containsKey("Content-Length")) {
            int length = Integer.parseInt(headers.get("Content-Length"));
            char[] buffer = new char[length];
            int read = reader.read(buffer, 0, length);
            if (read > 0) {
                body = new String(buffer, 0, read);
            }
        }

        return new EslResponse(headers, body);
    }

    /**
     * 订阅事件
     */
    public void subscribeEvents(String events) {
        sendCommand("event plain " + events);
    }

    /**
     * 启动事件监听器
     */
    private void startEventListener() {
        executorService.submit(() -> {
            while (connected) {
                try {
                    EslResponse response = readResponse();
                    if (response != null) {
                        handleEvent(response);
                    }
                } catch (IOException e) {
                    if (connected) {
                        log.error("Error reading event: {}", e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * 处理事件
     */
    private void handleEvent(EslResponse response) {
        String eventName = response.getHeader("Event-Name");
        String uuid = response.getHeader("Unique-ID");

        if (eventName != null) {
            log.debug("Received event: {} for UUID: {}", eventName, uuid);

            eventListeners.forEach((key, listener) -> {
                try {
                    listener.onEvent(eventName, uuid, response);
                } catch (Exception e) {
                    log.error("Error in event listener: {}", e.getMessage());
                }
            });
        }
    }

    /**
     * 注册事件监听器
     */
    public String addEventListener(EslEventListener listener) {
        String listenerId = UUID.randomUUID().toString();
        eventListeners.put(listenerId, listener);
        return listenerId;
    }

    /**
     * 移除事件监听器
     */
    public void removeEventListener(String listenerId) {
        eventListeners.remove(listenerId);
    }

    /**
     * 执行API命令
     */
    public EslResponse api(String command) {
        return execute("api " + command);
    }

    /**
     * 执行bgapi命令
     */
    public String bgapi(String command) {
        EslResponse response = execute("bgapi " + command);
        if (response != null && response.getBody() != null) {
            return response.getBody().replace("Job-UUID: ", "").trim();
        }
        return null;
    }

    /**
     * 执行命令
     */
    private synchronized EslResponse execute(String command) {
        if (!connected) {
            log.warn("Not connected to FreeSWITCH");
            return null;
        }

        try {
            sendCommand(command);
            return readResponse();
        } catch (IOException e) {
            log.error("Error executing command: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 播放音频文件
     */
    public boolean playback(String uuid, String file) {
        log.info("Playing file [{}] for UUID [{}]", file, uuid);
        EslResponse response = api("uuid_broadcast " + uuid + " " + file + " aleg");
        return response != null && response.isSuccess();
    }

    /**
     * 使用TTS播放文本
     */
    public boolean speak(String uuid, String text) {
        log.info("Speaking text for UUID [{}]: {}", uuid, text);
        String ttsEngine = config.getTtsEngine();
        String ttsVoice = config.getTtsVoice();
        EslResponse response = api("uuid_broadcast " + uuid +
                " speak:" + ttsEngine + "|" + ttsVoice + "|" + text + " aleg");
        return response != null && response.isSuccess();
    }

    /**
     * 开始DTMF收集
     */
    public boolean startDtmfCollection(String uuid, int maxDigits, long timeoutMs) {
        log.info("Starting DTMF collection for UUID [{}], max digits: {}, timeout: {}ms",
                uuid, maxDigits, timeoutMs);

        api("uuid_setvar " + uuid + " max_digits " + maxDigits);
        api("uuid_setvar " + uuid + " digit_timeout " + (timeoutMs / 1000));

        return true;
    }

    /**
     * 停止DTMF收集
     */
    public boolean stopDtmfCollection(String uuid) {
        log.info("Stopping DTMF collection for UUID [{}]", uuid);
        return true;
    }

    /**
     * 转接到坐席
     */
    public boolean transferToAgent(String uuid, String agentGroup, String skillGroup) {
        log.info("Transferring UUID [{}] to agent group [{}], skill [{}]",
                uuid, agentGroup, skillGroup);

        String dialString = "user/" + agentGroup;
        if (skillGroup != null && !skillGroup.isEmpty()) {
            dialString = "loopback/queue_" + skillGroup;
        }

        EslResponse response = api("uuid_transfer " + uuid + " " + dialString);
        return response != null && response.isSuccess();
    }

    /**
     * 转接到外部号码
     */
    public boolean transferToExternal(String uuid, String destination, String gateway) {
        log.info("Transferring UUID [{}] to external [{}] via gateway [{}]",
                uuid, destination, gateway);

        String dialString = "sofia/gateway/" + gateway + "/" + destination;
        EslResponse response = api("uuid_transfer " + uuid + " " + dialString);
        return response != null && response.isSuccess();
    }

    /**
     * 挂断通话
     */
    public boolean hangup(String uuid, String cause) {
        log.info("Hanging up UUID [{}] with cause [{}]", uuid, cause);
        EslResponse response = api("uuid_kill " + uuid + " " + cause);
        return response != null && response.isSuccess();
    }

    /**
     * 开始录音
     */
    public boolean startRecording(String uuid, String filePath, int maxDuration, int silenceThreshold) {
        log.info("Starting recording for UUID [{}] to file [{}]", uuid, filePath);
        EslResponse response = api("uuid_record " + uuid + " start " + filePath +
                " " + maxDuration + " " + silenceThreshold);
        return response != null && response.isSuccess();
    }

    /**
     * 停止录音
     */
    public boolean stopRecording(String uuid) {
        log.info("Stopping recording for UUID [{}]", uuid);
        EslResponse response = api("uuid_record " + uuid + " stop");
        return response != null && response.isSuccess();
    }

    /**
     * 进入队列
     */
    public boolean enterQueue(String uuid, String queueName, String holdMusic) {
        log.info("Entering queue [{}] for UUID [{}]", queueName, uuid);

        if (holdMusic != null && !holdMusic.isEmpty()) {
            api("uuid_setvar " + uuid + " hold_music " + holdMusic);
        }

        EslResponse response = api("uuid_transfer " + uuid + " callcenter:" + queueName);
        return response != null && response.isSuccess();
    }

    /**
     * 开始ASR识别
     */
    public boolean startAsrRecognition(String uuid, String engine, String grammar, long timeout) {
        log.info("Starting ASR for UUID [{}] with engine [{}]", uuid, engine);

        api("uuid_setvar " + uuid + " asr_engine " + engine);
        if (grammar != null) {
            api("uuid_setvar " + uuid + " asr_grammar " + grammar);
        }
        api("uuid_setvar " + uuid + " asr_timeout " + (timeout / 1000));

        EslResponse response = api("uuid_broadcast " + uuid + " detect_speech aleg");
        return response != null && response.isSuccess();
    }

    /**
     * 发起呼出
     */
    public String originate(String destination, String callerIdNumber, String callerIdName,
                            String gateway, Map<String, String> variables) {
        log.info("Originating call to [{}] from [{}]", destination, callerIdNumber);

        StringBuilder dialString = new StringBuilder();
        dialString.append("{");

        if (callerIdNumber != null) {
            dialString.append("origination_caller_id_number=").append(callerIdNumber).append(",");
        }
        if (callerIdName != null) {
            dialString.append("origination_caller_id_name=").append(callerIdName).append(",");
        }

        if (variables != null) {
            variables.forEach((k, v) -> dialString.append(k).append("=").append(v).append(","));
        }

        if (dialString.charAt(dialString.length() - 1) == ',') {
            dialString.setLength(dialString.length() - 1);
        }

        dialString.append("}");
        dialString.append("sofia/gateway/").append(gateway).append("/").append(destination);

        String jobUuid = bgapi("originate " + dialString + " &park()");
        log.info("Originate job UUID: {}", jobUuid);
        return jobUuid;
    }

    /**
     * 桥接两个通话
     */
    public boolean bridge(String uuid1, String uuid2) {
        log.info("Bridging UUID [{}] with UUID [{}]", uuid1, uuid2);
        EslResponse response = api("uuid_bridge " + uuid1 + " " + uuid2);
        return response != null && response.isSuccess();
    }

    /**
     * 设置通道变量
     */
    public boolean setVariable(String uuid, String variable, String value) {
        EslResponse response = api("uuid_setvar " + uuid + " " + variable + " " + value);
        return response != null && response.isSuccess();
    }

    /**
     * 获取通道变量
     */
    public String getVariable(String uuid, String variable) {
        EslResponse response = api("uuid_getvar " + uuid + " " + variable);
        if (response != null && response.getBody() != null) {
            return response.getBody().trim();
        }
        return null;
    }

    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * 事件监听器接口
     */
    public interface EslEventListener {
        void onEvent(String eventName, String uuid, EslResponse response);
    }
}
