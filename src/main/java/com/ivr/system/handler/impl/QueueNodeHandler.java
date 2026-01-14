package com.ivr.system.handler.impl;

import com.ivr.system.esl.FreeSwitchClient;
import com.ivr.system.handler.NodeHandler;
import com.ivr.system.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 队列等待节点处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueNodeHandler implements NodeHandler {

    private final FreeSwitchClient freeSwitchClient;

    @Override
    public IvrNode.NodeType getNodeType() {
        return IvrNode.NodeType.QUEUE;
    }

    @Override
    public IvrResult execute(CallSession session, IvrNode node, IvrFlow flow) {
        log.info("Executing queue node [{}] for session [{}]", node.getId(), session.getSessionId());

        try {
            session.setState(CallSession.SessionState.IN_QUEUE);

            if (node.getPrompt() != null && !node.getPrompt().isEmpty()) {
                if (node.isUseTts()) {
                    freeSwitchClient.speak(session.getSessionId(), node.getPrompt());
                } else {
                    freeSwitchClient.playback(session.getSessionId(), node.getPrompt());
                }
            }

            String queueName = "default";
            if (node.getParams() != null && node.getParams().containsKey("queueName")) {
                queueName = (String) node.getParams().get("queueName");
            }

            String holdMusic = null;
            if (node.getParams() != null && node.getParams().containsKey("holdMusic")) {
                holdMusic = (String) node.getParams().get("holdMusic");
            }

            freeSwitchClient.enterQueue(session.getSessionId(), queueName, holdMusic);

            return IvrResult.success();
        } catch (Exception e) {
            log.error("Error executing queue node: {}", e.getMessage(), e);
            return IvrResult.failure("EXECUTION_ERROR", e.getMessage());
        }
    }
}
