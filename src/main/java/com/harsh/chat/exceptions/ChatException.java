package com.harsh.chat.exceptions;
import org.springframework.http.HttpStatus;


public class ChatException extends RuntimeException {
    private HttpStatus status;

    public ChatException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}