package com.ivr.system.handler;

import com.ivr.system.model.*;

/**
 * 节点处理器接口 - 处理不同类型的IVR节点
 */
public interface NodeHandler {

    /**
     * 获取支持的节点类型
     *
     * @return 节点类型
     */
    IvrNode.NodeType getNodeType();

    /**
     * 执行节点
     *
     * @param session 呼叫会话
     * @param node    当前节点
     * @param flow    所属流程
     * @return 执行结果
     */
    IvrResult execute(CallSession session, IvrNode node, IvrFlow flow);
}
