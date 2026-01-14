package com.ivr.system.controller;

import com.ivr.system.model.CallSession;
import com.ivr.system.model.IvrFlow;
import com.ivr.system.model.IvrResult;
import com.ivr.system.service.IvrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * IVR REST API控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/ivr")
@RequiredArgsConstructor
public class IvrController {

    private final IvrService ivrService;

    /**
     * 获取系统状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(ivrService.getSessionStats());
    }

    /**
     * 发起呼出呼叫
     */
    @PostMapping("/call/outbound")
    public ResponseEntity<ApiResponse<CallSession>> initiateOutboundCall(
            @RequestBody OutboundCallRequest request) {

        log.info("Initiating outbound call to: {}", request.getDestination());

        CallSession session = ivrService.initiateOutboundCall(
                request.getDestination(),
                request.getCallerIdNumber(),
                request.getCallerIdName(),
                request.getFlowId(),
                request.getGateway(),
                request.getVariables()
        );

        if (session != null) {
            return ResponseEntity.ok(ApiResponse.success(session));
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("CALL_FAILED", "Failed to initiate call"));
        }
    }

    /**
     * 挂断呼叫
     */
    @PostMapping("/call/{sessionId}/hangup")
    public ResponseEntity<ApiResponse<IvrResult>> hangupCall(
            @PathVariable String sessionId,
            @RequestParam(required = false) String reason) {

        log.info("Hanging up call: {}", sessionId);

        IvrResult result = ivrService.hangupCall(sessionId, reason);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 转接到坐席
     */
    @PostMapping("/call/{sessionId}/transfer/agent")
    public ResponseEntity<ApiResponse<IvrResult>> transferToAgent(
            @PathVariable String sessionId,
            @RequestBody TransferRequest request) {

        log.info("Transferring call {} to agent group: {}", sessionId, request.getAgentGroup());

        IvrResult result = ivrService.transferToAgent(
                sessionId,
                request.getAgentGroup(),
                request.getSkillGroup()
        );

        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(result));
        } else {
            return ResponseEntity.badRequest().body(ApiResponse.error(result.getCode(), result.getMessage()));
        }
    }

    /**
     * 播放语音
     */
    @PostMapping("/call/{sessionId}/play")
    public ResponseEntity<ApiResponse<IvrResult>> playAudio(
            @PathVariable String sessionId,
            @RequestBody PlayRequest request) {

        log.info("Playing audio for call: {}", sessionId);

        IvrResult result = ivrService.playAudio(
                sessionId,
                request.getContent(),
                request.isUseTts()
        );

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取会话信息
     */
    @GetMapping("/call/{sessionId}")
    public ResponseEntity<ApiResponse<CallSession>> getSession(@PathVariable String sessionId) {
        CallSession session = ivrService.getSession(sessionId);
        if (session != null) {
            return ResponseEntity.ok(ApiResponse.success(session));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 获取所有流程
     */
    @GetMapping("/flows")
    public ResponseEntity<ApiResponse<Map<String, IvrFlow>>> getAllFlows() {
        return ResponseEntity.ok(ApiResponse.success(ivrService.getAllFlows()));
    }

    /**
     * 获取指定流程
     */
    @GetMapping("/flows/{flowId}")
    public ResponseEntity<ApiResponse<IvrFlow>> getFlow(@PathVariable String flowId) {
        IvrFlow flow = ivrService.getFlow(flowId);
        if (flow != null) {
            return ResponseEntity.ok(ApiResponse.success(flow));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 注册新流程
     */
    @PostMapping("/flows")
    public ResponseEntity<ApiResponse<Void>> registerFlow(@RequestBody IvrFlow flow) {
        log.info("Registering flow: {}", flow.getId());
        ivrService.registerFlow(flow);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 重新加载流程
     */
    @PostMapping("/flows/reload")
    public ResponseEntity<ApiResponse<Void>> reloadFlows() {
        log.info("Reloading flows");
        ivrService.reloadFlows();
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ==================== 请求/响应类 ====================

    @lombok.Data
    public static class OutboundCallRequest {
        private String destination;
        private String callerIdNumber;
        private String callerIdName;
        private String flowId;
        private String gateway;
        private Map<String, String> variables;
    }

    @lombok.Data
    public static class TransferRequest {
        private String agentGroup;
        private String skillGroup;
    }

    @lombok.Data
    public static class PlayRequest {
        private String content;
        private boolean useTts;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ApiResponse<T> {
        private boolean success;
        private String code;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .code("SUCCESS")
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> error(String code, String message) {
            return ApiResponse.<T>builder()
                    .success(false)
                    .code(code)
                    .message(message)
                    .build();
        }
    }
}
