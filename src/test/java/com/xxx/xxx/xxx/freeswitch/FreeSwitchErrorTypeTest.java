package com.xxx.xxx.xxx.freeswitch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FreeSwitchErrorType 测试类
 */
class FreeSwitchErrorTypeTest {
    
    @Test
    void testFromErrorMessage_DestinationOutOfOrder() {
        String errorMessage = "-ERR DESTINATION_OUT_OF_ORDER";
        FreeSwitchErrorType errorType = FreeSwitchErrorType.fromErrorMessage(errorMessage);
        
        assertEquals(FreeSwitchErrorType.DESTINATION_OUT_OF_ORDER, errorType);
    }
    
    @Test
    void testFromErrorMessage_UnknownError() {
        String errorMessage = "-ERR UNKNOWN_ERROR";
        FreeSwitchErrorType errorType = FreeSwitchErrorType.fromErrorMessage(errorMessage);
        
        assertEquals(FreeSwitchErrorType.UNKNOWN_ERROR, errorType);
    }
    
    @Test
    void testFromErrorMessage_Null() {
        FreeSwitchErrorType errorType = FreeSwitchErrorType.fromErrorMessage(null);
        
        assertEquals(FreeSwitchErrorType.UNKNOWN_ERROR, errorType);
    }
    
    @Test
    void testGetErrorMessage() {
        assertEquals("-ERR DESTINATION_OUT_OF_ORDER", 
                    FreeSwitchErrorType.DESTINATION_OUT_OF_ORDER.getErrorMessage());
        assertNull(FreeSwitchErrorType.UNKNOWN_ERROR.getErrorMessage());
    }
}
