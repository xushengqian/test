package com.freeswitch.esl.exception;

/**
 * ESL 命令执行异常
 */
public class EslCommandException extends EslException {

    private static final long serialVersionUID = 1L;

    private String command;

    public EslCommandException(String command, String message) {
        super("ESL_CMD_ERROR", message);
        this.command = command;
    }

    public EslCommandException(String command, String message, Throwable cause) {
        super("ESL_CMD_ERROR", message, cause);
        this.command = command;
    }

    public String getCommand() {
        return command;
    }
}
