package com.originate.call.client.freeswitch;

import java.util.HashMap;
import java.util.Map;

/**
 * FreeSWITCH事件解析器
 * 用于解析日志中的事件数据
 */
public class FreeSwitchEventParser {
    
    /**
     * 从日志JSON中解析事件头
     * 
     * 示例日志格式:
     * {"eventHeaders":{"Event-Name":"BACKGROUND_JOB","Job-UUID":"xxx",...}}
     */
    public static Map<String, String> parseEventHeaders(Map<String, Object> eventData) {
        Map<String, String> headers = new HashMap<>();
        
        if (eventData == null) {
            return headers;
        }
        
        Object eventHeadersObj = eventData.get("eventHeaders");
        if (eventHeadersObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> eventHeaders = (Map<String, Object>) eventHeadersObj;
            
            for (Map.Entry<String, Object> entry : eventHeaders.entrySet()) {
                if (entry.getValue() != null) {
                    headers.put(entry.getKey(), entry.getValue().toString());
                }
            }
        }
        
        return headers;
    }
    
    /**
     * 从日志JSON中解析事件体
     * 
     * 示例: {"eventBody":["-ERR DESTINATION_OUT_OF_ORDER"]}
     */
    public static String parseEventBody(Map<String, Object> eventData) {
        if (eventData == null) {
            return "";
        }
        
        Object eventBodyObj = eventData.get("eventBody");
        if (eventBodyObj instanceof String) {
            return (String) eventBodyObj;
        } else if (eventBodyObj instanceof Object[]) {
            Object[] bodyArray = (Object[]) eventBodyObj;
            if (bodyArray.length > 0 && bodyArray[0] != null) {
                return bodyArray[0].toString();
            }
        }
        
        return "";
    }
    
    /**
     * 从事件头中提取Job UUID
     */
    public static String extractJobUuid(Map<String, String> eventHeaders) {
        return eventHeaders.getOrDefault("Job-UUID", "");
    }
    
    /**
     * 从事件头中提取Call UUID
     */
    public static String extractCallUuid(Map<String, String> eventHeaders) {
        // 首先尝试从Job-Command-Arg中提取uuid参数
        String jobCommandArg = eventHeaders.get("Job-Command-Arg");
        if (jobCommandArg != null) {
            int uuidStart = jobCommandArg.indexOf("uuid='");
            if (uuidStart >= 0) {
                uuidStart += 6; // 跳过 "uuid='"
                int uuidEnd = jobCommandArg.indexOf("'", uuidStart);
                if (uuidEnd > uuidStart) {
                    return jobCommandArg.substring(uuidStart, uuidEnd);
                }
            }
        }
        
        // 如果没有找到，返回Core-UUID
        return eventHeaders.getOrDefault("Core-UUID", "");
    }
    
    /**
     * 从事件头中提取被叫号码
     */
    public static String extractCalledNumber(Map<String, String> eventHeaders) {
        String jobCommandArg = eventHeaders.get("Job-Command-Arg");
        if (jobCommandArg != null) {
            // 从Job-Command-Arg中提取号码
            // 格式: {params}sofia/gateway/gwname/NUMBER &lua(...)
            String[] parts = jobCommandArg.split("\\s+");
            for (String part : parts) {
                if (part.contains("sofia/gateway/")) {
                    String[] segments = part.split("/");
                    if (segments.length >= 4) {
                        return segments[3];
                    }
                }
            }
        }
        return "";
    }
    
    /**
     * 从事件头中提取网关名称
     */
    public static String extractGateway(Map<String, String> eventHeaders) {
        String jobCommandArg = eventHeaders.get("Job-Command-Arg");
        if (jobCommandArg != null) {
            if (jobCommandArg.contains("sofia/gateway/")) {
                int startIndex = jobCommandArg.indexOf("sofia/gateway/") + 14;
                int endIndex = jobCommandArg.indexOf("/", startIndex);
                if (endIndex > startIndex) {
                    return jobCommandArg.substring(startIndex, endIndex);
                }
            }
        }
        return "";
    }
    
    /**
     * 从事件头中提取主叫号码
     */
    public static String extractCallingNumber(Map<String, String> eventHeaders) {
        String jobCommandArg = eventHeaders.get("Job-Command-Arg");
        if (jobCommandArg != null) {
            int callerIdStart = jobCommandArg.indexOf("origination_caller_id_number='");
            if (callerIdStart >= 0) {
                callerIdStart += 31; // 跳过 "origination_caller_id_number='"
                int callerIdEnd = jobCommandArg.indexOf("'", callerIdStart);
                if (callerIdEnd > callerIdStart) {
                    return jobCommandArg.substring(callerIdStart, callerIdEnd);
                }
            }
        }
        return "";
    }
}
