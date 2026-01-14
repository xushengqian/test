package com.ivr.system.handler.impl;

import com.ivr.system.esl.FreeSwitchClient;
import com.ivr.system.handler.NodeHandler;
import com.ivr.system.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 转人工坐席节点处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferAgentNodeHandler implements NodeHandler {

    private final FreeSwitchClient freeSwitchClient;

    @Override
    public IvrNode.NodeType getNodeType() {
        return IvrNode.NodeType.TRANSFER_AGENT;
    }

    @Override
    public IvrResult execute(CallSession session, IvrNode node, IvrFlow flow) {
        log.info("Executing transfer-to-agent node [{}] for session [{}]", node.getId(), session.getSessionId());

        try {
            session.setState(CallSession.SessionState.TRANSFERRING);

            if (node.getPrompt() != null && !node.getPrompt().isEmpty()) {
                if (node.isUseTts()) {
                    freeSwitchClient.speak(session.getSessionId(), node.getPrompt());
                } else {
                    freeSwitchClient.playback(session.getSessionId(), node.getPrompt());
                }
            }

            String agentGroup = node.getAgentGroup();
            String skillGroup = node.getSkillGroup();

            log.info("Transferring session [{}] to agent group [{}], skill [{}]",
                    session.getSessionId(), agentGroup, skillGroup);

            boolean success = freeSwitchClient.transferToAgent(
                    session.getSessionId(),
                    agentGroup,
                    skillGroup
            );

            if (success) {
                session.setState(CallSession.SessionState.IN_QUEUE);
                return IvrResult.success("Transfer initiated", null);
            } else {
                String fallbackNode = node.getDefaultNextNode();
                if (fallbackNode != null) {
                    return IvrResult.success(fallbackNode);
                }
                return IvrResult.failure("TRANSFER_FAILED", "Failed to transfer to agent");
            }
        } catch (Exception e) {
            log.error("Error executing transfer node: {}", e.getMessage(), e);
            return IvrResult.failure("EXECUTION_ERROR", e.getMessage());
        }
    }
}
