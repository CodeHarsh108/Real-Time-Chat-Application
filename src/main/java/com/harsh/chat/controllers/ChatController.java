package com.harsh.chat.controllers;

import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.User;
import com.harsh.chat.payload.MessageRequest;
import com.harsh.chat.payload.TypingIndicatorDTO;
import com.harsh.chat.payload.MessageResponse;
import com.harsh.chat.service.AuthService;
import com.harsh.chat.service.ChatService;
import com.harsh.chat.service.UserStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final AuthService authService;
    private final UserStatusService userStatusService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/sendMessage/{roomId}")
    @SendTo("/topic/room/{roomId}")
    public MessageResponse sendMessage(
            @DestinationVariable String roomId,
            @Payload MessageRequest request,
            Principal principal
    ) {
        String username = principal.getName();
        log.info("User {} sending message to room {}", username, roomId);

        request.setSender(username);

        if (!roomId.equals(request.getRoomId())) {
            throw new IllegalArgumentException("Room ID mismatch");
        }

        Message savedMessage = chatService.saveMessage(request);

        userStatusService.userStoppedTyping(username, roomId);

        return MessageResponse.from(savedMessage);
    }


    @MessageMapping("/typing/start/{roomId}")
    public void startTyping(
            @DestinationVariable String roomId,
            Principal principal
    ) {
        String username = principal.getName();
        log.debug("User {} started typing in room {}", username, roomId);

        userStatusService.userTyping(username, roomId);
    }


    @MessageMapping("/typing/stop/{roomId}")
    public void stopTyping(
            @DestinationVariable String roomId,
            Principal principal
    ) {
        String username = principal.getName();
        log.debug("User {} stopped typing in room {}", username, roomId);

        userStatusService.userStoppedTyping(username, roomId);
    }


    @MessageMapping("/join/{roomId}")
    @SendTo("/topic/room/{roomId}/status")
    public Map<String, Object> joinRoom(
            @DestinationVariable String roomId,
            Principal principal
    ) {
        String username = principal.getName();
        log.info("User {} joining room {}", username, roomId);

        userStatusService.userJoinedRoom(username, roomId);

        return Map.of(
                "type", "USER_JOINED",
                "username", username,
                "roomId", roomId,
                "timestamp", System.currentTimeMillis()
        );
    }


    @MessageMapping("/leave/{roomId}")
    @SendTo("/topic/room/{roomId}/status")
    public Map<String, Object> leaveRoom(
            @DestinationVariable String roomId,
            Principal principal
    ) {
        String username = principal.getName();
        log.info("User {} leaving room {}", username, roomId);

        userStatusService.userLeftRoom(username, roomId);

        return Map.of(
                "type", "USER_LEFT",
                "username", username,
                "roomId", roomId,
                "timestamp", System.currentTimeMillis()
        );
    }


    @MessageMapping("/users/{roomId}")
    @SendTo("/topic/room/{roomId}/users")
    public Map<String, Object> getOnlineUsers(
            @DestinationVariable String roomId
    ) {
        return Map.of(
                "type", "ROOM_USERS",
                "roomId", roomId,
                "users", userStatusService.getOnlineUsersInRoom(roomId),
                "count", userStatusService.getOnlineUsersCount(roomId),
                "timestamp", System.currentTimeMillis()
        );
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