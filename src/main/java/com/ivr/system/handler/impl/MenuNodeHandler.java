package com.ivr.system.handler.impl;

import com.ivr.system.esl.FreeSwitchClient;
import com.ivr.system.handler.NodeHandler;
import com.ivr.system.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 菜单节点处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MenuNodeHandler implements NodeHandler {

    private final FreeSwitchClient freeSwitchClient;

    @Override
    public IvrNode.NodeType getNodeType() {
        return IvrNode.NodeType.MENU;
    }

    @Override
    public IvrResult execute(CallSession session, IvrNode node, IvrFlow flow) {
        log.info("Executing menu node [{}] for session [{}]", node.getId(), session.getSessionId());

        try {
            session.setState(CallSession.SessionState.WAITING_INPUT);

            if (node.getPrompt() != null && !node.getPrompt().isEmpty()) {
                if (node.isUseTts()) {
                    freeSwitchClient.speak(session.getSessionId(), node.getPrompt());
                } else {
                    freeSwitchClient.playback(session.getSessionId(), node.getPrompt());
                }
            }

            long timeout = node.getInputTimeout() > 0 ?
                    node.getInputTimeout() : flow.getDefaultTimeout();
            freeSwitchClient.startDtmfCollection(session.getSessionId(), 1, timeout);

            return IvrResult.success();
        } catch (Exception e) {
            log.error("Error executing menu node: {}", e.getMessage(), e);
            return IvrResult.failure("EXECUTION_ERROR", e.getMessage());
        }
    }
}
