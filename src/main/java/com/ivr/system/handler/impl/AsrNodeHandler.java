package com.ivr.system.handler.impl;

import com.ivr.system.esl.FreeSwitchClient;
import com.ivr.system.handler.NodeHandler;
import com.ivr.system.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 语音识别节点处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsrNodeHandler implements NodeHandler {

    private final FreeSwitchClient freeSwitchClient;

    @Override
    public IvrNode.NodeType getNodeType() {
        return IvrNode.NodeType.ASR;
    }

    @Override
    public IvrResult execute(CallSession session, IvrNode node, IvrFlow flow) {
        log.info("Executing ASR node [{}] for session [{}]", node.getId(), session.getSessionId());

        try {
            session.setState(CallSession.SessionState.WAITING_INPUT);

            if (node.getPrompt() != null && !node.getPrompt().isEmpty()) {
                if (node.isUseTts()) {
                    freeSwitchClient.speak(session.getSessionId(), node.getPrompt());
                } else {
                    freeSwitchClient.playback(session.getSessionId(), node.getPrompt());
                }
            }

            String asrEngine = "default";
            String grammar = null;
            long timeout = node.getInputTimeout() > 0 ? node.getInputTimeout() : flow.getDefaultTimeout();

            if (node.getParams() != null) {
                if (node.getParams().containsKey("asrEngine")) {
                    asrEngine = (String) node.getParams().get("asrEngine");
                }
                if (node.getParams().containsKey("grammar")) {
                    grammar = (String) node.getParams().get("grammar");
                }
            }

            freeSwitchClient.startAsrRecognition(session.getSessionId(), asrEngine, grammar, timeout);

            return IvrResult.success();
        } catch (Exception e) {
            log.error("Error executing ASR node: {}", e.getMessage(), e);
            return IvrResult.failure("EXECUTION_ERROR", e.getMessage());
        }
    }
}
