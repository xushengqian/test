package com.xxx.xxx.xxx.freeSwitch;

import org.freeswitch.esl.client.transport.event.EslEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * FreeSWITCH 客户端服务类
 * 处理 FreeSWITCH ESL 事件和后台作业结果
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
        
        // 获取作业命令
        String jobCommand = event.getEventHeaders().get("Job-Command");
        if (!"originate".equals(jobCommand)) {
            logger.debug("忽略非 originate 命令的作业结果: {}", jobCommand);
            return;
        }
        
        // 获取事件体内容
        List<String> eventBody = event.getEventBody();
        if (eventBody == null || eventBody.isEmpty()) {
            logger.warn("作业 {} 的事件体为空", jobUuid);
            return;
        }
        
        // 检查是否包含错误信息
        String result = eventBody.get(0);
        if (result == null) {
            logger.warn("作业 {} 的结果为空", jobUuid);
            return;
        }
        
        // 处理错误情况
        if (result.startsWith("-ERR")) {
            handleOriginateError(jobUuid, result, event);
        } else {
            logger.info("作业 {} 执行成功: {}", jobUuid, result);
        }
    }
    
    /**
     * 处理 originate 命令的错误
     * 
     * @param jobUuid 作业 UUID
     * @param errorMessage 错误消息
     * @param event ESL 事件对象
     */
    private void handleOriginateError(String jobUuid, String errorMessage, EslEvent event) {
        logger.error("作业 {} 执行失败: {}", jobUuid, errorMessage);
        
        // 解析错误类型
        FreeSwitchErrorType errorType = parseErrorType(errorMessage);
        
        // 根据错误类型进行不同的处理
        switch (errorType) {
            case DESTINATION_OUT_OF_ORDER:
                handleDestinationOutOfOrder(jobUuid, errorMessage, event);
                break;
            case USER_NOT_REGISTERED:
                handleUserNotRegistered(jobUuid, errorMessage, event);
                break;
            case NO_ROUTE_DESTINATION:
                handleNoRouteDestination(jobUuid, errorMessage, event);
                break;
            case UNKNOWN_ERROR:
            default:
                handleUnknownError(jobUuid, errorMessage, event);
                break;
        }
    }
    
    /**
     * 解析错误类型
     * 
     * @param errorMessage 错误消息
     * @return 错误类型枚举
     */
    private FreeSwitchErrorType parseErrorType(String errorMessage) {
        if (errorMessage.contains("DESTINATION_OUT_OF_ORDER")) {
            return FreeSwitchErrorType.DESTINATION_OUT_OF_ORDER;
        } else if (errorMessage.contains("USER_NOT_REGISTERED")) {
            return FreeSwitchErrorType.USER_NOT_REGISTERED;
        } else if (errorMessage.contains("NO_ROUTE_DESTINATION")) {
            return FreeSwitchErrorType.NO_ROUTE_DESTINATION;
        }
        return FreeSwitchErrorType.UNKNOWN_ERROR;
    }
    
    /**
     * 处理 DESTINATION_OUT_OF_ORDER 错误
     * 目标号码不可用或无法接通
     * 
     * @param jobUuid 作业 UUID
     * @param errorMessage 错误消息
     * @param event ESL 事件对象
     */
    private void handleDestinationOutOfOrder(String jobUuid, String errorMessage, EslEvent event) {
        logger.error("作业 {} 失败: 目标号码不可用 (DESTINATION_OUT_OF_ORDER)", jobUuid);
        
        // 获取作业命令参数，可能包含目标号码信息
        String jobCommandArg = event.getEventHeaders().get("Job-Command-Arg");
        logger.debug("作业命令参数: {}", jobCommandArg);
        
        // 可以在这里添加具体的处理逻辑，例如：
        // 1. 记录到数据库
        // 2. 发送告警通知
        // 3. 重试机制
        // 4. 更新呼叫状态
        
        // 示例：记录失败原因
        recordFailureReason(jobUuid, FreeSwitchErrorType.DESTINATION_OUT_OF_ORDER, errorMessage);
    }
    
    /**
     * 处理 USER_NOT_REGISTERED 错误
     * 
     * @param jobUuid 作业 UUID
     * @param errorMessage 错误消息
     * @param event ESL 事件对象
     */
    private void handleUserNotRegistered(String jobUuid, String errorMessage, EslEvent event) {
        logger.error("作业 {} 失败: 用户未注册 (USER_NOT_REGISTERED)", jobUuid);
        recordFailureReason(jobUuid, FreeSwitchErrorType.USER_NOT_REGISTERED, errorMessage);
    }
    
    /**
     * 处理 NO_ROUTE_DESTINATION 错误
     * 
     * @param jobUuid 作业 UUID
     * @param errorMessage 错误消息
     * @param event ESL 事件对象
     */
    private void handleNoRouteDestination(String jobUuid, String errorMessage, EslEvent event) {
        logger.error("作业 {} 失败: 无路由目标 (NO_ROUTE_DESTINATION)", jobUuid);
        recordFailureReason(jobUuid, FreeSwitchErrorType.NO_ROUTE_DESTINATION, errorMessage);
    }
    
    /**
     * 处理未知错误
     * 
     * @param jobUuid 作业 UUID
     * @param errorMessage 错误消息
     * @param event ESL 事件对象
     */
    private void handleUnknownError(String jobUuid, String errorMessage, EslEvent event) {
        logger.error("作业 {} 失败: 未知错误 - {}", jobUuid, errorMessage);
        recordFailureReason(jobUuid, FreeSwitchErrorType.UNKNOWN_ERROR, errorMessage);
    }
    
    /**
     * 记录失败原因
     * 
     * @param jobUuid 作业 UUID
     * @param errorType 错误类型
     * @param errorMessage 错误消息
     */
    private void recordFailureReason(String jobUuid, FreeSwitchErrorType errorType, String errorMessage) {
        // TODO: 实现具体的记录逻辑，例如保存到数据库
        logger.info("记录作业失败: jobUuid={}, errorType={}, errorMessage={}", 
                   jobUuid, errorType, errorMessage);
    }
}
