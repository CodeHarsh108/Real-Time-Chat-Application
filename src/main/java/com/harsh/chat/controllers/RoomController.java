package com.harsh.chat.controllers;

import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.Room;
import com.harsh.chat.payload.CreateRoomRequest;
import com.harsh.chat.payload.MessageResponse;
import com.harsh.chat.payload.RoomResponse;
import com.harsh.chat.service.ChatService;
import com.harsh.chat.service.UserStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@Slf4j
public class RoomController {

    private final ChatService chatService;

    private final UserStatusService userStatusService;

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            Authentication authentication
    ) {
        String username = authentication.getName();
        log.info("User {} creating room: {}", username, request.getRoomId());

        Room room = chatService.createRoom(request.getRoomId());
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomResponse.from(room));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> joinRoom(
            @PathVariable String roomId,
            Authentication authentication
    ) {
        String username = authentication.getName();
        log.debug("User {} joining room: {}", username, roomId);

        Room room = chatService.getRoom(roomId);
        return ResponseEntity.ok(RoomResponse.from(room));
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        String username = authentication.getName();
        log.debug("User {} fetching messages for room: {}", username, roomId);

        List<Message> messages = chatService.getMessages(roomId, page, size);
        List<MessageResponse> response = messages.stream()
                .map(MessageResponse::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{roomId}/exists")
    public ResponseEntity<Boolean> checkRoomExists(@PathVariable String roomId) {
        boolean exists = chatService.roomExists(roomId);
        return ResponseEntity.ok(exists);
    }

    @GetMapping("/{roomId}/users/online")
    public ResponseEntity<?> getOnlineUsersInRoom(@PathVariable String roomId) {
        log.debug("REST request for online users in room: {}", roomId);

        Set<String> onlineUsers = userStatusService.getOnlineUsersInRoom(roomId);
        Long count = userStatusService.getOnlineUsersCount(roomId);

        return ResponseEntity.ok(Map.of(
                "roomId", roomId,
                "users", onlineUsers,
                "count", count
        ));
    }

    @GetMapping("/{roomId}/users/{username}/online")
    public ResponseEntity<?> isUserOnline(
            @PathVariable String roomId,
            @PathVariable String username
    ) {
        boolean isOnline = userStatusService.isUserOnlineInRoom(username, roomId);

        return ResponseEntity.ok(Map.of(
                "username", username,
                "roomId", roomId,
                "online", isOnline
        ));
    }

    @GetMapping("/{roomId}/typing")
    public ResponseEntity<?> getUsersTyping(@PathVariable String roomId) {
        Set<String> typingUsers = userStatusService.getUsersTyping(roomId);

        return ResponseEntity.ok(Map.of(
                "roomId", roomId,
                "typing", typingUsers,
                "count", typingUsers.size()
        ));
    }
}