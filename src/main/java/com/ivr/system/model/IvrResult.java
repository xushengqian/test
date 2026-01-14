package com.ivr.system.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IVR操作结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IvrResult {

    /**
     * 操作是否成功
     */
    private boolean success;

    /**
     * 结果消息
     */
    private String message;

    /**
     * 结果代码
     */
    private String code;

    /**
     * 下一个节点ID
     */
    private String nextNodeId;

    /**
     * 结果数据
     */
    private Object data;

    /**
     * 创建成功结果
     */
    public static IvrResult success() {
        return IvrResult.builder()
                .success(true)
                .code("SUCCESS")
                .build();
    }

    /**
     * 创建成功结果（带下一节点）
     */
    public static IvrResult success(String nextNodeId) {
        return IvrResult.builder()
                .success(true)
                .code("SUCCESS")
                .nextNodeId(nextNodeId)
                .build();
    }

    /**
     * 创建成功结果（带消息和数据）
     */
    public static IvrResult success(String message, Object data) {
        return IvrResult.builder()
                .success(true)
                .code("SUCCESS")
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static IvrResult failure(String message) {
        return IvrResult.builder()
                .success(false)
                .code("FAILURE")
                .message(message)
                .build();
    }

    /**
     * 创建失败结果（带代码）
     */
    public static IvrResult failure(String code, String message) {
        return IvrResult.builder()
                .success(false)
                .code(code)
                .message(message)
                .build();
    }

    /**
     * 创建需要重试的结果
     */
    public static IvrResult retry(String message) {
        return IvrResult.builder()
                .success(false)
                .code("RETRY")
                .message(message)
                .build();
    }

    /**
     * 创建超时结果
     */
    public static IvrResult timeout() {
        return IvrResult.builder()
                .success(false)
                .code("TIMEOUT")
                .message("Input timeout")
                .build();
    }
}
