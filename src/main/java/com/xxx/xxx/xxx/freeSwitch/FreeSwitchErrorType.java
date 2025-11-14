package com.xxx.xxx.xxx.freeSwitch;

/**
 * FreeSWITCH 错误类型枚举
 */
public enum FreeSwitchErrorType {
    /**
     * 目标号码不可用或无法接通
     */
    DESTINATION_OUT_OF_ORDER("DESTINATION_OUT_OF_ORDER", "目标号码不可用"),
    
    /**
     * 用户未注册
     */
    USER_NOT_REGISTERED("USER_NOT_REGISTERED", "用户未注册"),
    
    /**
     * 无路由目标
     */
    NO_ROUTE_DESTINATION("NO_ROUTE_DESTINATION", "无路由目标"),
    
    /**
     * 未知错误
     */
    UNKNOWN_ERROR("UNKNOWN_ERROR", "未知错误");
    
    private final String code;
    private final String description;
    
    FreeSwitchErrorType(String code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
}
