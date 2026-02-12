package com.harsh.chat.controllers;

import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.Room;
import com.harsh.chat.exception.RoomNotFoundException;
import com.harsh.chat.payload.CreateRoomRequest;
import com.harsh.chat.payload.ErrorResponse;
import com.harsh.chat.payload.MessageResponse;
import com.harsh.chat.payload.RoomResponse;
import com.harsh.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@Slf4j
public class RoomController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        log.info("Create room request: {}", request.getRoomId());
        Room room = chatService.createRoom(request.getRoomId());
        RoomResponse response = RoomResponse.from(room);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> joinRoom(@PathVariable String roomId) {
        log.debug("Join room request: {}", roomId);
        Room room = chatService.getRoom(roomId);
        RoomResponse response = RoomResponse.from(room);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.debug("Get messages request for room: {}, page: {}, size: {}", roomId, page, size);
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



    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoomNotFound(
            RoomNotFoundException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error("Room Not Found")
                .message(ex.getMessage())
                .path(request.getDescription(false))
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .message(ex.getMessage())
                .path(request.getDescription(false))
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}