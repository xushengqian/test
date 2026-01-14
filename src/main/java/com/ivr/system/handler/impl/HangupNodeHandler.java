package com.ivr.system.handler.impl;

import com.ivr.system.esl.FreeSwitchClient;
import com.ivr.system.handler.NodeHandler;
import com.ivr.system.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 挂断节点处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HangupNodeHandler implements NodeHandler {

    private final FreeSwitchClient freeSwitchClient;

    @Override
    public IvrNode.NodeType getNodeType() {
        return IvrNode.NodeType.HANGUP;
    }

    @Override
    public IvrResult execute(CallSession session, IvrNode node, IvrFlow flow) {
        log.info("Executing hangup node [{}] for session [{}]", node.getId(), session.getSessionId());

        try {
            if (node.getPrompt() != null && !node.getPrompt().isEmpty()) {
                if (node.isUseTts()) {
                    freeSwitchClient.speak(session.getSessionId(), node.getPrompt());
                } else {
                    freeSwitchClient.playback(session.getSessionId(), node.getPrompt());
                }

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            session.setState(CallSession.SessionState.HANGUP);
            freeSwitchClient.hangup(session.getSessionId(), "NORMAL_CLEARING");

            return IvrResult.success("Call ended normally", null);
        } catch (Exception e) {
            log.error("Error executing hangup node: {}", e.getMessage(), e);
            return IvrResult.failure("EXECUTION_ERROR", e.getMessage());
        }
    }
}
