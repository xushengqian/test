package com.ivr.system.handler;

import com.ivr.system.model.IvrNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点处理器工厂
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeHandlerFactory {

    private final List<NodeHandler> handlers;
    private final Map<IvrNode.NodeType, NodeHandler> handlerMap = new HashMap<>();

    @PostConstruct
    public void init() {
        for (NodeHandler handler : handlers) {
            handlerMap.put(handler.getNodeType(), handler);
            log.info("Registered handler for node type: {}", handler.getNodeType());
        }
    }

    /**
     * 获取节点处理器
     *
     * @param nodeType 节点类型
     * @return 对应的处理器
     */
    public NodeHandler getHandler(IvrNode.NodeType nodeType) {
        return handlerMap.get(nodeType);
    }

    /**
     * 注册自定义处理器
     *
     * @param handler 处理器
     */
    public void registerHandler(NodeHandler handler) {
        handlerMap.put(handler.getNodeType(), handler);
        log.info("Registered custom handler for: {}", handler.getNodeType());
    }
}
