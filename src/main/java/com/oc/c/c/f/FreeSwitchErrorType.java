package com.oc.c.c.f;

import java.util.Arrays;
import java.util.Optional;

/**
 * FreeSWITCH 错误类型枚举
 * 用于分类和处理不同类型的 FreeSWITCH 错误
 */
public enum FreeSwitchErrorType {
    
    /**
     * 目标号码不可用或线路故障
     * 错误格式: -ERR DESTINATION_OUT_OF_ORDER
     */
    DESTINATION_OUT_OF_ORDER("DESTINATION_OUT_OF_ORDER"),
    
    /**
     * 用户不存在
     */
    USER_NOT_FOUND("USER_NOT_FOUND"),
    
    /**
     * 无效的命令
     */
    INVALID_COMMAND("INVALID_COMMAND"),
    
    /**
     * 网关错误
     */
    GATEWAY_ERROR("GATEWAY_ERROR"),
    
    /**
     * 未知错误
     */
    UNKNOWN_ERROR("UNKNOWN");
    
    private final String errorCode;
    
    FreeSwitchErrorType(String errorCode) {
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * 从错误消息中解析错误类型
     * 
     * @param errorMessage 错误消息，格式如: "-ERR DESTINATION_OUT_OF_ORDER"
     * @return 对应的错误类型，如果无法识别则返回 UNKNOWN_ERROR
     */
    public static FreeSwitchErrorType parseError(String errorMessage) {
        if (errorMessage == null || !errorMessage.startsWith("-ERR")) {
            return UNKNOWN_ERROR;
        }
        
        // 提取错误代码部分
        String errorCode = errorMessage.substring(4).trim();
        
        // 查找匹配的错误类型
        Optional<FreeSwitchErrorType> matchedType = Arrays.stream(values())
            .filter(type -> !type.equals(UNKNOWN_ERROR))
            .filter(type -> errorCode.equals(type.errorCode) || errorCode.contains(type.errorCode))
            .findFirst();
        
        return matchedType.orElse(UNKNOWN_ERROR);
    }
}
