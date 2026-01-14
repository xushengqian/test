package com.ivr.system.esl;

import com.ivr.system.engine.IvrFlowEngine;
import com.ivr.system.model.CallSession;
import com.ivr.system.model.IvrEvent;
import com.ivr.system.service.IvrService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * ESL事件处理器
 * 处理来自FreeSWITCH的事件并转发到IVR引擎
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EslEventHandler {

    private final FreeSwitchClient freeSwitchClient;
    private final IvrFlowEngine flowEngine;
    private final IvrService ivrService;
    private final ApplicationEventPublisher eventPublisher;

    private String listenerId;

    @PostConstruct
    public void init() {
        listenerId = freeSwitchClient.addEventListener(this::handleEvent);
        log.info("ESL event handler registered with listener ID: {}", listenerId);
    }

    /**
     * 处理ESL事件
     */
    private void handleEvent(String eventName, String uuid, EslResponse response) {
        if (uuid == null || uuid.isEmpty()) {
            return;
        }

        log.debug("Processing event [{}] for UUID [{}]", eventName, uuid);

        switch (eventName) {
            case "CHANNEL_CREATE" -> handleChannelCreate(uuid, response);
            case "CHANNEL_ANSWER" -> handleChannelAnswer(uuid, response);
            case "CHANNEL_HANGUP" -> handleChannelHangup(uuid, response);
            case "DTMF" -> handleDtmf(uuid, response);
            case "PLAYBACK_START" -> handlePlaybackStart(uuid, response);
            case "PLAYBACK_STOP" -> handlePlaybackStop(uuid, response);
            case "RECORD_START" -> handleRecordStart(uuid, response);
            case "RECORD_STOP" -> handleRecordStop(uuid, response);
            case "DETECTED_SPEECH" -> handleDetectedSpeech(uuid, response);
            case "CHANNEL_BRIDGE" -> handleChannelBridge(uuid, response);
            default -> log.trace("Unhandled event: {}", eventName);
        }
    }

    /**
     * 处理通道创建事件
     */
    private void handleChannelCreate(String uuid, EslResponse response) {
        log.info("Channel created: {}", uuid);

        String direction = response.getCallDirection();
        if ("inbound".equalsIgnoreCase(direction)) {
            CallSession session = CallSession.builder()
                    .sessionId(uuid)
                    .callerNumber(response.getCallerIdNumber())
                    .calledNumber(response.getDestinationNumber())
                    .direction(CallSession.CallDirection.INBOUND)
                    .state(CallSession.SessionState.INIT)
                    .build();

            ivrService.handleInboundCall(session);
        }

        publishEvent(uuid, IvrEvent.EventType.CALL_INIT, response);
    }

    /**
     * 处理通道应答事件
     */
    private void handleChannelAnswer(String uuid, EslResponse response) {
        log.info("Channel answered: {}", uuid);

        CallSession session = flowEngine.getSession(uuid);
        if (session != null && session.getState() == CallSession.SessionState.INIT) {
            session.setState(CallSession.SessionState.RUNNING);
            flowEngine.executeCurrentNode(session);
        }

        publishEvent(uuid, IvrEvent.EventType.CALL_ANSWER, response);
    }

    /**
     * 处理通道挂断事件
     */
    private void handleChannelHangup(String uuid, EslResponse response) {
        String cause = response.getHangupCause();
        log.info("Channel hangup: {}, cause: {}", uuid, cause);

        flowEngine.handleHangup(uuid, cause);

        publishEvent(uuid, IvrEvent.EventType.CALL_HANGUP, response);
    }

    /**
     * 处理DTMF事件
     */
    private void handleDtmf(String uuid, EslResponse response) {
        String digit = response.getDtmfDigit();
        log.info("DTMF received: {} for UUID: {}", digit, uuid);

        if (digit != null && !digit.isEmpty()) {
            flowEngine.handleDtmfInput(uuid, digit);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("digit", digit);
        publishEvent(uuid, IvrEvent.EventType.DTMF, data);
    }

    /**
     * 处理播放开始事件
     */
    private void handlePlaybackStart(String uuid, EslResponse response) {
        log.debug("Playback started for UUID: {}", uuid);
        publishEvent(uuid, IvrEvent.EventType.PLAYBACK_START, response);
    }

    /**
     * 处理播放结束事件
     */
    private void handlePlaybackStop(String uuid, EslResponse response) {
        log.debug("Playback stopped for UUID: {}", uuid);

        CallSession session = flowEngine.getSession(uuid);
        if (session != null && session.getState() == CallSession.SessionState.RUNNING) {
            flowEngine.executeCurrentNode(session);
        }

        publishEvent(uuid, IvrEvent.EventType.PLAYBACK_COMPLETE, response);
    }

    /**
     * 处理录音开始事件
     */
    private void handleRecordStart(String uuid, EslResponse response) {
        log.info("Recording started for UUID: {}", uuid);
        publishEvent(uuid, IvrEvent.EventType.RECORD_START, response);
    }

    /**
     * 处理录音结束事件
     */
    private void handleRecordStop(String uuid, EslResponse response) {
        log.info("Recording stopped for UUID: {}", uuid);

        String filePath = response.getHeader("Record-File-Path");
        CallSession session = flowEngine.getSession(uuid);
        if (session != null && filePath != null) {
            session.setVariable("last_record_file", filePath);
        }

        publishEvent(uuid, IvrEvent.EventType.RECORD_STOP, response);
    }

    /**
     * 处理语音识别结果事件
     */
    private void handleDetectedSpeech(String uuid, EslResponse response) {
        String speech = response.getBody();
        log.info("Speech detected for UUID: {}: {}", uuid, speech);

        CallSession session = flowEngine.getSession(uuid);
        if (session != null && speech != null) {
            session.setVariable("asr_result", speech);
            flowEngine.executeCurrentNode(session);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("speech", speech);
        publishEvent(uuid, IvrEvent.EventType.ASR_RESULT, data);
    }

    /**
     * 处理通道桥接事件
     */
    private void handleChannelBridge(String uuid, EslResponse response) {
        String otherLegUuid = response.getHeader("Other-Leg-Unique-ID");
        log.info("Channel bridge: {} <-> {}", uuid, otherLegUuid);

        CallSession session = flowEngine.getSession(uuid);
        if (session != null) {
            session.setState(CallSession.SessionState.WITH_AGENT);
            session.setAttribute("bridged_uuid", otherLegUuid);
        }

        publishEvent(uuid, IvrEvent.EventType.AGENT_ANSWER, response);
    }

    /**
     * 发布IVR事件
     */
    private void publishEvent(String uuid, IvrEvent.EventType type, EslResponse response) {
        Map<String, Object> data = new HashMap<>();
        if (response != null) {
            data.putAll(response.getHeaders());
        }
        publishEvent(uuid, type, data);
    }

    /**
     * 发布IVR事件
     */
    private void publishEvent(String uuid, IvrEvent.EventType type, Map<String, Object> data) {
        IvrEvent event = IvrEvent.builder()
                .sessionId(uuid)
                .type(type)
                .data(data)
                .source("FreeSWITCH")
                .build();

        eventPublisher.publishEvent(event);
    }
}
