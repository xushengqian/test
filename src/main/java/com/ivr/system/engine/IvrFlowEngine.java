package com.ivr.system.engine;

import com.ivr.system.handler.NodeHandler;
import com.ivr.system.handler.NodeHandlerFactory;
import com.ivr.system.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IVR流程引擎 - 负责执行IVR流程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IvrFlowEngine {

    private final NodeHandlerFactory nodeHandlerFactory;
    private final IvrFlowRepository flowRepository;

    /**
     * 活跃会话存储
     */
    private final Map<String, CallSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * 开始IVR流程
     *
     * @param session 呼叫会话
     * @param flowId  流程ID
     * @return 执行结果
     */
    public IvrResult startFlow(CallSession session, String flowId) {
        log.info("Starting IVR flow [{}] for session [{}]", flowId, session.getSessionId());

        IvrFlow flow = flowRepository.getFlow(flowId);
        if (flow == null) {
            log.error("Flow not found: {}", flowId);
            return IvrResult.failure("FLOW_NOT_FOUND", "IVR flow not found: " + flowId);
        }

        if (!flow.isEnabled()) {
            log.warn("Flow is disabled: {}", flowId);
            return IvrResult.failure("FLOW_DISABLED", "IVR flow is disabled: " + flowId);
        }

        session.setFlowId(flowId);
        session.setCurrentNodeId(flow.getEntryNodeId());
        session.setState(CallSession.SessionState.RUNNING);
        session.setStartTime(LocalDateTime.now());

        activeSessions.put(session.getSessionId(), session);

        return executeCurrentNode(session);
    }

    /**
     * 执行当前节点
     *
     * @param session 呼叫会话
     * @return 执行结果
     */
    public IvrResult executeCurrentNode(CallSession session) {
        String nodeId = session.getCurrentNodeId();
        String flowId = session.getFlowId();

        log.debug("Executing node [{}] for session [{}]", nodeId, session.getSessionId());

        IvrFlow flow = flowRepository.getFlow(flowId);
        if (flow == null) {
            return IvrResult.failure("FLOW_NOT_FOUND", "Flow not found");
        }

        IvrNode node = flow.getNodeById(nodeId);
        if (node == null) {
            log.error("Node not found: {} in flow: {}", nodeId, flowId);
            return IvrResult.failure("NODE_NOT_FOUND", "Node not found: " + nodeId);
        }

        NodeHandler handler = nodeHandlerFactory.getHandler(node.getType());
        if (handler == null) {
            log.error("No handler for node type: {}", node.getType());
            return IvrResult.failure("HANDLER_NOT_FOUND", "No handler for type: " + node.getType());
        }

        try {
            return handler.execute(session, node, flow);
        } catch (Exception e) {
            log.error("Error executing node [{}]: {}", nodeId, e.getMessage(), e);
            return IvrResult.failure("EXECUTION_ERROR", e.getMessage());
        }
    }

    /**
     * 处理DTMF输入
     *
     * @param sessionId 会话ID
     * @param dtmf      DTMF按键
     * @return 执行结果
     */
    public IvrResult handleDtmfInput(String sessionId, String dtmf) {
        log.info("Handling DTMF [{}] for session [{}]", dtmf, sessionId);

        CallSession session = activeSessions.get(sessionId);
        if (session == null) {
            log.warn("Session not found: {}", sessionId);
            return IvrResult.failure("SESSION_NOT_FOUND", "Session not found");
        }

        IvrFlow flow = flowRepository.getFlow(session.getFlowId());
        IvrNode currentNode = flow.getNodeById(session.getCurrentNodeId());

        if (currentNode == null) {
            return IvrResult.failure("NODE_NOT_FOUND", "Current node not found");
        }

        String collectedDtmf = session.getCollectedDtmf();
        if (collectedDtmf == null) {
            collectedDtmf = "";
        }
        collectedDtmf += dtmf;
        session.setCollectedDtmf(collectedDtmf);

        if (currentNode.getType() == IvrNode.NodeType.MENU) {
            return handleMenuInput(session, currentNode, dtmf, flow);
        } else if (currentNode.getType() == IvrNode.NodeType.COLLECT) {
            return handleCollectInput(session, currentNode, collectedDtmf, flow);
        }

        return IvrResult.success();
    }

    /**
     * 处理菜单输入
     */
    private IvrResult handleMenuInput(CallSession session, IvrNode node, String dtmf, IvrFlow flow) {
        Map<String, String> dtmfMapping = node.getDtmfMapping();

        if (dtmfMapping != null && dtmfMapping.containsKey(dtmf)) {
            String nextNodeId = dtmfMapping.get(dtmf);
            return moveToNode(session, nextNodeId);
        }

        int maxRetries = node.getMaxRetries() > 0 ? node.getMaxRetries() : flow.getGlobalMaxRetries();
        session.setRetryCount(session.getRetryCount() + 1);

        if (session.getRetryCount() >= maxRetries) {
            String defaultNext = node.getDefaultNextNode();
            if (defaultNext != null) {
                return moveToNode(session, defaultNext);
            }
            return handleHangup(session.getSessionId(), "Max retries exceeded");
        }

        return IvrResult.retry("Invalid input, please try again");
    }

    /**
     * 处理收集输入
     */
    private IvrResult handleCollectInput(CallSession session, IvrNode node, String collected, IvrFlow flow) {
        if (collected.endsWith("#")) {
            session.setVariable("collected_input", collected.substring(0, collected.length() - 1));
            session.setCollectedDtmf("");
            String nextNodeId = node.getDefaultNextNode();
            if (nextNodeId != null) {
                return moveToNode(session, nextNodeId);
            }
        }
        return IvrResult.success();
    }

    /**
     * 移动到指定节点
     *
     * @param session    会话
     * @param nextNodeId 下一节点ID
     * @return 执行结果
     */
    public IvrResult moveToNode(CallSession session, String nextNodeId) {
        log.info("Moving session [{}] from node [{}] to [{}]",
                session.getSessionId(), session.getCurrentNodeId(), nextNodeId);

        session.setCurrentNodeId(nextNodeId);
        session.setRetryCount(0);
        session.setCollectedDtmf("");

        return executeCurrentNode(session);
    }

    /**
     * 处理超时
     *
     * @param sessionId 会话ID
     * @return 执行结果
     */
    public IvrResult handleTimeout(String sessionId) {
        log.info("Handling timeout for session [{}]", sessionId);

        CallSession session = activeSessions.get(sessionId);
        if (session == null) {
            return IvrResult.failure("SESSION_NOT_FOUND", "Session not found");
        }

        IvrFlow flow = flowRepository.getFlow(session.getFlowId());
        IvrNode currentNode = flow.getNodeById(session.getCurrentNodeId());

        int maxRetries = currentNode.getMaxRetries() > 0 ?
                currentNode.getMaxRetries() : flow.getGlobalMaxRetries();

        session.setRetryCount(session.getRetryCount() + 1);

        if (session.getRetryCount() >= maxRetries) {
            String defaultNext = currentNode.getDefaultNextNode();
            if (defaultNext != null) {
                return moveToNode(session, defaultNext);
            }
            return handleHangup(sessionId, "Timeout - max retries exceeded");
        }

        return IvrResult.timeout();
    }

    /**
     * 处理挂断
     *
     * @param sessionId 会话ID
     * @param reason    挂断原因
     * @return 执行结果
     */
    public IvrResult handleHangup(String sessionId, String reason) {
        log.info("Handling hangup for session [{}], reason: {}", sessionId, reason);

        CallSession session = activeSessions.remove(sessionId);
        if (session != null) {
            session.setState(CallSession.SessionState.HANGUP);
            session.setEndTime(LocalDateTime.now());
        }

        return IvrResult.success("Call ended: " + reason, null);
    }

    /**
     * 获取会话
     *
     * @param sessionId 会话ID
     * @return 会话对象
     */
    public CallSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    /**
     * 获取所有活跃会话数量
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * 检查会话是否存在
     */
    public boolean hasSession(String sessionId) {
        return activeSessions.containsKey(sessionId);
    }
}
