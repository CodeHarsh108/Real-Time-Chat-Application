package com.harsh.chat.exception;

import org.springframework.http.HttpStatus;

public class RoomAlreadyExistsException extends BaseRuntimeException {

    private static final HttpStatus STATUS = HttpStatus.CONFLICT;
    private static final String ERROR_CODE = "ROOM_002";

    public RoomAlreadyExistsException(String roomId) {
        super("Room already exists with ID: " + roomId, STATUS, ERROR_CODE);
    }
}