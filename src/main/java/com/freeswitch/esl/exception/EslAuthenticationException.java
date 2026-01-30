package com.freeswitch.esl.exception;

/**
 * ESL 认证异常
 */
public class EslAuthenticationException extends EslException {

    private static final long serialVersionUID = 1L;

    public EslAuthenticationException(String message) {
        super("ESL_AUTH_ERROR", message);
    }

    public EslAuthenticationException(String message, Throwable cause) {
        super("ESL_AUTH_ERROR", message, cause);
    }
}
