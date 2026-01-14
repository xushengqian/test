package com.ivr.system.controller;

import com.ivr.system.engine.IvrFlowEngine;
import com.ivr.system.model.CallSession;
import com.ivr.system.model.IvrResult;
import com.ivr.system.service.IvrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Webhook控制器 - 处理来自FreeSWITCH的HTTP回调
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final IvrService ivrService;
    private final IvrFlowEngine flowEngine;

    /**
     * 处理呼入呼叫回调
     */
    @PostMapping("/inbound")
    public ResponseEntity<Map<String, String>> handleInboundCall(
            @RequestBody Map<String, String> params) {

        log.info("Inbound call webhook: {}", params);

        String uuid = params.get("uuid");
        String callerNumber = params.get("caller_id_number");
        String calledNumber = params.get("destination_number");

        CallSession session = CallSession.builder()
                .sessionId(uuid)
                .callerNumber(callerNumber)
                .calledNumber(calledNumber)
                .direction(CallSession.CallDirection.INBOUND)
                .state(CallSession.SessionState.INIT)
                .startTime(LocalDateTime.now())
                .build();

        IvrResult result = ivrService.handleInboundCall(session);

        return ResponseEntity.ok(Map.of(
                "status", result.isSuccess() ? "ok" : "error",
                "message", result.getMessage() != null ? result.getMessage() : ""
        ));
    }

    /**
     * 处理DTMF回调
     */
    @PostMapping("/dtmf")
    public ResponseEntity<Map<String, String>> handleDtmf(
            @RequestBody Map<String, String> params) {

        log.info("DTMF webhook: {}", params);

        String uuid = params.get("uuid");
        String digit = params.get("dtmf_digit");

        if (uuid != null && digit != null) {
            flowEngine.handleDtmfInput(uuid, digit);
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * 处理挂断回调
     */
    @PostMapping("/hangup")
    public ResponseEntity<Map<String, String>> handleHangup(
            @RequestBody Map<String, String> params) {

        log.info("Hangup webhook: {}", params);

        String uuid = params.get("uuid");
        String cause = params.get("hangup_cause");

        if (uuid != null) {
            flowEngine.handleHangup(uuid, cause);
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * 处理播放完成回调
     */
    @PostMapping("/playback-complete")
    public ResponseEntity<Map<String, String>> handlePlaybackComplete(
            @RequestBody Map<String, String> params) {

        log.info("Playback complete webhook: {}", params);

        String uuid = params.get("uuid");

        if (uuid != null) {
            CallSession session = flowEngine.getSession(uuid);
            if (session != null && session.getState() == CallSession.SessionState.RUNNING) {
                flowEngine.executeCurrentNode(session);
            }
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * 处理超时回调
     */
    @PostMapping("/timeout")
    public ResponseEntity<Map<String, String>> handleTimeout(
            @RequestBody Map<String, String> params) {

        log.info("Timeout webhook: {}", params);

        String uuid = params.get("uuid");

        if (uuid != null) {
            flowEngine.handleTimeout(uuid);
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * 处理ASR结果回调
     */
    @PostMapping("/asr-result")
    public ResponseEntity<Map<String, String>> handleAsrResult(
            @RequestBody Map<String, String> params) {

        log.info("ASR result webhook: {}", params);

        String uuid = params.get("uuid");
        String text = params.get("text");
        String confidence = params.get("confidence");

        if (uuid != null && text != null) {
            CallSession session = flowEngine.getSession(uuid);
            if (session != null) {
                session.setVariable("asr_result", text);
                session.setVariable("asr_confidence", confidence);
                flowEngine.executeCurrentNode(session);
            }
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * 处理转接结果回调
     */
    @PostMapping("/transfer-result")
    public ResponseEntity<Map<String, String>> handleTransferResult(
            @RequestBody Map<String, String> params) {

        log.info("Transfer result webhook: {}", params);

        String uuid = params.get("uuid");
        String result = params.get("result");
        String agentId = params.get("agent_id");

        if (uuid != null) {
            CallSession session = flowEngine.getSession(uuid);
            if (session != null) {
                if ("success".equals(result)) {
                    session.setState(CallSession.SessionState.WITH_AGENT);
                    session.setTransferredAgentId(agentId);
                } else {
                    session.setState(CallSession.SessionState.RUNNING);
                    flowEngine.executeCurrentNode(session);
                }
            }
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
