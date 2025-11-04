package com.originate.call.client.freeswitch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * FreeSWITCH Originate错误处理使用示例
 */
public class OriginateErrorExample {
    
    private static final Logger log = LoggerFactory.getLogger(OriginateErrorExample.class);
    
    public static void main(String[] args) {
        // 创建事件处理器
        BackgroundJobEventProcessor processor = new BackgroundJobEventProcessor();
        
        // 模拟从日志中解析的事件数据
        Map<String, String> eventHeaders = createSampleEventHeaders();
        String eventBody = "-ERR DESTINATION_OUT_OF_ORDER";
        String jobUuid = "1550d668-8d83-4593-985e-492ca034c02c";
        
        // 处理后台任务结果
        processor.processBackgroundJobResult(jobUuid, eventHeaders, eventBody);
        
        // 获取处理结果
        OriginateErrorResult result = processor.getJobResult(jobUuid);
        if (result != null) {
            log.info("错误处理结果:");
            log.info("  任务UUID: {}", result.getJobUuid());
            log.info("  呼叫UUID: {}", result.getCallUuid());
            log.info("  错误类型: {} ({})", result.getErrorType().getErrorCode(), 
                     result.getErrorType().getDescription());
            log.info("  被叫号码: {}", result.getCalledNumber());
            log.info("  使用网关: {}", result.getGateway());
            log.info("  是否可重试: {}", result.isRetryable());
            log.info("  建议操作: {}", result.getSuggestedAction());
            log.info("  详细信息: {}", result.getDetails());
        }
        
        // 查看错误统计
        Map<String, Integer> statistics = processor.getErrorStatistics();
        log.info("错误统计: {}", statistics);
        
        // 演示其他错误类型
        demonstrateOtherErrorTypes(processor);
    }
    
    /**
     * 创建示例事件头
     */
    private static Map<String, String> createSampleEventHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Event-Name", "BACKGROUND_JOB");
        headers.put("Job-Command", "originate");
        headers.put("Job-UUID", "1550d668-8d83-4593-985e-492ca034c02c");
        headers.put("Core-UUID", "23e5ace4-aab3-4f3c-9df4-465b1fc8bd41");
        headers.put("Job-Command-Arg", 
                "{session_type=3,object_type=1,ignore_early_media=false," +
                "origination_caller_id_number='gw113120002026'," +
                "uuid='yrRobot_202510231651440005437'," +
                "media_bug_answer_req=false," +
                "execute_on_media=lua::pre_media_event.lua}" +
                "sofia/gateway/gwopensips/13390118999 &lua(ivrbot-nopause.lua)");
        headers.put("FreeSWITCH-Hostname", "10-181-10-158");
        headers.put("FreeSWITCH-IPv4", "10.181.10.158");
        return headers;
    }
    
    /**
     * 演示其他错误类型的处理
     */
    private static void demonstrateOtherErrorTypes(BackgroundJobEventProcessor processor) {
        log.info("\n=== 演示其他错误类型 ===\n");
        
        // 用户忙
        processErrorExample(processor, "job-uuid-2", "-ERR USER_BUSY");
        
        // 无应答
        processErrorExample(processor, "job-uuid-3", "-ERR NO_ANSWER");
        
        // 号码不存在
        processErrorExample(processor, "job-uuid-4", "-ERR UNALLOCATED_NUMBER");
        
        // 网关故障
        processErrorExample(processor, "job-uuid-5", "-ERR GATEWAY_DOWN");
    }
    
    private static void processErrorExample(
            BackgroundJobEventProcessor processor,
            String jobUuid,
            String errorBody) {
        
        Map<String, String> headers = createSampleEventHeaders();
        headers.put("Job-UUID", jobUuid);
        
        processor.processBackgroundJobResult(jobUuid, headers, errorBody);
        
        OriginateErrorResult result = processor.getJobResult(jobUuid);
        if (result != null) {
            log.info("错误: {} - {} (可重试: {})",
                    result.getErrorType().getErrorCode(),
                    result.getErrorType().getDescription(),
                    result.isRetryable());
        }
    }
}
