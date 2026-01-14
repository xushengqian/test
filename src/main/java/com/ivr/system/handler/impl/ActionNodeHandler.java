package com.ivr.system.handler.impl;

import com.ivr.system.handler.NodeHandler;
import com.ivr.system.model.*;
import com.ivr.system.service.ActionHandlerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 自定义动作节点处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionNodeHandler implements NodeHandler {

    private final ActionHandlerRegistry actionHandlerRegistry;

    @Override
    public IvrNode.NodeType getNodeType() {
        return IvrNode.NodeType.ACTION;
    }

    @Override
    public IvrResult execute(CallSession session, IvrNode node, IvrFlow flow) {
        log.info("Executing action node [{}] for session [{}]", node.getId(), session.getSessionId());

        try {
            String handlerName = node.getActionHandler();
            if (handlerName == null || handlerName.isEmpty()) {
                log.warn("No action handler specified for node: {}", node.getId());
                return IvrResult.success(node.getDefaultNextNode());
            }

            ActionHandler handler = actionHandlerRegistry.getHandler(handlerName);
            if (handler == null) {
                log.error("Action handler not found: {}", handlerName);
                return IvrResult.failure("HANDLER_NOT_FOUND", "Action handler not found: " + handlerName);
            }

            IvrResult result = handler.execute(session, node.getParams());

            if (result.isSuccess() && result.getNextNodeId() == null) {
                result = IvrResult.builder()
                        .success(true)
                        .code(result.getCode())
                        .message(result.getMessage())
                        .data(result.getData())
                        .nextNodeId(node.getDefaultNextNode())
                        .build();
            }

            return result;
        } catch (Exception e) {
            log.error("Error executing action node: {}", e.getMessage(), e);
            return IvrResult.failure("EXECUTION_ERROR", e.getMessage());
        }
    }

    /**
     * 自定义动作处理器接口
     */
    public interface ActionHandler {
        /**
         * 执行自定义动作
         *
         * @param session 会话
         * @param params  参数
         * @return 执行结果
         */
        IvrResult execute(CallSession session, java.util.Map<String, Object> params);
    }
}
