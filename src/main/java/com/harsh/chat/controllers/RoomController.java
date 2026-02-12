package com.harsh.chat.controllers;

import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.Room;
import com.harsh.chat.repositories.MessageRepository;
import com.harsh.chat.repositories.RoomRepository;
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
public class RoomController {

    private RoomRepository roomRepository;

    private MessageRepository messageRepository;


    public RoomController(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @PostMapping
    public ResponseEntity<?> createRoom(@RequestBody Map<String, String> request) {
        String roomId = request.get("roomId");

        if (roomId == null || roomId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Room ID is required");
        }

        if (roomRepository.existsByRoomId(roomId)) {
            return ResponseEntity.badRequest().body("Room already exists!");
        }

        Room room = Room.builder()
                .roomId(roomId)
                .recentMessageIds(new ArrayList<>())
                .totalMessages(0)
                .build();

        Room savedRoom = roomRepository.save(room);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedRoom);
    }


    //get room: join
    @GetMapping("/{roomId}")
    public ResponseEntity<?> joinRoom(
            @PathVariable String roomId
    ) {

        Optional<Room> room = roomRepository.findByRoomId(roomId);
        if (room == null) {
            return ResponseEntity.badRequest()
                    .body("Room not found!!");
        }
        return ResponseEntity.ok(room);
    }



    @GetMapping("/{roomId}/messages")
    public ResponseEntity<List<Message>> getMessages(
            @PathVariable String roomId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<Message> messagePage = messageRepository.findByRoomIdOrderByTimestampDesc(roomId, pageable);
        return ResponseEntity.ok(messagePage.getContent());

    }


}