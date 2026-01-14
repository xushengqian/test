package com.ivr.system.handler.impl;

import com.ivr.system.esl.FreeSwitchClient;
import com.ivr.system.handler.NodeHandler;
import com.ivr.system.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 录音节点处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordNodeHandler implements NodeHandler {

    private final FreeSwitchClient freeSwitchClient;

    @Override
    public IvrNode.NodeType getNodeType() {
        return IvrNode.NodeType.RECORD;
    }

    @Override
    public IvrResult execute(CallSession session, IvrNode node, IvrFlow flow) {
        log.info("Executing record node [{}] for session [{}]", node.getId(), session.getSessionId());

        try {
            if (node.getPrompt() != null && !node.getPrompt().isEmpty()) {
                if (node.isUseTts()) {
                    freeSwitchClient.speak(session.getSessionId(), node.getPrompt());
                } else {
                    freeSwitchClient.playback(session.getSessionId(), node.getPrompt());
                }
            }

            int maxDuration = 60;
            int silenceThreshold = 3;
            String filePath = "/tmp/recordings/" + session.getSessionId() + ".wav";

            if (node.getParams() != null) {
                if (node.getParams().containsKey("maxDuration")) {
                    maxDuration = (Integer) node.getParams().get("maxDuration");
                }
                if (node.getParams().containsKey("silenceThreshold")) {
                    silenceThreshold = (Integer) node.getParams().get("silenceThreshold");
                }
                if (node.getParams().containsKey("filePath")) {
                    filePath = (String) node.getParams().get("filePath");
                }
            }

            freeSwitchClient.startRecording(session.getSessionId(), filePath, maxDuration, silenceThreshold);

            session.setVariable("record_file", filePath);

            String nextNodeId = node.getDefaultNextNode();
            if (nextNodeId != null) {
                return IvrResult.success(nextNodeId);
            }

            return IvrResult.success();
        } catch (Exception e) {
            log.error("Error executing record node: {}", e.getMessage(), e);
            return IvrResult.failure("EXECUTION_ERROR", e.getMessage());
        }
    }
}
