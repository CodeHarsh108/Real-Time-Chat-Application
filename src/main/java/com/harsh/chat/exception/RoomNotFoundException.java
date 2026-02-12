package com.harsh.chat.exception;

import org.springframework.http.HttpStatus;

public class RoomNotFoundException extends BaseRuntimeException {

    private static final HttpStatus STATUS = HttpStatus.NOT_FOUND;
    private static final String ERROR_CODE = "ROOM_001";

    public RoomNotFoundException(String roomId) {
        super("Room not found with ID: " + roomId, STATUS, ERROR_CODE);
    }

    public RoomNotFoundException(String roomId, Throwable cause) {
        super("Room not found with ID: " + roomId, STATUS, ERROR_CODE, cause);
    }
}