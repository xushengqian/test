package com.xxx.xxx.xxx.freeswitch;

import org.freeswitch.esl.client.transport.event.EslEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.util.*;

import static org.mockito.Mockito.*;

/**
 * FreeSwitchClientService 测试类
 */
@ExtendWith(MockitoExtension.class)
class FreeSwitchClientServiceTest {
    
    private FreeSwitchClientService service;
    
    @Mock
    private EslEvent mockEvent;
    
    @BeforeEach
    void setUp() {
        service = new FreeSwitchClientService();
    }
    
    @Test
    void testHandleDestinationOutOfOrderError() {
        // 准备测试数据
        String jobUuid = "1550d668-8d83-4593-985e-492ca034c02c";
        String jobCommandArg = "{session_type=3,object_type=1,ignore_early_media=false,origination_caller_id_number='gw113120002026',uuid='yrRobot_202510231651440005437',media_bug_answer_req=false,execute_on_media=lua::pre_media_event.lua}sofia/gateway/gwopensips/13390118999 &lua(ivrbot-nopause.lua)";
        
        Map<String, String> eventHeaders = new HashMap<>();
        eventHeaders.put("Job-Command-Arg", jobCommandArg);
        eventHeaders.put("Job-UUID", jobUuid);
        
        List<String> eventBody = Arrays.asList("-ERR DESTINATION_OUT_OF_ORDER");
        
        // 设置 mock 行为
        when(mockEvent.getEventHeaders()).thenReturn(eventHeaders);
        when(mockEvent.getEventBodyLines()).thenReturn(eventBody);
        
        // 执行测试
        service.backgroundJobResultReceived(jobUuid, mockEvent);
        
        // 验证方法被调用
        verify(mockEvent, atLeastOnce()).getEventHeaders();
        verify(mockEvent, atLeastOnce()).getEventBodyLines();
    }
    
    @Test
    void testHandleSuccessfulJob() {
        // 准备测试数据
        String jobUuid = "test-uuid-123";
        Map<String, String> eventHeaders = new HashMap<>();
        eventHeaders.put("Job-UUID", jobUuid);
        
        List<String> eventBody = Arrays.asList("+OK");
        
        // 设置 mock 行为
        when(mockEvent.getEventHeaders()).thenReturn(eventHeaders);
        when(mockEvent.getEventBodyLines()).thenReturn(eventBody);
        
        // 执行测试
        service.backgroundJobResultReceived(jobUuid, mockEvent);
        
        // 验证方法被调用
        verify(mockEvent, atLeastOnce()).getEventHeaders();
        verify(mockEvent, atLeastOnce()).getEventBodyLines();
    }
    
    @Test
    void testHandleEmptyEventBody() {
        // 准备测试数据
        String jobUuid = "test-uuid-456";
        Map<String, String> eventHeaders = new HashMap<>();
        eventHeaders.put("Job-UUID", jobUuid);
        
        // 设置 mock 行为 - 空的事件体
        when(mockEvent.getEventHeaders()).thenReturn(eventHeaders);
        when(mockEvent.getEventBodyLines()).thenReturn(null);
        
        // 执行测试
        service.backgroundJobResultReceived(jobUuid, mockEvent);
        
        // 验证方法被调用
        verify(mockEvent, atLeastOnce()).getEventHeaders();
        verify(mockEvent, atLeastOnce()).getEventBodyLines();
    }
}
