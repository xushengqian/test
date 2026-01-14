package com.ivr.system.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * IVR流程定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IvrFlow {

    /**
     * 流程唯一标识
     */
    private String id;

    /**
     * 流程名称
     */
    private String name;

    /**
     * 流程描述
     */
    private String description;

    /**
     * 流程版本
     */
    private String version;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 入口节点ID
     */
    private String entryNodeId;

    /**
     * 流程中的所有节点
     */
    private List<IvrNode> nodes;

    /**
     * 节点映射（ID -> 节点）
     */
    private Map<String, IvrNode> nodeMap;

    /**
     * 全局默认超时时间（毫秒）
     */
    private long defaultTimeout;

    /**
     * 全局最大重试次数
     */
    private int globalMaxRetries;

    /**
     * 无效输入时播放的提示
     */
    private String invalidInputPrompt;

    /**
     * 超时时播放的提示
     */
    private String timeoutPrompt;

    /**
     * 流程扩展参数
     */
    private Map<String, Object> params;

    /**
     * 根据ID获取节点
     */
    public IvrNode getNodeById(String nodeId) {
        if (nodeMap != null) {
            return nodeMap.get(nodeId);
        }
        if (nodes != null) {
            return nodes.stream()
                    .filter(n -> n.getId().equals(nodeId))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }
}
