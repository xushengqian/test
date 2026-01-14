package com.ivr.system.handler.impl;

import com.ivr.system.esl.FreeSwitchClient;
import com.ivr.system.handler.NodeHandler;
import com.ivr.system.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 欢迎语节点处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WelcomeNodeHandler implements NodeHandler {

    private final FreeSwitchClient freeSwitchClient;

    @Override
    public IvrNode.NodeType getNodeType() {
        return IvrNode.NodeType.WELCOME;
    }

    @Override
    public IvrResult execute(CallSession session, IvrNode node, IvrFlow flow) {
        log.info("Executing welcome node [{}] for session [{}]", node.getId(), session.getSessionId());

        try {
            if (node.getPrompt() != null && !node.getPrompt().isEmpty()) {
                if (node.isUseTts()) {
                    freeSwitchClient.speak(session.getSessionId(), node.getPrompt());
                } else {
                    freeSwitchClient.playback(session.getSessionId(), node.getPrompt());
                }
            }

            String nextNodeId = node.getDefaultNextNode();
            if (nextNodeId != null) {
                return IvrResult.success(nextNodeId);
            }

            return IvrResult.success();
        } catch (Exception e) {
            log.error("Error executing welcome node: {}", e.getMessage(), e);
            return IvrResult.failure("EXECUTION_ERROR", e.getMessage());
        }
    }
}
