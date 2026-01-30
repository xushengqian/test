package com.freeswitch.esl.client;

import com.freeswitch.esl.config.FreeSwitchConfig;
import com.freeswitch.esl.event.EslEvent;
import com.freeswitch.esl.event.EslEventHandler;
import com.freeswitch.esl.event.EslEventListener;
import com.freeswitch.esl.exception.EslCommandException;
import com.freeswitch.esl.exception.EslConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * FreeSWITCH 客户端
 * 
 * 提供与 FreeSWITCH 交互的高级 API
 */
@Component
public class FreeSwitchClient {

    private static final Logger log = LoggerFactory.getLogger(FreeSwitchClient.class);

    private final FreeSwitchConfig config;
    private final EslEventHandler eventHandler;
    private EslConnection connection;

    /**
     * 异步作业结果缓存
     */
    private final Map<String, CompletableFuture<EslEvent>> jobFutures = new ConcurrentHashMap<>();

    @Autowired
    public FreeSwitchClient(FreeSwitchConfig config, EslEventHandler eventHandler) {
        this.config = config;
        this.eventHandler = eventHandler;
    }

    /**
     * 初始化连接
     */
    @PostConstruct
    public void init() {
        try {
            connect();
            setupBackgroundJobListener();
        } catch (Exception e) {
            log.error("Failed to initialize FreeSWITCH client: {}", e.getMessage());
        }
    }

    /**
     * 建立连接
     */
    public void connect() throws EslConnectionException {
        if (connection != null && connection.isConnected()) {
            return;
        }

        connection = new EslConnection(config, eventHandler);
        connection.setConnectionStateListener(new EslConnection.ConnectionStateListener() {
            @Override
            public void onConnected() {
                log.info("FreeSWITCH client connected");
            }

            @Override
            public void onDisconnected() {
                log.warn("FreeSWITCH client disconnected");
            }
        });

        connection.connect();
    }

    /**
     * 设置后台作业监听器
     */
    private void setupBackgroundJobListener() {
        eventHandler.addEventListener("BACKGROUND_JOB", new EslEventListener() {
            @Override
            public void onEvent(EslEvent event) {
                String jobUuid = event.getHeader("Job-UUID");
                if (jobUuid != null) {
                    CompletableFuture<EslEvent> future = jobFutures.remove(jobUuid);
                    if (future != null) {
                        future.complete(event);
                    }
                }
            }
        });
    }

    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return connection != null && connection.isConnected();
    }

    // ==================== 呼叫控制 API ====================

    /**
     * 发起呼叫（originate）
     *
     * @param destination   目标号码
     * @param callerIdNum   主叫号码
     * @param callerIdName  主叫名称
     * @param extension     分机号
     * @param dialplan      拨号计划
     * @param context       上下文
     * @return Channel UUID
     */
    public String originate(String destination, String callerIdNum, String callerIdName,
                            String extension, String dialplan, String context) throws EslCommandException {
        return originate(destination, callerIdNum, callerIdName, extension, dialplan, context, null, 60);
    }

    /**
     * 发起呼叫（originate）- 完整版本
     *
     * @param destination   目标号码
     * @param callerIdNum   主叫号码
     * @param callerIdName  主叫名称
     * @param extension     分机号
     * @param dialplan      拨号计划
     * @param context       上下文
     * @param variables     通道变量
     * @param timeout       超时时间（秒）
     * @return Channel UUID
     */
    public String originate(String destination, String callerIdNum, String callerIdName,
                            String extension, String dialplan, String context,
                            Map<String, String> variables, int timeout) throws EslCommandException {
        
        StringBuilder cmd = new StringBuilder();
        cmd.append("originate ");

        // 构建变量字符串
        StringBuilder varStr = new StringBuilder("{");
        varStr.append("origination_caller_id_number=").append(callerIdNum).append(",");
        varStr.append("origination_caller_id_name=").append(callerIdName).append(",");
        varStr.append("originate_timeout=").append(timeout);

        if (variables != null && !variables.isEmpty()) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                varStr.append(",").append(entry.getKey()).append("=").append(entry.getValue());
            }
        }
        varStr.append("}");

        cmd.append(varStr);
        cmd.append(destination);
        cmd.append(" ");
        cmd.append(extension != null ? extension : destination);
        cmd.append(" ");
        cmd.append(dialplan != null ? dialplan : "XML");
        cmd.append(" ");
        cmd.append(context != null ? context : "default");

        String result = sendApiCommand(cmd.toString());
        
        // 解析返回的 UUID
        if (result != null && result.startsWith("+OK")) {
            return result.substring(4).trim();
        }

        throw new EslCommandException("originate", "Originate failed: " + result);
    }

    /**
     * 异步发起呼叫
     *
     * @param destination 目标号码
     * @param callerIdNum 主叫号码
     * @param extension   分机号
     * @param context     上下文
     * @return CompletableFuture<EslEvent>
     */
    public CompletableFuture<EslEvent> originateAsync(String destination, String callerIdNum,
                                                       String extension, String context) throws EslCommandException {
        StringBuilder cmd = new StringBuilder();
        cmd.append("originate ");
        cmd.append("{origination_caller_id_number=").append(callerIdNum).append("}");
        cmd.append(destination);
        cmd.append(" ");
        cmd.append(extension != null ? extension : destination);
        cmd.append(" XML ");
        cmd.append(context != null ? context : "default");

        String jobUuid = connection.sendAsyncCommand(cmd.toString());
        
        CompletableFuture<EslEvent> future = new CompletableFuture<>();
        jobFutures.put(jobUuid, future);

        // 设置超时
        future.orTimeout(60, TimeUnit.SECONDS)
                .whenComplete((event, ex) -> jobFutures.remove(jobUuid));

        return future;
    }

    /**
     * 桥接两个通道
     *
     * @param uuid1 第一个通道 UUID
     * @param uuid2 第二个通道 UUID
     * @return 是否成功
     */
    public boolean bridge(String uuid1, String uuid2) throws EslCommandException {
        String result = sendApiCommand("uuid_bridge " + uuid1 + " " + uuid2);
        return result != null && result.startsWith("+OK");
    }

    /**
     * 将通道桥接到目标
     *
     * @param uuid        通道 UUID
     * @param destination 目标
     * @return 是否成功
     */
    public boolean bridgeTo(String uuid, String destination) throws EslCommandException {
        return executeApp(uuid, "bridge", destination);
    }

    /**
     * 转接呼叫
     *
     * @param uuid        通道 UUID
     * @param destination 目标号码
     * @param dialplan    拨号计划
     * @param context     上下文
     * @return 是否成功
     */
    public boolean transfer(String uuid, String destination, String dialplan, String context) throws EslCommandException {
        String cmd = String.format("uuid_transfer %s %s %s %s",
                uuid, destination,
                dialplan != null ? dialplan : "XML",
                context != null ? context : "default");
        String result = sendApiCommand(cmd);
        return result != null && result.startsWith("+OK");
    }

    /**
     * 挂断呼叫
     *
     * @param uuid  通道 UUID
     * @param cause 挂断原因
     * @return 是否成功
     */
    public boolean hangup(String uuid, String cause) throws EslCommandException {
        String cmd = "uuid_kill " + uuid;
        if (cause != null && !cause.isEmpty()) {
            cmd += " " + cause;
        }
        String result = sendApiCommand(cmd);
        return result != null && result.startsWith("+OK");
    }

    /**
     * 挂断呼叫（默认原因）
     *
     * @param uuid 通道 UUID
     * @return 是否成功
     */
    public boolean hangup(String uuid) throws EslCommandException {
        return hangup(uuid, "NORMAL_CLEARING");
    }

    /**
     * 应答呼叫
     *
     * @param uuid 通道 UUID
     * @return 是否成功
     */
    public boolean answer(String uuid) throws EslCommandException {
        return executeApp(uuid, "answer", null);
    }

    /**
     * 保持呼叫
     *
     * @param uuid 通道 UUID
     * @return 是否成功
     */
    public boolean hold(String uuid) throws EslCommandException {
        String result = sendApiCommand("uuid_hold " + uuid);
        return result != null && result.startsWith("+OK");
    }

    /**
     * 取消保持
     *
     * @param uuid 通道 UUID
     * @return 是否成功
     */
    public boolean unhold(String uuid) throws EslCommandException {
        String result = sendApiCommand("uuid_hold off " + uuid);
        return result != null && result.startsWith("+OK");
    }

    // ==================== 媒体控制 API ====================

    /**
     * 播放音频文件
     *
     * @param uuid     通道 UUID
     * @param filePath 音频文件路径
     * @return 是否成功
     */
    public boolean playback(String uuid, String filePath) throws EslCommandException {
        return executeApp(uuid, "playback", filePath);
    }

    /**
     * 异步播放音频（可打断）
     *
     * @param uuid     通道 UUID
     * @param filePath 音频文件路径
     * @return 是否成功
     */
    public boolean playbackAsync(String uuid, String filePath) throws EslCommandException {
        String result = sendApiCommand("uuid_broadcast " + uuid + " " + filePath + " aleg");
        return result != null && result.startsWith("+OK");
    }

    /**
     * 停止播放
     *
     * @param uuid 通道 UUID
     * @return 是否成功
     */
    public boolean stopPlayback(String uuid) throws EslCommandException {
        return executeApp(uuid, "break", null);
    }

    /**
     * 替换/叠加音频（uuid_displace）
     *
     * @param uuid     通道 UUID
     * @param filePath 音频文件路径
     * @param flags    标志 (例如: "m" 混合, "l" 循环)
     * @return 是否成功
     */
    public boolean displace(String uuid, String filePath, String flags) throws EslCommandException {
        String cmd = "uuid_displace " + uuid + " start " + filePath;
        if (flags != null && !flags.isEmpty()) {
            cmd += " " + flags;
        }
        String result = sendApiCommand(cmd);
        return result != null && result.startsWith("+OK");
    }

    /**
     * 停止叠加音频
     *
     * @param uuid     通道 UUID
     * @param filePath 音频文件路径
     * @return 是否成功
     */
    public boolean stopDisplace(String uuid, String filePath) throws EslCommandException {
        String result = sendApiCommand("uuid_displace " + uuid + " stop " + filePath);
        return result != null && result.startsWith("+OK");
    }

    /**
     * 开始录音
     *
     * @param uuid     通道 UUID
     * @param filePath 录音文件路径
     * @return 是否成功
     */
    public boolean startRecord(String uuid, String filePath) throws EslCommandException {
        String result = sendApiCommand("uuid_record " + uuid + " start " + filePath);
        return result != null && result.startsWith("+OK");
    }

    /**
     * 停止录音
     *
     * @param uuid     通道 UUID
     * @param filePath 录音文件路径
     * @return 是否成功
     */
    public boolean stopRecord(String uuid, String filePath) throws EslCommandException {
        String result = sendApiCommand("uuid_record " + uuid + " stop " + filePath);
        return result != null && result.startsWith("+OK");
    }

    /**
     * 发送 DTMF
     *
     * @param uuid   通道 UUID
     * @param digits DTMF 数字
     * @return 是否成功
     */
    public boolean sendDtmf(String uuid, String digits) throws EslCommandException {
        String result = sendApiCommand("uuid_send_dtmf " + uuid + " " + digits);
        return result != null && result.startsWith("+OK");
    }

    // ==================== 变量操作 API ====================

    /**
     * 设置通道变量
     *
     * @param uuid  通道 UUID
     * @param name  变量名
     * @param value 变量值
     * @return 是否成功
     */
    public boolean setVariable(String uuid, String name, String value) throws EslCommandException {
        String result = sendApiCommand("uuid_setvar " + uuid + " " + name + " " + value);
        return result != null && result.startsWith("+OK");
    }

    /**
     * 设置多个通道变量
     *
     * @param uuid      通道 UUID
     * @param variables 变量映射
     * @return 是否成功
     */
    public boolean setVariables(String uuid, Map<String, String> variables) throws EslCommandException {
        StringBuilder vars = new StringBuilder();
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            vars.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
        }
        String result = sendApiCommand("uuid_setvar_multi " + uuid + " " + vars);
        return result != null && result.startsWith("+OK");
    }

    /**
     * 获取通道变量
     *
     * @param uuid 通道 UUID
     * @param name 变量名
     * @return 变量值
     */
    public String getVariable(String uuid, String name) throws EslCommandException {
        String result = sendApiCommand("uuid_getvar " + uuid + " " + name);
        if (result != null && !result.startsWith("-ERR")) {
            return result.trim();
        }
        return null;
    }

    // ==================== 通道信息 API ====================

    /**
     * 获取通道状态
     *
     * @param uuid 通道 UUID
     * @return 通道信息
     */
    public String getChannelInfo(String uuid) throws EslCommandException {
        return sendApiCommand("uuid_dump " + uuid);
    }

    /**
     * 检查通道是否存在
     *
     * @param uuid 通道 UUID
     * @return 是否存在
     */
    public boolean channelExists(String uuid) throws EslCommandException {
        String result = sendApiCommand("uuid_exists " + uuid);
        return "true".equalsIgnoreCase(result != null ? result.trim() : "");
    }

    /**
     * 获取活动呼叫数量
     *
     * @return 呼叫数量
     */
    public int getActiveCallCount() throws EslCommandException {
        String result = sendApiCommand("show calls count");
        if (result != null) {
            try {
                // 解析返回格式
                String[] lines = result.split("\n");
                for (String line : lines) {
                    if (line.contains("total")) {
                        String[] parts = line.split(" ");
                        return Integer.parseInt(parts[0]);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse call count: {}", e.getMessage());
            }
        }
        return 0;
    }

    /**
     * 获取 FreeSWITCH 状态
     *
     * @return 状态信息
     */
    public String getStatus() throws EslCommandException {
        return sendApiCommand("status");
    }

    // ==================== 应用执行 API ====================

    /**
     * 在通道上执行应用
     *
     * @param uuid 通道 UUID
     * @param app  应用名称
     * @param args 应用参数
     * @return 是否成功
     */
    public boolean executeApp(String uuid, String app, String args) throws EslCommandException {
        StringBuilder cmd = new StringBuilder("uuid_broadcast ");
        cmd.append(uuid).append(" ");
        cmd.append(app);
        if (args != null && !args.isEmpty()) {
            cmd.append("::").append(args);
        }
        cmd.append(" aleg");

        String result = sendApiCommand(cmd.toString());
        return result != null && result.startsWith("+OK");
    }

    /**
     * 发送消息到通道
     *
     * @param uuid    通道 UUID
     * @param app     应用名称
     * @param args    应用参数
     * @param async   是否异步
     * @param loops   循环次数
     * @return 是否成功
     */
    public boolean sendMsg(String uuid, String app, String args, boolean async, int loops) throws EslCommandException {
        StringBuilder cmd = new StringBuilder("sendmsg ");
        cmd.append(uuid).append("\n");
        cmd.append("call-command: execute\n");
        cmd.append("execute-app-name: ").append(app).append("\n");
        if (args != null && !args.isEmpty()) {
            cmd.append("execute-app-arg: ").append(args).append("\n");
        }
        if (async) {
            cmd.append("async: true\n");
        }
        if (loops > 1) {
            cmd.append("loops: ").append(loops).append("\n");
        }

        EslMessage response = connection.sendSyncCommand(cmd.toString());
        return response != null && response.isOk();
    }

    // ==================== 底层 API ====================

    /**
     * 发送 API 命令
     *
     * @param command API 命令
     * @return 响应内容
     */
    public String sendApiCommand(String command) throws EslCommandException {
        ensureConnected();
        return connection.sendApiCommand(command);
    }

    /**
     * 发送异步 API 命令
     *
     * @param command API 命令
     * @return Job UUID
     */
    public String sendAsyncApiCommand(String command) throws EslCommandException {
        ensureConnected();
        return connection.sendAsyncCommand(command);
    }

    /**
     * 发送同步命令
     *
     * @param command 命令
     * @return 响应消息
     */
    public EslMessage sendSyncCommand(String command) throws EslCommandException {
        ensureConnected();
        return connection.sendSyncCommand(command);
    }

    /**
     * 确保已连接
     */
    private void ensureConnected() throws EslConnectionException {
        if (!isConnected()) {
            connect();
        }
    }

    /**
     * 添加事件监听器
     */
    public void addEventListener(String eventType, EslEventListener listener) {
        eventHandler.addEventListener(eventType, listener);
    }

    /**
     * 添加 UUID 监听器
     */
    public void addUuidListener(String uuid, EslEventListener listener) {
        eventHandler.addUuidListener(uuid, listener);
    }

    /**
     * 移除 UUID 监听器
     */
    public void removeUuidListener(String uuid, EslEventListener listener) {
        eventHandler.removeUuidListener(uuid, listener);
    }

    /**
     * 销毁客户端
     */
    @PreDestroy
    public void destroy() {
        log.info("Destroying FreeSWITCH client");
        if (connection != null) {
            connection.close();
        }
        eventHandler.shutdown();
    }
}
