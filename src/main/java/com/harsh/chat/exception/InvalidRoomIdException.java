package com.harsh.chat.exception;

import org.springframework.http.HttpStatus;

public class InvalidRoomIdException extends BaseRuntimeException {

    private static final HttpStatus STATUS = HttpStatus.BAD_REQUEST;
    private static final String ERROR_CODE = "ROOM_003";

    public InvalidRoomIdException(String message) {
        super(message, STATUS, ERROR_CODE);
    }
}