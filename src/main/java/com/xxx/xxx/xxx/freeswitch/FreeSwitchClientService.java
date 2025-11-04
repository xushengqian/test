package com.xxx.xxx.xxx.freeswitch;

import org.freeswitch.esl.client.transport.event.EslEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

/**
 * FreeSWITCH 客户端服务类
 * 处理 FreeSWITCH ESL 事件和 originate 命令的错误
 */
public class FreeSwitchClientService {
    
    private static final Logger logger = LoggerFactory.getLogger(FreeSwitchClientService.class);
    
    /**
     * 处理后台任务结果
     * 
     * @param jobUuid 任务 UUID
     * @param event ESL 事件对象
     */
    public void backgroundJobResultReceived(String jobUuid, EslEvent event) {
        logger.info("backgroundJobResultReceived :{},event:{}", jobUuid, event);
        
        // 获取事件体
        List<String> eventBody = event.getEventBodyLines();
        if (eventBody == null || eventBody.isEmpty()) {
            logger.warn("Event body is empty for job UUID: {}", jobUuid);
            return;
        }
        
        // 检查错误信息
        String eventBodyContent = String.join(" ", eventBody);
        if (eventBodyContent.startsWith("-ERR")) {
            handleOriginateError(jobUuid, event, eventBodyContent);
        } else {
            logger.debug("Background job {} completed successfully", jobUuid);
        }
    }
    
    /**
     * 处理 originate 命令的错误
     * 
     * @param jobUuid 任务 UUID
     * @param event ESL 事件对象
     * @param errorMessage 错误消息
     */
    private void handleOriginateError(String jobUuid, EslEvent event, String errorMessage) {
        logger.error("FreeSWITCH originate error for job UUID: {}, error: {}", jobUuid, errorMessage);
        
        // 提取错误类型
        FreeSwitchErrorType errorType = FreeSwitchErrorType.fromErrorMessage(errorMessage);
        
        // 获取任务命令参数，用于错误上下文
        String jobCommandArg = event.getEventHeaders().get("Job-Command-Arg");
        
        switch (errorType) {
            case DESTINATION_OUT_OF_ORDER:
                handleDestinationOutOfOrderError(jobUuid, jobCommandArg, errorMessage);
                break;
            case UNKNOWN_ERROR:
            default:
                handleUnknownError(jobUuid, jobCommandArg, errorMessage);
                break;
        }
    }
    
    /**
     * 处理 DESTINATION_OUT_OF_ORDER 错误
     * 该错误通常表示目标号码不可用或网关无法连接
     * 
     * @param jobUuid 任务 UUID
     * @param jobCommandArg 命令参数
     * @param errorMessage 错误消息
     */
    private void handleDestinationOutOfOrderError(String jobUuid, String jobCommandArg, String errorMessage) {
        logger.error("DESTINATION_OUT_OF_ORDER error detected. Job UUID: {}, Command Args: {}, Error: {}", 
                    jobUuid, jobCommandArg, errorMessage);
        
        // 提取目标号码和网关信息（如果存在）
        String destination = extractDestination(jobCommandArg);
        String gateway = extractGateway(jobCommandArg);
        
        logger.error("Failed to originate call - Destination: {}, Gateway: {}, Job UUID: {}", 
                    destination, gateway, jobUuid);
        
        // TODO: 在这里添加业务逻辑处理
        // 例如：
        // - 记录到数据库
        // - 发送告警通知
        // - 重试机制
        // - 更新呼叫状态
    }
    
    /**
     * 处理未知错误
     * 
     * @param jobUuid 任务 UUID
     * @param jobCommandArg 命令参数
     * @param errorMessage 错误消息
     */
    private void handleUnknownError(String jobUuid, String jobCommandArg, String errorMessage) {
        logger.error("Unknown FreeSWITCH error. Job UUID: {}, Command Args: {}, Error: {}", 
                    jobUuid, jobCommandArg, errorMessage);
        
        // TODO: 添加通用错误处理逻辑
    }
    
    /**
     * 从命令参数中提取目标号码
     * 
     * @param jobCommandArg 命令参数
     * @return 目标号码
     */
    private String extractDestination(String jobCommandArg) {
        if (jobCommandArg == null) {
            return "unknown";
        }
        
        // 从参数中提取号码，例如：sofia/gateway/gwopensips/13390118999
        // 简单提取，可根据实际格式调整
        try {
            String[] parts = jobCommandArg.split("/");
            if (parts.length > 0) {
                // 查找电话号码部分
                for (String part : parts) {
                    if (part.matches("\\d+")) {
                        return part;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to extract destination from command args: {}", jobCommandArg, e);
        }
        
        return "unknown";
    }
    
    /**
     * 从命令参数中提取网关信息
     * 
     * @param jobCommandArg 命令参数
     * @return 网关名称
     */
    private String extractGateway(String jobCommandArg) {
        if (jobCommandArg == null) {
            return "unknown";
        }
        
        // 从参数中提取网关，例如：sofia/gateway/gwopensips/13390118999
        try {
            if (jobCommandArg.contains("gateway/")) {
                String[] parts = jobCommandArg.split("/");
                for (int i = 0; i < parts.length; i++) {
                    if ("gateway".equals(parts[i]) && i + 1 < parts.length) {
                        return parts[i + 1];
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to extract gateway from command args: {}", jobCommandArg, e);
        }
        
        return "unknown";
    }
}
