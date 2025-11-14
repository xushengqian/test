package com.openaibot.callcenter.core.freeswitch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FreeSWITCH Originate 失败处理器
 * 处理后台任务中的 originate 命令失败场景
 */
@Slf4j
@Component
public class FreeSwitchOriginateFailureHandler {

    // 错误码映射
    private static final Map<String, String> ERROR_CODE_MAP = Map.of(
        "DESTINATION_OUT_OF_ORDER", "目标号码不可达或已停机",
        "NO_ROUTE_DESTINATION", "无路由到目标",
        "CALL_REJECTED", "呼叫被拒绝",
        "NO_USER_RESPONSE", "用户无响应",
        "USER_BUSY", "用户忙",
        "NORMAL_TEMPORARY_FAILURE", "临时故障",
        "RECOVERY_ON_TIMER_EXPIRE", "超时恢复",
        "ORIGINATOR_CANCEL", "发起方取消"
    );

    /**
     * 处理后台任务结果
     * 
     * @param jobUuid 任务 UUID
     * @param event 事件对象
     */
    public void handleBackgroundJobResult(String jobUuid, Object event) {
        try {
            log.info("处理 backgroundJob 结果，jobUuid: {}", jobUuid);
            
            // 解析事件体获取错误信息
            String errorMessage = extractErrorMessage(event);
            
            if (errorMessage != null && errorMessage.startsWith("-ERR")) {
                // 呼叫失败
                handleOriginateFailure(jobUuid, errorMessage, event);
            } else if (errorMessage != null && !errorMessage.startsWith("-ERR")) {
                // 呼叫成功，errorMessage 包含 channel UUID
                handleOriginateSuccess(jobUuid, errorMessage, event);
            } else {
                log.warn("无法解析 backgroundJob 结果，jobUuid: {}, event: {}", jobUuid, event);
            }
        } catch (Exception e) {
            log.error("处理 backgroundJob 结果异常，jobUuid: {}", jobUuid, e);
        }
    }

    /**
     * 处理 originate 失败
     * 
     * @param jobUuid 任务 UUID
     * @param errorMessage 错误信息
     * @param event 事件对象
     */
    private void handleOriginateFailure(String jobUuid, String errorMessage, Object event) {
        // 提取错误码
        String errorCode = extractErrorCode(errorMessage);
        String errorDesc = ERROR_CODE_MAP.getOrDefault(errorCode, "未知错误");
        
        log.error("Originate 呼叫失败 - jobUuid: {}, 错误码: {}, 错误描述: {}, 原始错误: {}", 
                  jobUuid, errorCode, errorDesc, errorMessage);
        
        // 从事件中提取关键信息
        Map<String, String> callInfo = extractCallInfo(event);
        
        // 根据错误类型进行不同处理
        switch (errorCode) {
            case "DESTINATION_OUT_OF_ORDER":
                handleDestinationOutOfOrder(jobUuid, callInfo);
                break;
            case "NO_ROUTE_DESTINATION":
                handleNoRouteDestination(jobUuid, callInfo);
                break;
            case "USER_BUSY":
                handleUserBusy(jobUuid, callInfo);
                break;
            case "NO_USER_RESPONSE":
                handleNoUserResponse(jobUuid, callInfo);
                break;
            case "NORMAL_TEMPORARY_FAILURE":
                handleTemporaryFailure(jobUuid, callInfo);
                break;
            default:
                handleGeneralFailure(jobUuid, errorCode, callInfo);
                break;
        }
    }

    /**
     * 处理目标不可达错误
     */
    private void handleDestinationOutOfOrder(String jobUuid, Map<String, String> callInfo) {
        log.warn("目标号码不可达 - jobUuid: {}, 被叫号码: {}, 网关: {}", 
                 jobUuid, callInfo.get("callee"), callInfo.get("gateway"));
        
        // TODO: 实现业务逻辑
        // 1. 标记该号码为无效号码
        // 2. 更新呼叫记录状态
        // 3. 触发重试机制（如果配置了）
        // 4. 发送通知或告警
    }

    /**
     * 处理无路由错误
     */
    private void handleNoRouteDestination(String jobUuid, Map<String, String> callInfo) {
        log.warn("无路由到目标 - jobUuid: {}, 被叫号码: {}, 网关: {}", 
                 jobUuid, callInfo.get("callee"), callInfo.get("gateway"));
        
        // TODO: 实现业务逻辑
        // 1. 检查网关配置
        // 2. 尝试切换备用网关
        // 3. 记录路由失败日志
    }

    /**
     * 处理用户忙错误
     */
    private void handleUserBusy(String jobUuid, Map<String, String> callInfo) {
        log.info("用户忙 - jobUuid: {}, 被叫号码: {}", jobUuid, callInfo.get("callee"));
        
        // TODO: 实现业务逻辑
        // 1. 安排稍后重拨
        // 2. 更新呼叫记录状态为"忙"
    }

    /**
     * 处理用户无响应错误
     */
    private void handleNoUserResponse(String jobUuid, Map<String, String> callInfo) {
        log.info("用户无响应 - jobUuid: {}, 被叫号码: {}", jobUuid, callInfo.get("callee"));
        
        // TODO: 实现业务逻辑
        // 1. 安排重试
        // 2. 更新呼叫记录状态为"无应答"
    }

    /**
     * 处理临时故障
     */
    private void handleTemporaryFailure(String jobUuid, Map<String, String> callInfo) {
        log.warn("临时故障 - jobUuid: {}, 被叫号码: {}", jobUuid, callInfo.get("callee"));
        
        // TODO: 实现业务逻辑
        // 1. 触发重试机制
        // 2. 记录故障信息
    }

    /**
     * 处理通用失败
     */
    private void handleGeneralFailure(String jobUuid, String errorCode, Map<String, String> callInfo) {
        log.error("Originate 失败 - jobUuid: {}, 错误码: {}, 呼叫信息: {}", 
                  jobUuid, errorCode, callInfo);
        
        // TODO: 实现业务逻辑
        // 1. 更新呼叫记录状态为失败
        // 2. 记录详细错误信息
        // 3. 根据配置决定是否重试
    }

    /**
     * 处理 originate 成功
     * 
     * @param jobUuid 任务 UUID
     * @param channelUuid 通道 UUID
     * @param event 事件对象
     */
    private void handleOriginateSuccess(String jobUuid, String channelUuid, Object event) {
        log.info("Originate 呼叫成功 - jobUuid: {}, channelUuid: {}", jobUuid, channelUuid);
        
        // TODO: 实现业务逻辑
        // 1. 更新呼叫记录状态
        // 2. 保存 channel UUID
        // 3. 开始监控通话状态
    }

    /**
     * 从事件体中提取错误信息
     */
    private String extractErrorMessage(Object event) {
        try {
            // 假设 event 是一个 Map 或者有 getEventBody() 方法
            if (event instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> eventMap = (Map<String, Object>) event;
                Object eventBody = eventMap.get("eventBody");
                if (eventBody instanceof String[]) {
                    String[] bodyArray = (String[]) eventBody;
                    return bodyArray.length > 0 ? bodyArray[0] : null;
                } else if (eventBody != null) {
                    return eventBody.toString();
                }
            }
            
            // 尝试通过反射获取
            try {
                var method = event.getClass().getMethod("getEventBody");
                Object result = method.invoke(event);
                if (result instanceof String[]) {
                    String[] bodyArray = (String[]) result;
                    return bodyArray.length > 0 ? bodyArray[0] : null;
                }
                return result != null ? result.toString() : null;
            } catch (Exception e) {
                log.debug("无法通过反射获取 eventBody", e);
            }
            
            return null;
        } catch (Exception e) {
            log.error("提取错误信息失败", e);
            return null;
        }
    }

    /**
     * 从错误信息中提取错误码
     * 例如: "-ERR DESTINATION_OUT_OF_ORDER" -> "DESTINATION_OUT_OF_ORDER"
     */
    private String extractErrorCode(String errorMessage) {
        if (errorMessage == null || !errorMessage.startsWith("-ERR")) {
            return "UNKNOWN";
        }
        
        String code = errorMessage.substring(5).trim();
        // 移除可能的换行符
        int newlineIndex = code.indexOf('\n');
        if (newlineIndex > 0) {
            code = code.substring(0, newlineIndex);
        }
        
        return code;
    }

    /**
     * 从事件中提取呼叫信息
     */
    private Map<String, String> extractCallInfo(Object event) {
        try {
            // 解析 Job-Command-Arg 获取呼叫参数
            String commandArg = getEventHeader(event, "Job-Command-Arg");
            if (commandArg != null) {
                return parseOriginateCommand(commandArg);
            }
        } catch (Exception e) {
            log.error("提取呼叫信息失败", e);
        }
        return Map.of();
    }

    /**
     * 获取事件头信息
     */
    private String getEventHeader(Object event, String headerName) {
        try {
            if (event instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> eventMap = (Map<String, Object>) event;
                Object eventHeaders = eventMap.get("eventHeaders");
                if (eventHeaders instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> headers = (Map<String, Object>) eventHeaders;
                    Object value = headers.get(headerName);
                    return value != null ? value.toString() : null;
                }
            }
        } catch (Exception e) {
            log.debug("获取事件头失败: {}", headerName, e);
        }
        return null;
    }

    /**
     * 解析 originate 命令参数
     * 例如: "{...}sofia/gateway/gwopensips/13716763135 &lua(ivrbot-nopause.lua)"
     */
    private Map<String, String> parseOriginateCommand(String commandArg) {
        String callee = null;
        String gateway = null;
        String uuid = null;
        String caller = null;
        
        try {
            // 提取被叫号码和网关
            Pattern pattern = Pattern.compile("sofia/gateway/([^/]+)/([^\\s]+)");
            Matcher matcher = pattern.matcher(commandArg);
            if (matcher.find()) {
                gateway = matcher.group(1);
                callee = matcher.group(2);
            }
            
            // 提取 UUID
            Pattern uuidPattern = Pattern.compile("uuid='([^']+)'");
            Matcher uuidMatcher = uuidPattern.matcher(commandArg);
            if (uuidMatcher.find()) {
                uuid = uuidMatcher.group(1);
            }
            
            // 提取主叫号码
            Pattern callerPattern = Pattern.compile("origination_caller_id_number='([^']+)'");
            Matcher callerMatcher = callerPattern.matcher(commandArg);
            if (callerMatcher.find()) {
                caller = callerMatcher.group(1);
            }
        } catch (Exception e) {
            log.error("解析 originate 命令参数失败: {}", commandArg, e);
        }
        
        return Map.of(
            "callee", callee != null ? callee : "",
            "gateway", gateway != null ? gateway : "",
            "uuid", uuid != null ? uuid : "",
            "caller", caller != null ? caller : ""
        );
    }
}
