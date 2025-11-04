package com.originate.call.client.freeswitch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FreeSWITCH后台任务事件处理器
 * 用于处理BACKGROUND_JOB事件，特别是originate命令的结果
 */
public class BackgroundJobEventProcessor {
    
    private static final Logger log = LoggerFactory.getLogger(BackgroundJobEventProcessor.class);
    
    private final OriginateErrorHandler errorHandler;
    
    // 任务结果缓存
    private final Map<String, OriginateErrorResult> jobResultCache = new ConcurrentHashMap<>();
    
    public BackgroundJobEventProcessor() {
        this.errorHandler = new OriginateErrorHandler();
    }
    
    public BackgroundJobEventProcessor(OriginateErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }
    
    /**
     * 处理后台任务结果
     * 
     * @param jobUuid 任务UUID (Job-UUID)
     * @param eventHeaders 事件头信息
     * @param eventBody 事件体内容（可能包含错误信息）
     */
    public void processBackgroundJobResult(
            String jobUuid,
            Map<String, String> eventHeaders,
            String eventBody) {
        
        if (jobUuid == null || jobUuid.isEmpty()) {
            log.warn("jobUuid为空，无法处理后台任务结果");
            return;
        }
        
        log.info("收到后台任务结果 - jobUuid: {}, eventBody: {}", jobUuid, eventBody);
        
        // 获取任务命令
        String jobCommand = eventHeaders.get("Job-Command");
        
        // 只处理originate命令
        if (!"originate".equalsIgnoreCase(jobCommand)) {
            log.debug("非originate命令，跳过处理: {}", jobCommand);
            return;
        }
        
        // 判断是否是错误结果
        if (isErrorResult(eventBody)) {
            handleOriginateError(jobUuid, eventHeaders, eventBody);
        } else {
            handleOriginateSuccess(jobUuid, eventHeaders, eventBody);
        }
    }
    
    /**
     * 判断是否是错误结果
     */
    private boolean isErrorResult(String eventBody) {
        if (eventBody == null || eventBody.isEmpty()) {
            return false;
        }
        
        String trimmed = eventBody.trim();
        return trimmed.startsWith("-ERR") || trimmed.startsWith("ERROR");
    }
    
    /**
     * 处理originate错误
     */
    private void handleOriginateError(
            String jobUuid,
            Map<String, String> eventHeaders,
            String eventBody) {
        
        log.warn("Originate命令执行失败 - jobUuid: {}, error: {}", jobUuid, eventBody);
        
        // 使用错误处理器处理错误
        OriginateErrorResult result = errorHandler.handleOriginateError(
                jobUuid, 
                eventBody, 
                eventHeaders);
        
        // 缓存结果
        jobResultCache.put(jobUuid, result);
        
        // 输出详细信息
        log.error("Originate错误详情 - {}", result);
        
        // 根据错误类型执行相应操作
        if (result.isRetryable()) {
            log.info("错误可重试，建议操作: {}", result.getSuggestedAction());
            // 这里可以触发重试逻辑
            // triggerRetry(result);
        } else {
            log.warn("错误不建议重试: {}", result.getSuggestedAction());
            // 这里可以触发告警或记录
            // notifyFailure(result);
        }
    }
    
    /**
     * 处理originate成功
     */
    private void handleOriginateSuccess(
            String jobUuid,
            Map<String, String> eventHeaders,
            String eventBody) {
        
        log.info("Originate命令执行成功 - jobUuid: {}, result: {}", jobUuid, eventBody);
        
        // 这里可以处理成功的情况
        // 例如: 更新呼叫状态、触发后续流程等
    }
    
    /**
     * 获取任务处理结果
     */
    public OriginateErrorResult getJobResult(String jobUuid) {
        return jobResultCache.get(jobUuid);
    }
    
    /**
     * 清除任务结果缓存
     */
    public void clearJobResult(String jobUuid) {
        jobResultCache.remove(jobUuid);
    }
    
    /**
     * 获取所有缓存的任务结果
     */
    public Map<String, OriginateErrorResult> getAllJobResults() {
        return new ConcurrentHashMap<>(jobResultCache);
    }
    
    /**
     * 清空所有缓存
     */
    public void clearAllJobResults() {
        jobResultCache.clear();
        log.info("已清空所有任务结果缓存");
    }
    
    /**
     * 获取错误统计
     */
    public Map<String, Integer> getErrorStatistics() {
        return errorHandler.getErrorStatistics();
    }
    
    /**
     * 获取错误处理器
     */
    public OriginateErrorHandler getErrorHandler() {
        return errorHandler;
    }
}
