package com.harsh.chat.exception;

import org.springframework.http.HttpStatus;

public class MessageSendException extends BaseRuntimeException {

    private static final HttpStatus STATUS = HttpStatus.INTERNAL_SERVER_ERROR;
    private static final String ERROR_CODE = "MSG_001";

    public MessageSendException(String message) {
        super(message, STATUS, ERROR_CODE);
    }

    public MessageSendException(String message, Throwable cause) {
        super(message, STATUS, ERROR_CODE, cause);
    }
}