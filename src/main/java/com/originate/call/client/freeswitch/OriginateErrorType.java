package com.originate.call.client.freeswitch;

/**
 * FreeSWITCH Originate错误类型枚举
 */
public enum OriginateErrorType {
    
    /**
     * 目标不可达/无法接通
     */
    DESTINATION_OUT_OF_ORDER("DESTINATION_OUT_OF_ORDER", "目标不可达", true),
    
    /**
     * 用户忙
     */
    USER_BUSY("USER_BUSY", "用户忙", true),
    
    /**
     * 无应答
     */
    NO_ANSWER("NO_ANSWER", "无应答", true),
    
    /**
     * 呼叫被拒绝
     */
    CALL_REJECTED("CALL_REJECTED", "呼叫被拒绝", false),
    
    /**
     * 号码不存在
     */
    UNALLOCATED_NUMBER("UNALLOCATED_NUMBER", "号码不存在", false),
    
    /**
     * 正常挂断
     */
    NORMAL_CLEARING("NORMAL_CLEARING", "正常挂断", false),
    
    /**
     * 超时
     */
    ORIGINATOR_CANCEL("ORIGINATOR_CANCEL", "发起方取消", false),
    
    /**
     * 网关不可用
     */
    GATEWAY_DOWN("GATEWAY_DOWN", "网关不可用", true),
    
    /**
     * 正常呼叫进行中被挂断
     */
    NORMAL_TEMPORARY_FAILURE("NORMAL_TEMPORARY_FAILURE", "临时失败", true),
    
    /**
     * 未知错误
     */
    UNKNOWN("UNKNOWN", "未知错误", false);
    
    private final String errorCode;
    private final String description;
    private final boolean retryable;
    
    OriginateErrorType(String errorCode, String description, boolean retryable) {
        this.errorCode = errorCode;
        this.description = description;
        this.retryable = retryable;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
    
    /**
     * 根据错误代码获取错误类型
     */
    public static OriginateErrorType fromErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isEmpty()) {
            return UNKNOWN;
        }
        
        for (OriginateErrorType type : values()) {
            if (type.errorCode.equals(errorCode)) {
                return type;
            }
        }
        
        return UNKNOWN;
    }
    
    /**
     * 从错误消息中提取错误代码
     * 例如: "-ERR DESTINATION_OUT_OF_ORDER" -> "DESTINATION_OUT_OF_ORDER"
     */
    public static OriginateErrorType fromErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return UNKNOWN;
        }
        
        String trimmed = errorMessage.trim();
        if (trimmed.startsWith("-ERR ")) {
            String errorCode = trimmed.substring(5).trim();
            return fromErrorCode(errorCode);
        }
        
        return fromErrorCode(trimmed);
    }
}
