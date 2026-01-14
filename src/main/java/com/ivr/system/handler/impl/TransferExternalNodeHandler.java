package com.ivr.system.handler.impl;

import com.ivr.system.esl.FreeSwitchClient;
import com.ivr.system.handler.NodeHandler;
import com.ivr.system.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 转外线节点处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferExternalNodeHandler implements NodeHandler {

    private final FreeSwitchClient freeSwitchClient;

    @Override
    public IvrNode.NodeType getNodeType() {
        return IvrNode.NodeType.TRANSFER_EXTERNAL;
    }

    @Override
    public IvrResult execute(CallSession session, IvrNode node, IvrFlow flow) {
        log.info("Executing transfer-external node [{}] for session [{}]", node.getId(), session.getSessionId());

        try {
            session.setState(CallSession.SessionState.TRANSFERRING);

            if (node.getPrompt() != null && !node.getPrompt().isEmpty()) {
                if (node.isUseTts()) {
                    freeSwitchClient.speak(session.getSessionId(), node.getPrompt());
                } else {
                    freeSwitchClient.playback(session.getSessionId(), node.getPrompt());
                }
            }

            String destination = null;
            String gateway = "default";

            if (node.getParams() != null) {
                destination = (String) node.getParams().get("destination");
                if (node.getParams().containsKey("gateway")) {
                    gateway = (String) node.getParams().get("gateway");
                }
            }

            if (destination == null || destination.isEmpty()) {
                log.error("No destination specified for transfer-external node");
                return IvrResult.failure("INVALID_CONFIG", "No destination specified");
            }

            log.info("Transferring session [{}] to external number [{}] via gateway [{}]",
                    session.getSessionId(), destination, gateway);

            boolean success = freeSwitchClient.transferToExternal(
                    session.getSessionId(),
                    destination,
                    gateway
            );

            if (success) {
                return IvrResult.success("Transfer initiated", null);
            } else {
                String fallbackNode = node.getDefaultNextNode();
                if (fallbackNode != null) {
                    return IvrResult.success(fallbackNode);
                }
                return IvrResult.failure("TRANSFER_FAILED", "Failed to transfer to external number");
            }
        } catch (Exception e) {
            log.error("Error executing transfer-external node: {}", e.getMessage(), e);
            return IvrResult.failure("EXECUTION_ERROR", e.getMessage());
        }
    }
}
