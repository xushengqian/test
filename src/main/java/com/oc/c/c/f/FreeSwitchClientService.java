package com.oc.c.c.f;

import org.freeswitch.esl.client.transport.event.EslEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FreeSWITCH 客户端服务类
 * 处理 FreeSWITCH 事件，特别是 BACKGROUND_JOB 事件的错误处理
 */
public class FreeSwitchClientService {
    
    private static final Logger logger = LoggerFactory.getLogger(FreeSwitchClientService.class);
    
    /**
     * 处理后台作业结果
     * 
     * @param jobUuid 作业 UUID
     * @param event ESL 事件对象
     */
    public void backgroundJobResultReceived(String jobUuid, EslEvent event) {
        logger.info("backgroundJobResultReceived :{},event:{}", jobUuid, event);
        
        // 获取事件体内容
        String eventBody = extractEventBody(event);
        
        if (eventBody == null || eventBody.trim().isEmpty()) {
            logger.warn("Job {} has empty event body", jobUuid);
            return;
        }
        
        // 检查是否是错误响应
        if (eventBody.startsWith("-ERR")) {
            handleOriginateError(jobUuid, eventBody, event);
        } else {
            logger.info("Job {} completed successfully: {}", jobUuid, eventBody);
        }
    }
    
    /**
     * 提取事件体内容
     */
    private String extractEventBody(EslEvent event) {
        if (event == null || event.getEventBody() == null) {
            return null;
        }
        
        // 事件体可能是字符串数组
        var eventBody = event.getEventBody();
        if (eventBody.length > 0) {
            return eventBody[0];
        }
        
        return null;
    }
    
    /**
     * 处理 originate 命令的错误
     * 
     * @param jobUuid 作业 UUID
     * @param errorMessage 错误消息
     * @param event 原始事件对象
     */
    private void handleOriginateError(String jobUuid, String errorMessage, EslEvent event) {
        // 解析错误类型
        FreeSwitchErrorType errorType = FreeSwitchErrorType.parseError(errorMessage);
        
        logger.error("Job {} failed with error: {} - {}", jobUuid, errorType, errorMessage);
        
        // 根据错误类型进行不同的处理
        switch (errorType) {
            case DESTINATION_OUT_OF_ORDER:
                handleDestinationOutOfOrderError(jobUuid, errorMessage, event);
                break;
            case UNKNOWN_ERROR:
            default:
                handleGenericError(jobUuid, errorMessage, event);
                break;
        }
    }
    
    /**
     * 处理 DESTINATION_OUT_OF_ORDER 错误
     * 这个错误通常表示目标号码不可用或线路故障
     */
    private void handleDestinationOutOfOrderError(String jobUuid, String errorMessage, EslEvent event) {
        logger.warn("Destination out of order for job {}. This may indicate:", jobUuid);
        logger.warn("  - The destination number is not available");
        logger.warn("  - Network connectivity issues");
        logger.warn("  - Gateway configuration problems");
        
        // 获取作业命令参数以获取更多上下文
        String jobCommandArg = event.getEventHeader("Job-Command-Arg");
        if (jobCommandArg != null) {
            logger.debug("Job command argument: {}", jobCommandArg);
            
            // 可以在这里提取目标号码等信息
            // 例如：从 jobCommandArg 中解析目标号码
            // 然后可以记录到数据库、发送告警等
        }
        
        // TODO: 实现具体的错误处理逻辑，例如：
        // - 记录错误到数据库
        // - 发送告警通知
        // - 重试机制
        // - 更新呼叫状态等
    }
    
    /**
     * 处理通用错误
     */
    private void handleGenericError(String jobUuid, String errorMessage, EslEvent event) {
        logger.error("Unhandled error for job {}: {}", jobUuid, errorMessage);
        // TODO: 实现通用错误处理逻辑
    }
}
