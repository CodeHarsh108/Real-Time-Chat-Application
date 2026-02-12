package com.harsh.chat.controllers;


import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.Room;
import com.harsh.chat.payload.MessageRequest;
import com.harsh.chat.repositories.MessageRepository;
import com.harsh.chat.repositories.RoomRepository;
import com.harsh.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatController {


    private final ChatService chatService;

    @MessageMapping("/sendMessage/{roomId}")
    @SendTo("/topic/room/{roomId}")
    public Message sendMessage(
            @DestinationVariable String roomId,
            @Payload MessageRequest request
    ) {
        log.info("Received message for room {}: {}", roomId, request);

        if (!roomId.equals(request.getRoomId())) {
            throw new IllegalArgumentException("Room ID in path and body do not match");
        }

        return chatService.saveMessage(request);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleException(Exception exception) {
        log.error("WebSocket error: {}", exception.getMessage());
        return "Error: " + exception.getMessage();
    }

}