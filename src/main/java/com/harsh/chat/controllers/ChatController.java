package com.harsh.chat.controllers;

import com.harsh.chat.entity.Message;
import com.harsh.chat.payload.MessageRequest;
import com.harsh.chat.payload.MessageResponse;
import com.harsh.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/sendMessage/{roomId}")
    @SendTo("/topic/room/{roomId}")
    public MessageResponse sendMessage(
            @DestinationVariable String roomId,
            @Payload MessageRequest request
    ) {
        log.info("Received message for room {} from sender: {}", roomId, request.getSender());

        if (!roomId.equals(request.getRoomId())) {
            throw new IllegalArgumentException("Room ID in path and body do not match");
        }

        Message savedMessage = chatService.saveMessage(request);
        return MessageResponse.from(savedMessage);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleWebSocketException(Exception exception) {
        log.error("WebSocket error: {}", exception.getMessage());
        return "Error: " + exception.getMessage();
    }
}