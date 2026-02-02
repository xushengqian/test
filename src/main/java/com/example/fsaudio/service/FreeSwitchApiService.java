package com.example.fsaudio.service;

import com.example.fsaudio.esl.EslConnectionManager;
import com.example.fsaudio.handler.EslEventHandler;
import com.example.fsaudio.model.CallSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * FreeSwtich API服务
 * 
 * 提供对FreeSwtich的各种操作接口
 */
@Slf4j
@Service
public class FreeSwitchApiService {

    private final EslConnectionManager eslManager;
    private final EslEventHandler eventHandler;

    @Autowired
    public FreeSwitchApiService(EslConnectionManager eslManager, EslEventHandler eventHandler) {
        this.eslManager = eslManager;
        this.eventHandler = eventHandler;
    }

    /**
     * 发起呼叫
     * 
     * @param callerNumber 主叫号码
     * @param calleeNumber 被叫号码
     * @param gateway 网关
     * @return 通话UUID
     */
    public String originate(String callerNumber, String calleeNumber, String gateway) {
        String dialString = String.format("sofia/gateway/%s/%s", gateway, calleeNumber);
        String command = String.format("originate {origination_caller_id_number=%s}%s &park", 
                callerNumber, dialString);
        
        String response = eslManager.executeApi(command);
        log.info("发起呼叫响应: {}", response);
        
        // 解析UUID
        if (response != null && response.startsWith("+OK")) {
            return response.replace("+OK ", "").trim();
        }
        return null;
    }

    /**
     * 挂断通话
     * 
     * @param uuid 通话UUID
     * @param cause 挂断原因
     */
    public void hangup(String uuid, String cause) {
        String command = String.format("uuid_kill %s %s", uuid, cause != null ? cause : "NORMAL_CLEARING");
        String response = eslManager.executeApi(command);
        log.info("挂断通话响应: {}", response);
    }

    /**
     * 通话转接 (桥接)
     * 
     * @param uuid 原通话UUID
     * @param destination 目标号码/通道
     */
    public void bridge(String uuid, String destination) {
        String command = String.format("uuid_bridge %s %s", uuid, destination);
        String response = eslManager.executeApi(command);
        log.info("通话转接响应: {}", response);
    }

    /**
     * 播放音频文件
     * 
     * @param uuid 通话UUID
     * @param audioFile 音频文件路径
     */
    public void playback(String uuid, String audioFile) {
        String command = String.format("uuid_broadcast %s %s both", uuid, audioFile);
        String response = eslManager.executeApi(command);
        log.info("播放音频响应: {}", response);
    }

    /**
     * 开始录音
     * 
     * @param uuid 通话UUID
     * @param filePath 录音文件路径
     */
    public void startRecord(String uuid, String filePath) {
        String command = String.format("uuid_record %s start %s", uuid, filePath);
        String response = eslManager.executeApi(command);
        log.info("开始录音响应: {}", response);
    }

    /**
     * 停止录音
     * 
     * @param uuid 通话UUID
     * @param filePath 录音文件路径
     */
    public void stopRecord(String uuid, String filePath) {
        String command = String.format("uuid_record %s stop %s", uuid, filePath);
        String response = eslManager.executeApi(command);
        log.info("停止录音响应: {}", response);
    }

    /**
     * 发送DTMF
     * 
     * @param uuid 通话UUID
     * @param digits DTMF数字
     */
    public void sendDtmf(String uuid, String digits) {
        String command = String.format("uuid_send_dtmf %s %s", uuid, digits);
        String response = eslManager.executeApi(command);
        log.info("发送DTMF响应: {}", response);
    }

    /**
     * 保持/取消保持
     * 
     * @param uuid 通话UUID
     * @param hold true=保持, false=取消保持
     */
    public void hold(String uuid, boolean hold) {
        String command = String.format("uuid_hold %s %s", hold ? "" : "off", uuid);
        String response = eslManager.executeApi(command);
        log.info("保持通话响应: {}", response);
    }

    /**
     * 设置通道变量
     * 
     * @param uuid 通话UUID
     * @param variable 变量名
     * @param value 变量值
     */
    public void setVariable(String uuid, String variable, String value) {
        String command = String.format("uuid_setvar %s %s %s", uuid, variable, value);
        String response = eslManager.executeApi(command);
        log.debug("设置变量响应: {}", response);
    }

    /**
     * 获取通道变量
     * 
     * @param uuid 通话UUID
     * @param variable 变量名
     * @return 变量值
     */
    public String getVariable(String uuid, String variable) {
        String command = String.format("uuid_getvar %s %s", uuid, variable);
        return eslManager.executeApi(command);
    }

    /**
     * 获取活跃通道列表
     * 
     * @return 通道列表信息
     */
    public String showChannels() {
        return eslManager.executeApi("show channels");
    }

    /**
     * 获取FreeSwtich状态
     * 
     * @return 状态信息
     */
    public String getStatus() {
        return eslManager.executeApi("status");
    }

    /**
     * 获取活跃呼叫数
     * 
     * @return 呼叫数
     */
    public String getCallCount() {
        return eslManager.executeApi("show calls count");
    }

    /**
     * 获取通话会话信息
     * 
     * @param uuid 通话UUID
     * @return 会话信息
     */
    public CallSession getCallSession(String uuid) {
        return eventHandler.getSession(uuid);
    }

    /**
     * 获取所有活跃会话
     * 
     * @return 活跃会话Map
     */
    public Map<String, CallSession> getAllSessions() {
        return eventHandler.getAllSessions();
    }

    /**
     * 执行通用API命令
     * 
     * @param command API命令
     * @return 响应结果
     */
    public String executeCommand(String command) {
        return eslManager.executeApi(command);
    }

    /**
     * 检查ESL连接状态
     * 
     * @return 是否已连接
     */
    public boolean isConnected() {
        return eslManager.isConnected();
    }
}
