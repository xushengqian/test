package com.originate.call.client.freeswitch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FreeSWITCH Originate错误处理器
 * 用于处理后台任务返回的错误结果
 */
public class OriginateErrorHandler {
    
    private static final Logger log = LoggerFactory.getLogger(OriginateErrorHandler.class);
    
    // 错误统计
    private final Map<String, Integer> errorStatistics = new ConcurrentHashMap<>();
    
    // 重试配置
    private int maxRetryAttempts = 3;
    private long retryDelayMillis = 2000;
    
    /**
     * 处理originate错误
     * 
     * @param jobUuid 任务UUID
     * @param errorBody 错误消息体
     * @param eventHeaders 事件头信息
     * @return 处理结果
     */
    public OriginateErrorResult handleOriginateError(
            String jobUuid, 
            String errorBody,
            Map<String, String> eventHeaders) {
        
        log.info("处理originate错误 - jobUuid: {}, errorBody: {}", jobUuid, errorBody);
        
        // 解析错误类型
        OriginateErrorType errorType = OriginateErrorType.fromErrorMessage(errorBody);
        
        // 记录错误统计
        recordErrorStatistics(errorType);
        
        // 提取呼叫参数
        String calledNumber = extractCalledNumber(eventHeaders);
        String gateway = extractGateway(eventHeaders);
        String callUuid = eventHeaders.get("uuid");
        
        log.warn("Originate失败 - 错误类型: {}, 描述: {}, 被叫号码: {}, 网关: {}, callUuid: {}", 
                errorType.getErrorCode(), 
                errorType.getDescription(), 
                calledNumber, 
                gateway,
                callUuid);
        
        // 构建处理结果
        OriginateErrorResult result = new OriginateErrorResult();
        result.setJobUuid(jobUuid);
        result.setCallUuid(callUuid);
        result.setErrorType(errorType);
        result.setErrorMessage(errorBody);
        result.setCalledNumber(calledNumber);
        result.setGateway(gateway);
        result.setRetryable(errorType.isRetryable());
        
        // 根据错误类型决定是否需要重试
        if (errorType.isRetryable()) {
            result.setSuggestedAction("建议重试，可能是临时性网络问题");
            log.info("错误类型 {} 可以重试", errorType.getErrorCode());
        } else {
            result.setSuggestedAction("不建议重试，可能是号码或配置问题");
            log.info("错误类型 {} 不建议重试", errorType.getErrorCode());
        }
        
        // 特殊错误处理
        handleSpecialErrorTypes(errorType, result);
        
        return result;
    }
    
    /**
     * 处理特殊错误类型
     */
    private void handleSpecialErrorTypes(OriginateErrorType errorType, OriginateErrorResult result) {
        switch (errorType) {
            case DESTINATION_OUT_OF_ORDER:
                // 目标不可达，可能是网络问题或号码关机
                result.addDetail("检查项", "1.确认被叫号码是否正常 2.检查网络连接 3.尝试其他网关");
                break;
                
            case GATEWAY_DOWN:
                // 网关故障
                result.addDetail("检查项", "网关状态异常，需要检查FreeSWITCH网关配置和连接");
                break;
                
            case UNALLOCATED_NUMBER:
                // 号码不存在
                result.addDetail("检查项", "号码不存在，请验证号码格式和有效性");
                result.setRetryable(false);
                break;
                
            case USER_BUSY:
                // 用户忙
                result.addDetail("检查项", "用户忙，可稍后重试");
                break;
                
            case NO_ANSWER:
                // 无应答
                result.addDetail("检查项", "无应答，用户未接听");
                break;
                
            default:
                break;
        }
    }
    
    /**
     * 提取被叫号码
     */
    private String extractCalledNumber(Map<String, String> eventHeaders) {
        String jobCommandArg = eventHeaders.get("Job-Command-Arg");
        if (jobCommandArg != null) {
            // 从Job-Command-Arg中提取号码
            // 格式类似: {params}sofia/gateway/gwname/13390118999 &lua(...)
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
        return "unknown";
    }
    
    /**
     * 提取网关名称
     */
    private String extractGateway(Map<String, String> eventHeaders) {
        String jobCommandArg = eventHeaders.get("Job-Command-Arg");
        if (jobCommandArg != null) {
            // 从Job-Command-Arg中提取网关名
            if (jobCommandArg.contains("sofia/gateway/")) {
                int startIndex = jobCommandArg.indexOf("sofia/gateway/") + 14;
                int endIndex = jobCommandArg.indexOf("/", startIndex);
                if (endIndex > startIndex) {
                    return jobCommandArg.substring(startIndex, endIndex);
                }
            }
        }
        return "unknown";
    }
    
    /**
     * 记录错误统计
     */
    private void recordErrorStatistics(OriginateErrorType errorType) {
        String key = errorType.getErrorCode();
        errorStatistics.merge(key, 1, Integer::sum);
        
        // 定期输出统计信息
        if (errorStatistics.get(key) % 10 == 0) {
            log.info("错误统计 - {}: {} 次", errorType.getDescription(), errorStatistics.get(key));
        }
    }
    
    /**
     * 获取错误统计
     */
    public Map<String, Integer> getErrorStatistics() {
        return new ConcurrentHashMap<>(errorStatistics);
    }
    
    /**
     * 清空错误统计
     */
    public void clearErrorStatistics() {
        errorStatistics.clear();
        log.info("错误统计已清空");
    }
    
    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }
    
    public void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }
    
    public long getRetryDelayMillis() {
        return retryDelayMillis;
    }
    
    public void setRetryDelayMillis(long retryDelayMillis) {
        this.retryDelayMillis = retryDelayMillis;
    }
}
