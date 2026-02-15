package com.harsh.chat.service;

import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.Room;
import com.harsh.chat.exception.InvalidRoomIdException;
import com.harsh.chat.exception.MessageSendException;
import com.harsh.chat.exception.RoomAlreadyExistsException;
import com.harsh.chat.exception.RoomNotFoundException;
import com.harsh.chat.payload.MessageRequest;
import com.harsh.chat.repositories.MessageRepository;
import com.harsh.chat.repositories.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final RoomRepository roomRepository;
    private final MessageRepository messageRepository;
    private final RedisService redisService;

    @Transactional
    public Room createRoom(String roomId) {
        log.info("Creating room with ID: {}", roomId);

        if (roomId == null || roomId.trim().isEmpty()) {
            throw new InvalidRoomIdException("Room ID cannot be empty");
        }

        if (!roomId.matches("^[a-zA-Z0-9_-]{3,50}$")) {
            throw new InvalidRoomIdException(
                    "Room ID must be 3-50 characters and contain only letters, numbers, hyphens, and underscores"
            );
        }

        if (roomRepository.existsByRoomId(roomId)) {
            throw new RoomAlreadyExistsException(roomId);
        }

        Room room = Room.builder()
                .roomId(roomId)
                .recentMessageIds(new java.util.ArrayList<>())
                .totalMessages(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Room savedRoom = roomRepository.save(room);

        redisService.cacheRoom(savedRoom);

        log.info("Room created and cached successfully: {}", roomId);
        return savedRoom;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "roomInfo", key = "#roomId", unless = "#result == null")
    public Room getRoom(String roomId) {
        log.debug("Fetching room: {}", roomId);

        Room cachedRoom = redisService.getCachedRoom(roomId);
        if (cachedRoom != null){
            log.info("Room found in cache : {}", roomId);
            return cachedRoom;
        }

        Room room = roomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));
        redisService.cacheRoom(room);
        return room;
    }

    @CacheEvict(value = "roomInfo", key = "#roomId")
    public void evictRoomCache(String roomId) {
        log.info("Evicting room from cache: {}", roomId);
        redisService.evictRoom(roomId);
    }



    @Transactional
    public Message saveMessage(MessageRequest request) {
        log.info("Saving message in room: {} from sender: {}", request.getRoomId(), request.getSender());

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }

        String rateKey = "msg: " + request.getRoomId() + ":" + request.getSender();
        if (!redisService.checkRateLimit(rateKey, 10, 60)){
            throw new RuntimeException("Rate limit exceeded. Too many messages.");
        }

        Room room = roomRepository.findByRoomId(request.getRoomId())
                .orElseThrow(() -> new RoomNotFoundException(request.getRoomId()));

        Message message = Message.builder()
                .roomId(request.getRoomId())
                .sender(request.getSender())
                .content(request.getContent())
                .timestamp(LocalDateTime.now())
                .build();

        try {
            Message savedMessage = messageRepository.save(message);
            room.addMessage(savedMessage.getId());
            roomRepository.save(room);
            redisService.cacheMessage(request.getRoomId(), savedMessage);
            redisService.cacheRoom(room);
            return savedMessage;
        } catch (Exception e) {
            log.error("Failed to save message in room: {}", request.getRoomId(), e);
            throw new MessageSendException("Failed to send message", e);
        }

    }

    @Transactional(readOnly = true)
    public List<Message> getMessages(String roomId, int page, int size) {
        log.debug("Fetching messages for room: {}, page: {}, size: {}", roomId, page, size);
        // For page 0 (most recent), try cache first
        if (page == 0) {
            List<Message> cachedMessages = redisService.getRecentMessages(roomId);
            if (!cachedMessages.isEmpty()) {
                log.info("Returning {} messages from cache for room: {}", cachedMessages.size(), roomId);
                // If cached messages are less than requested size, get from DB
                if (cachedMessages.size() >= size) {
                    return cachedMessages.subList(0, Math.min(size, cachedMessages.size()));
                }
            }
        }
        // Fallback to database
        if (!roomRepository.existsByRoomId(roomId)) {
            throw new RoomNotFoundException("Room not found: " + roomId);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<Message> messagesPage = messageRepository.findByRoomIdOrderByTimestampDesc(roomId, pageable);
        List<Message> messages = messagesPage.getContent();
        if (page == 0 && !messages.isEmpty()) {
            messages.forEach(msg -> redisService.cacheMessage(roomId, msg));
        }
        return messages;
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



    public void userJoined(String username, String roomId) {
        redisService.userOnline(username);
        log.info("User {} joined room {}", username, roomId);
    }

    public void userLeft(String username, String roomId) {
        redisService.userOffline(username);
        log.info("User {} left room {}", username, roomId);
    }

    public boolean isUserOnline(String username) {
        return redisService.isUserOnline(username);
    }

    public Set<Object> getOnlineUsers() {
        return redisService.getOnlineUsers();
    }




}
