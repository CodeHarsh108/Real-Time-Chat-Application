package com.harsh.chat.service;

import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.Room;
import com.harsh.chat.exception.RoomNotFoundException;
import com.harsh.chat.payload.MessageRequest;
import com.harsh.chat.repositories.MessageRepository;
import com.harsh.chat.repositories.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final RoomRepository roomRepository;
    private final MessageRepository messageRepository;

    @Transactional
    public Room createRoom(String roomId) {
        log.info("Creating room with ID: {}", roomId);
        if (roomRepository.existsByRoomId(roomId)) {
            throw new IllegalArgumentException("Room already exists: " + roomId);
        }
        Room room = Room.builder()
                .roomId(roomId)
                .recentMessageIds(new java.util.ArrayList<>())
                .totalMessages(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Room savedRoom = roomRepository.save(room);
        log.info("Room created successfully: {}", roomId);
        return savedRoom;
    }

    @Transactional(readOnly = true)
    public Room getRoom(String roomId) {
        log.debug("Fetching room: {}", roomId);
        return roomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));
    }



    @Transactional
    public Message saveMessage(MessageRequest request) {
        log.info("Saving message in room: {} from sender: {}", request.getRoomId(), request.getSender());
        Room room = roomRepository.findByRoomId(request.getRoomId())
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + request.getRoomId()));
        Message message = Message.builder()
                .roomId(request.getRoomId())
                .sender(request.getSender())
                .content(request.getContent())
                .timestamp(LocalDateTime.now())
                .build();
        Message savedMessage = messageRepository.save(message);
        room.addMessage(savedMessage.getId());
        roomRepository.save(room);
        log.info("Message saved successfully with ID: {}", savedMessage.getId());
        return savedMessage;
    }

    @Transactional(readOnly = true)
    public List<Message> getMessages(String roomId, int page, int size) {
        log.debug("Fetching messages for room: {}, page: {}, size: {}", roomId, page, size);
        if (!roomRepository.existsByRoomId(roomId)) {
            throw new RoomNotFoundException("Room not found: " + roomId);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<Message> messagesPage = messageRepository.findByRoomIdOrderByTimestampDesc(roomId, pageable);
        return messagesPage.getContent();
    }


    @Transactional(readOnly = true)
    public List<Message> getRecentMessages(String roomId, int limit) {
        log.debug("Fetching recent {} messages for room: {}", limit, roomId);
        return messageRepository.findTop50ByRoomIdOrderByTimestampDesc(roomId)
                .stream()
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getMessageCount(String roomId) {
        return messageRepository.countByRoomId(roomId);
    }

    @Transactional(readOnly = true)
    public boolean roomExists(String roomId) {
        return roomRepository.existsByRoomId(roomId);
    }



}
