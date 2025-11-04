package com.oc.c.c.f;

import org.freeswitch.esl.client.transport.event.EslEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        // 模拟 DESTINATION_OUT_OF_ORDER 错误
        String jobUuid = "1550d668-8d83-4593-985e-492ca034c02c";
        String[] eventBody = {"-ERR DESTINATION_OUT_OF_ORDER"};
        
        when(mockEvent.getEventBody()).thenReturn(eventBody);
        when(mockEvent.getEventHeader("Job-Command-Arg"))
            .thenReturn("{session_type=3,object_type=1,ignore_early_media=false,origination_caller_id_number='gw113120002026',uuid='yrRobot_202510231651440005437',media_bug_answer_req=false,execute_on_media=lua::pre_media_event.lua}sofia/gateway/gwopensips/13390118999 &lua(ivrbot-nopause.lua)");
        
        // 执行测试
        service.backgroundJobResultReceived(jobUuid, mockEvent);
        
        // 验证事件体被正确读取
        verify(mockEvent, atLeastOnce()).getEventBody();
    }
}
