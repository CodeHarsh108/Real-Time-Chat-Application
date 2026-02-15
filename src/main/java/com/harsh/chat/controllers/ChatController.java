package com.harsh.chat.controllers;

import com.harsh.chat.entity.Message;
import com.harsh.chat.payload.MessageRequest;
import com.harsh.chat.payload.MessageResponse;
import com.harsh.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/sendMessage/{roomId}")
    @SendTo("/topic/room/{roomId}")
    public MessageResponse sendMessage(
            @DestinationVariable String roomId,
            @Payload MessageRequest request,
            Principal principal  // This will now be populated by the interceptor
    ) {
        // Add null check for safety
        if (principal == null) {
            log.error("No authenticated principal found for WebSocket connection");
            throw new IllegalArgumentException("Not authenticated");
        }

        // Get username from principal
        String username;
        if (principal instanceof UsernamePasswordAuthenticationToken) {
            UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) principal;
            UserDetails userDetails = (UserDetails) auth.getPrincipal();
            username = userDetails.getUsername();
        } else {
            username = principal.getName();
        }

        log.info("User {} sending message to room {}", username, roomId);

        // Set sender from authenticated user (cannot spoof)
        request.setSender(username);

        if (!roomId.equals(request.getRoomId())) {
            throw new IllegalArgumentException("Room ID in path and body do not match");
        }

        Message savedMessage = chatService.saveMessage(request);
        return MessageResponse.from(savedMessage);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleWebSocketException(Exception exception, Principal principal) {
        log.error("WebSocket error for user {}: {}",
                principal != null ? principal.getName() : "anonymous",
                exception.getMessage());
        return "Error: " + exception.getMessage();
    }
}