package com.harsh.chat.exception;

import org.springframework.http.HttpStatus;

public class WebSocketAuthenticationException extends BaseRuntimeException {

    private static final HttpStatus STATUS = HttpStatus.UNAUTHORIZED;
    private static final String ERROR_CODE = "WS_001";

    public WebSocketAuthenticationException(String message) {
        super(message, STATUS, ERROR_CODE);
    }
}