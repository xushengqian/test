package com.xxx.xxx.xxx.freeswitch;

/**
 * FreeSWITCH 错误类型枚举
 */
public enum FreeSwitchErrorType {
    
    /**
     * 目标不可用错误
     * 通常表示目标号码不可用、网关无法连接或路由失败
     */
    DESTINATION_OUT_OF_ORDER("-ERR DESTINATION_OUT_OF_ORDER"),
    
    /**
     * 未知错误类型
     */
    UNKNOWN_ERROR(null);
    
    private final String errorMessage;
    
    FreeSwitchErrorType(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    /**
     * 根据错误消息字符串判断错误类型
     * 
     * @param errorMessage 错误消息
     * @return 错误类型枚举
     */
    public static FreeSwitchErrorType fromErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return UNKNOWN_ERROR;
        }
        
        for (FreeSwitchErrorType errorType : values()) {
            if (errorType.errorMessage != null && errorMessage.contains(errorType.errorMessage)) {
                return errorType;
            }
        }
        
        return UNKNOWN_ERROR;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
}
