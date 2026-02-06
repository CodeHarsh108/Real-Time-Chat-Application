package com.harsh.chat.exceptions;

import org.springframework.http.HttpStatus;

public class RoomNotFoundException extends RuntimeException {
    public RoomNotFoundException(String message) {
        super(message);
    }
}