package com.freeswitch.esl.exception;

/**
 * FreeSWITCH ESL 异常基类
 */
public class EslException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private String errorCode;

    public EslException(String message) {
        super(message);
    }

    public EslException(String message, Throwable cause) {
        super(message, cause);
    }

    public EslException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public EslException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
