package com.xxx.xxx.xxx.freeSwitch;

import org.freeswitch.esl.client.transport.event.EslEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        String jobUuid = "26f93d9e-6eb5-43b7-8f59-7cee547e6eba";
        Map<String, String> eventHeaders = new HashMap<>();
        eventHeaders.put("Job-Command", "originate");
        eventHeaders.put("Job-Command-Arg", "{session_type=3,object_type=1,ignore_early_media=false,origination_caller_id_number='gw113120002026',uuid='yrRobot_202511141035370016696',media_bug_answer_req=false,execute_on_media=lua::pre_media_event.lua}sofia/gateway/gwopensips/13716763135 &lua(ivrbot-nopause.lua)");
        
        List<String> eventBody = Arrays.asList("-ERR DESTINATION_OUT_OF_ORDER");
        
        // Mock EslEvent 行为
        when(mockEvent.getEventHeaders()).thenReturn(eventHeaders);
        when(mockEvent.getEventBody()).thenReturn(eventBody);
        
        // 执行测试
        service.backgroundJobResultReceived(jobUuid, mockEvent);
        
        // 验证方法被调用
        verify(mockEvent, atLeastOnce()).getEventHeaders();
        verify(mockEvent, atLeastOnce()).getEventBody();
    }
    
    @Test
    void testHandleSuccessCase() {
        // 准备测试数据 - 成功情况
        String jobUuid = "test-success-uuid";
        Map<String, String> eventHeaders = new HashMap<>();
        eventHeaders.put("Job-Command", "originate");
        
        List<String> eventBody = Arrays.asList("+OK");
        
        when(mockEvent.getEventHeaders()).thenReturn(eventHeaders);
        when(mockEvent.getEventBody()).thenReturn(eventBody);
        
        // 执行测试
        service.backgroundJobResultReceived(jobUuid, mockEvent);
        
        // 验证方法被调用
        verify(mockEvent, atLeastOnce()).getEventHeaders();
        verify(mockEvent, atLeastOnce()).getEventBody();
    }
    
    @Test
    void testIgnoreNonOriginateCommand() {
        // 准备测试数据 - 非 originate 命令
        String jobUuid = "test-other-command-uuid";
        Map<String, String> eventHeaders = new HashMap<>();
        eventHeaders.put("Job-Command", "status");
        
        when(mockEvent.getEventHeaders()).thenReturn(eventHeaders);
        
        // 执行测试
        service.backgroundJobResultReceived(jobUuid, mockEvent);
        
        // 验证方法被调用
        verify(mockEvent).getEventHeaders();
        verify(mockEvent, never()).getEventBody();
    }
}
