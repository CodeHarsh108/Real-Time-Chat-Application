// Updated RoomController.java
package com.harsh.chat.controllers;

import com.harsh.chat.dto.RoomDTO;
import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.Room;
import com.harsh.chat.services.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<Room> createRoom(@RequestBody String roomId) {
        Room room = chatService.createRoom(roomId);
        return ResponseEntity.status(HttpStatus.CREATED).body(room);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<Room> getRoom(@PathVariable String roomId) {
        Room room = chatService.getRoom(roomId);
        return ResponseEntity.ok(room);
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<Page<Message>> getMessages(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<Message> messages = chatService.getMessages(
                roomId,
                PageRequest.of(page, size)
        );
        return ResponseEntity.ok(messages);
    }

    @GetMapping
    public ResponseEntity<List<RoomDTO>> getAllRooms() {
        List<RoomDTO> rooms = chatService.getAllRooms();
        return ResponseEntity.ok(rooms);
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(@PathVariable String roomId) {
        // Implement delete logic
        return ResponseEntity.noContent().build();
    }
}