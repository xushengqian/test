package com.openaibot.callcenter.core.freeswitch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FreeSwitchOriginateFailureHandler 单元测试
 */
@ExtendWith(MockitoExtension.class)
class FreeSwitchOriginateFailureHandlerTest {

    @InjectMocks
    private FreeSwitchOriginateFailureHandler handler;

    private Map<String, Object> mockEvent;

    @BeforeEach
    void setUp() {
        mockEvent = new HashMap<>();
    }

    @Test
    void testHandleDestinationOutOfOrder() {
        // 模拟 DESTINATION_OUT_OF_ORDER 错误
        setupMockEvent("-ERR DESTINATION_OUT_OF_ORDER",
                      "26f93d9e-6eb5-43b7-8f59-7cee547e6eba",
                      "{session_type=3,origination_caller_id_number='gw113120002026',uuid='yrRobot_202511141035370016696'}sofia/gateway/gwopensips/13716763135 &lua(ivrbot-nopause.lua)");
        
        // 执行测试
        assertDoesNotThrow(() -> 
            handler.handleBackgroundJobResult("26f93d9e-6eb5-43b7-8f59-7cee547e6eba", mockEvent)
        );
    }

    @Test
    void testHandleUserBusy() {
        // 模拟 USER_BUSY 错误
        setupMockEvent("-ERR USER_BUSY",
                      "test-uuid-001",
                      "sofia/gateway/test-gw/13800138000 &park()");
        
        assertDoesNotThrow(() -> 
            handler.handleBackgroundJobResult("test-uuid-001", mockEvent)
        );
    }

    @Test
    void testHandleNoRouteDestination() {
        // 模拟 NO_ROUTE_DESTINATION 错误
        setupMockEvent("-ERR NO_ROUTE_DESTINATION",
                      "test-uuid-002",
                      "sofia/gateway/invalid-gw/13900139000 &park()");
        
        assertDoesNotThrow(() -> 
            handler.handleBackgroundJobResult("test-uuid-002", mockEvent)
        );
    }

    @Test
    void testHandleOriginateSuccess() {
        // 模拟成功的 originate
        setupMockEvent("550e8400-e29b-41d4-a716-446655440000",
                      "test-uuid-003",
                      "sofia/gateway/test-gw/13800138000 &park()");
        
        assertDoesNotThrow(() -> 
            handler.handleBackgroundJobResult("test-uuid-003", mockEvent)
        );
    }

    @Test
    void testHandleNoUserResponse() {
        // 模拟 NO_USER_RESPONSE 错误
        setupMockEvent("-ERR NO_USER_RESPONSE",
                      "test-uuid-004",
                      "sofia/gateway/test-gw/13800138000 &park()");
        
        assertDoesNotThrow(() -> 
            handler.handleBackgroundJobResult("test-uuid-004", mockEvent)
        );
    }

    @Test
    void testHandleTemporaryFailure() {
        // 模拟 NORMAL_TEMPORARY_FAILURE 错误
        setupMockEvent("-ERR NORMAL_TEMPORARY_FAILURE",
                      "test-uuid-005",
                      "sofia/gateway/test-gw/13800138000 &park()");
        
        assertDoesNotThrow(() -> 
            handler.handleBackgroundJobResult("test-uuid-005", mockEvent)
        );
    }

    @Test
    void testHandleUnknownError() {
        // 模拟未知错误
        setupMockEvent("-ERR UNKNOWN_ERROR_CODE",
                      "test-uuid-006",
                      "sofia/gateway/test-gw/13800138000 &park()");
        
        assertDoesNotThrow(() -> 
            handler.handleBackgroundJobResult("test-uuid-006", mockEvent)
        );
    }

    @Test
    void testHandleNullEvent() {
        // 测试空事件处理
        assertDoesNotThrow(() -> 
            handler.handleBackgroundJobResult("test-uuid-007", null)
        );
    }

    @Test
    void testHandleInvalidEventFormat() {
        // 测试无效的事件格式
        Map<String, Object> invalidEvent = new HashMap<>();
        invalidEvent.put("invalidKey", "invalidValue");
        
        assertDoesNotThrow(() -> 
            handler.handleBackgroundJobResult("test-uuid-008", invalidEvent)
        );
    }

    /**
     * 设置模拟事件数据
     */
    private void setupMockEvent(String eventBody, String jobUuid, String commandArg) {
        // 设置事件体
        mockEvent.put("eventBody", new String[]{eventBody});
        
        // 设置事件头
        Map<String, Object> eventHeaders = new HashMap<>();
        eventHeaders.put("Job-UUID", jobUuid);
        eventHeaders.put("Job-Command", "originate");
        eventHeaders.put("Job-Command-Arg", commandArg);
        eventHeaders.put("Event-Name", "BACKGROUND_JOB");
        
        mockEvent.put("eventHeaders", eventHeaders);
        
        // 设置消息头
        Map<String, String> messageHeaders = new HashMap<>();
        messageHeaders.put("CONTENT_TYPE", "text/event-plain");
        mockEvent.put("messageHeaders", messageHeaders);
    }

    @Test
    void testRealWorldExample() {
        // 使用用户提供的真实日志数据进行测试
        Map<String, Object> realEvent = new HashMap<>();
        
        // 事件体
        realEvent.put("eventBody", new String[]{"-ERR DESTINATION_OUT_OF_ORDER"});
        
        // 事件头
        Map<String, Object> eventHeaders = new HashMap<>();
        eventHeaders.put("Event-Date-Timestamp", "1763087737463809");
        eventHeaders.put("Job-Command", "originate");
        eventHeaders.put("Core-UUID", "ae0c48a0-5c4a-42ae-afd4-f1ed72d45943");
        eventHeaders.put("FreeSWITCH-Switchname", "10-181-10-158");
        eventHeaders.put("Event-Calling-Line-Number", "1572");
        eventHeaders.put("Job-UUID", "26f93d9e-6eb5-43b7-8f59-7cee547e6eba");
        eventHeaders.put("FreeSWITCH-Hostname", "10-181-10-158");
        eventHeaders.put("Event-Calling-Function", "api_exec");
        eventHeaders.put("Event-Date-Local", "2025-11-14 10:35:37");
        eventHeaders.put("Event-Name", "BACKGROUND_JOB");
        eventHeaders.put("FreeSWITCH-IPv6", "::1");
        eventHeaders.put("FreeSWITCH-IPv4", "10.181.10.158");
        eventHeaders.put("Event-Sequence", "1911383");
        eventHeaders.put("Event-Calling-File", "mod_event_socket.c");
        eventHeaders.put("Event-Date-GMT", "Fri, 14 Nov 2025 02:35:37 GMT");
        eventHeaders.put("Job-Command-Arg", 
            "{session_type=3,object_type=1,ignore_early_media=false,origination_caller_id_number='gw113120002026'," +
            "uuid='yrRobot_202511141035370016696',media_bug_answer_req=false," +
            "execute_on_media=lua::pre_media_event.lua}sofia/gateway/gwopensips/13716763135 &lua(ivrbot-nopause.lua)");
        eventHeaders.put("Content-Length", "30");
        
        realEvent.put("eventHeaders", eventHeaders);
        
        // 消息头
        Map<String, String> messageHeaders = new HashMap<>();
        messageHeaders.put("CONTENT_LENGTH", "906");
        messageHeaders.put("CONTENT_TYPE", "text/event-plain");
        realEvent.put("messageHeaders", messageHeaders);
        
        // 执行测试
        assertDoesNotThrow(() -> 
            handler.handleBackgroundJobResult("26f93d9e-6eb5-43b7-8f59-7cee547e6eba", realEvent)
        );
    }
}
