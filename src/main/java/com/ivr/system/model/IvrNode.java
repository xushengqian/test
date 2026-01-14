package com.ivr.system.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * IVR节点 - 表示IVR流程中的一个步骤
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IvrNode {

    /**
     * 节点唯一标识
     */
    private String id;

    /**
     * 节点名称
     */
    private String name;

    /**
     * 节点类型
     */
    private NodeType type;

    /**
     * 播放的语音文件或TTS文本
     */
    private String prompt;

    /**
     * 是否使用TTS（文本转语音）
     */
    private boolean useTts;

    /**
     * DTMF按键映射到下一节点
     */
    private Map<String, String> dtmfMapping;

    /**
     * 最大重试次数
     */
    private int maxRetries;

    /**
     * 输入超时时间（毫秒）
     */
    private long inputTimeout;

    /**
     * 默认下一节点（无输入或无效输入时）
     */
    private String defaultNextNode;

    /**
     * 转人工时的坐席组
     */
    private String agentGroup;

    /**
     * 转人工时的技能组
     */
    private String skillGroup;

    /**
     * 子节点列表（用于菜单类型）
     */
    private List<IvrNode> children;

    /**
     * 自定义动作处理器名称
     */
    private String actionHandler;

    /**
     * 扩展参数
     */
    private Map<String, Object> params;

    /**
     * 节点类型枚举
     */
    public enum NodeType {
        /**
         * 欢迎语节点
         */
        WELCOME,

        /**
         * 菜单节点 - 播放菜单并等待DTMF输入
         */
        MENU,

        /**
         * 播放语音节点
         */
        PLAY,

        /**
         * 收集用户输入节点
         */
        COLLECT,

        /**
         * 转人工坐席节点
         */
        TRANSFER_AGENT,

        /**
         * 转外线节点
         */
        TRANSFER_EXTERNAL,

        /**
         * 挂断节点
         */
        HANGUP,

        /**
         * 条件判断节点
         */
        CONDITION,

        /**
         * 自定义动作节点
         */
        ACTION,

        /**
         * 录音节点
         */
        RECORD,

        /**
         * 语音识别节点
         */
        ASR,

        /**
         * 队列等待节点
         */
        QUEUE
    }
}
