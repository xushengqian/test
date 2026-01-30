package com.freeswitch.esl.exception;

/**
 * ESL 连接异常
 */
public class EslConnectionException extends EslException {

    private static final long serialVersionUID = 1L;

    public EslConnectionException(String message) {
        super("ESL_CONN_ERROR", message);
    }

    public EslConnectionException(String message, Throwable cause) {
        super("ESL_CONN_ERROR", message, cause);
    }
}
