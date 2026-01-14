package com.ivr.system.service;

import com.ivr.system.engine.IvrFlowEngine;
import com.ivr.system.engine.IvrFlowRepository;
import com.ivr.system.esl.FreeSwitchClient;
import com.ivr.system.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * IVR服务 - 提供IVR核心业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IvrService {

    private final IvrFlowEngine flowEngine;
    private final IvrFlowRepository flowRepository;
    private final FreeSwitchClient freeSwitchClient;

    @Value("${ivr.default-flow:default}")
    private String defaultFlowId;

    /**
     * 处理呼入呼叫
     *
     * @param session 呼叫会话
     * @return 处理结果
     */
    public IvrResult handleInboundCall(CallSession session) {
        log.info("Handling inbound call from [{}] to [{}], session: {}",
                session.getCallerNumber(), session.getCalledNumber(), session.getSessionId());

        String flowId = determineFlowId(session);
        log.info("Using flow [{}] for inbound call", flowId);

        return flowEngine.startFlow(session, flowId);
    }

    /**
     * 发起呼出呼叫
     *
     * @param destination      目标号码
     * @param callerIdNumber   主叫号码
     * @param callerIdName     主叫名称
     * @param flowId           使用的流程ID
     * @param gateway          网关
     * @param variables        自定义变量
     * @return 呼叫会话
     */
    public CallSession initiateOutboundCall(String destination, String callerIdNumber,
                                            String callerIdName, String flowId,
                                            String gateway, Map<String, String> variables) {
        log.info("Initiating outbound call to [{}] from [{}]", destination, callerIdNumber);

        if (flowId == null || flowId.isEmpty()) {
            flowId = defaultFlowId;
        }

        if (gateway == null || gateway.isEmpty()) {
            gateway = "default";
        }

        Map<String, String> vars = new HashMap<>();
        vars.put("ivr_flow_id", flowId);
        if (variables != null) {
            vars.putAll(variables);
        }

        String jobUuid = freeSwitchClient.originate(
                destination, callerIdNumber, callerIdName, gateway, vars);

        if (jobUuid != null) {
            CallSession session = CallSession.builder()
                    .sessionId(jobUuid)
                    .callerNumber(callerIdNumber)
                    .calledNumber(destination)
                    .direction(CallSession.CallDirection.OUTBOUND)
                    .flowId(flowId)
                    .state(CallSession.SessionState.INIT)
                    .startTime(LocalDateTime.now())
                    .build();

            return session;
        }

        return null;
    }

    /**
     * 挂断呼叫
     *
     * @param sessionId 会话ID
     * @param reason    挂断原因
     * @return 处理结果
     */
    public IvrResult hangupCall(String sessionId, String reason) {
        log.info("Hanging up call [{}], reason: {}", sessionId, reason);
        freeSwitchClient.hangup(sessionId, reason != null ? reason : "NORMAL_CLEARING");
        return flowEngine.handleHangup(sessionId, reason);
    }

    /**
     * 转接到坐席
     *
     * @param sessionId  会话ID
     * @param agentGroup 坐席组
     * @param skillGroup 技能组
     * @return 处理结果
     */
    public IvrResult transferToAgent(String sessionId, String agentGroup, String skillGroup) {
        log.info("Transferring [{}] to agent group [{}], skill [{}]",
                sessionId, agentGroup, skillGroup);

        CallSession session = flowEngine.getSession(sessionId);
        if (session == null) {
            return IvrResult.failure("SESSION_NOT_FOUND", "Session not found");
        }

        session.setState(CallSession.SessionState.TRANSFERRING);
        boolean success = freeSwitchClient.transferToAgent(sessionId, agentGroup, skillGroup);

        if (success) {
            session.setState(CallSession.SessionState.IN_QUEUE);
            return IvrResult.success("Transfer initiated", null);
        } else {
            return IvrResult.failure("TRANSFER_FAILED", "Failed to transfer");
        }
    }

    /**
     * 播放语音
     *
     * @param sessionId 会话ID
     * @param content   语音文件路径或TTS文本
     * @param useTts    是否使用TTS
     * @return 处理结果
     */
    public IvrResult playAudio(String sessionId, String content, boolean useTts) {
        log.info("Playing audio for [{}]: {}", sessionId, content);

        boolean success;
        if (useTts) {
            success = freeSwitchClient.speak(sessionId, content);
        } else {
            success = freeSwitchClient.playback(sessionId, content);
        }

        if (success) {
            return IvrResult.success();
        } else {
            return IvrResult.failure("PLAYBACK_FAILED", "Failed to play audio");
        }
    }

    /**
     * 获取会话信息
     *
     * @param sessionId 会话ID
     * @return 会话对象
     */
    public CallSession getSession(String sessionId) {
        return flowEngine.getSession(sessionId);
    }

    /**
     * 获取会话统计信息
     *
     * @return 统计信息
     */
    public Map<String, Object> getSessionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeSessionCount", flowEngine.getActiveSessionCount());
        stats.put("connectedToFreeSWITCH", freeSwitchClient.isConnected());
        stats.put("loadedFlowCount", flowRepository.getAllFlows().size());
        return stats;
    }

    /**
     * 根据会话信息确定使用的流程ID
     */
    private String determineFlowId(CallSession session) {
        String calledNumber = session.getCalledNumber();

        Map<String, IvrFlow> flows = flowRepository.getAllFlows();
        for (IvrFlow flow : flows.values()) {
            if (flow.getParams() != null) {
                Object dids = flow.getParams().get("dids");
                if (dids instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<String> didList = (java.util.List<String>) dids;
                    if (didList.contains(calledNumber)) {
                        return flow.getId();
                    }
                }
            }
        }

        return defaultFlowId;
    }

    /**
     * 重新加载流程
     */
    public void reloadFlows() {
        log.info("Reloading IVR flows");
        flowRepository.reloadFlows();
    }

    /**
     * 获取所有流程
     */
    public Map<String, IvrFlow> getAllFlows() {
        return flowRepository.getAllFlows();
    }

    /**
     * 获取指定流程
     */
    public IvrFlow getFlow(String flowId) {
        return flowRepository.getFlow(flowId);
    }

    /**
     * 注册流程
     */
    public void registerFlow(IvrFlow flow) {
        flowRepository.registerFlow(flow);
    }
}
