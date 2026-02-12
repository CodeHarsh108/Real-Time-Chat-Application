package com.harsh.chat.controllers;

import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.Room;
import com.harsh.chat.payload.CreateRoomRequest;
import com.harsh.chat.repositories.MessageRepository;
import com.harsh.chat.repositories.RoomRepository;
import com.harsh.chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@Slf4j
public class RoomController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<?> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        log.info("Create room request: {}", request.getRoomId());

        try {
            Room room = chatService.createRoom(request.getRoomId());
            return ResponseEntity.status(HttpStatus.CREATED).body(room);
        } catch (IllegalArgumentException e) {
            log.warn("Room creation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<?> joinRoom(@PathVariable String roomId) {
        log.debug("Join room request: {}", roomId);

        try {
            Room room = chatService.getRoom(roomId);
            return ResponseEntity.ok(room);
        } catch (Exception e) {
            log.warn("Room not found: {}", roomId);
            return ResponseEntity.badRequest().body("Room not found!!");
        }
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<List<Message>> getMessages(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.debug("Get messages request for room: {}, page: {}, size: {}", roomId, page, size);

        try {
            List<Message> messages = chatService.getMessages(roomId, page, size);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            log.warn("Failed to get messages: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }



}