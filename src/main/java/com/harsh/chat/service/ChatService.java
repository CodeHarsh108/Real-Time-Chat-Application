package com.harsh.chat.service;

import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.Room;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final RoomRepository roomRepository;
    private final MessageRepository messageRepository;
    private final RedisService redisService;
    private final RedisTemplate redisTemplate;

    @Transactional
    public Room createRoom(String roomId) {
        log.info("Creating room with ID: {}", roomId);

        if (roomRepository.existsByRoomId(roomId)) {
            throw new IllegalArgumentException("Room already exists: " + roomId);
        }

        Room room = Room.builder()
                .roomId(roomId)
                .recentMessageIds(new ArrayList<>())
                .totalMessages(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Room savedRoom = roomRepository.save(room);

        // Cache the room
        try {
            redisService.cacheRoom(savedRoom);
        } catch (Exception e) {
            log.warn("Failed to cache room, but room was created: {}", e.getMessage());
        }

        log.info("Room created: {}", roomId);
        return savedRoom;
    }


    @Transactional(readOnly = true)
    @Cacheable(value = "roomInfo", key = "#roomId", unless = "#result == null")
    public Room getRoom(String roomId) {
        log.info("Fetching room: {}", roomId);

        // Try Redis cache first with error handling
        try {
            Room cachedRoom = redisService.getCachedRoom(roomId);
            if (cachedRoom != null) {
                log.info("Room found in cache: {}", roomId);
                return cachedRoom;
            }
        } catch (Exception e) {
            log.warn("Redis cache error, falling back to database: {}", e.getMessage());
        }

        // Fallback to database
        Room room = roomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        // Try to cache for next time (don't fail if caching fails)
        try {
            redisService.cacheRoom(room);
        } catch (Exception e) {
            log.warn("Failed to cache room after database fetch: {}", e.getMessage());
        }

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

        // Rate limiting
        String rateKey = "msg:" + request.getRoomId() + ":" + request.getSender();
        try {
            if (!redisService.checkRateLimit(rateKey, 10, 60)) {
                throw new RuntimeException("Rate limit exceeded. Too many messages.");
            }
        } catch (Exception e) {
            log.warn("Rate limiting failed, proceeding anyway: {}", e.getMessage());
        }

        Room room = roomRepository.findByRoomId(request.getRoomId())
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + request.getRoomId()));

        Message message = Message.builder()
                .roomId(request.getRoomId())
                .sender(request.getSender())
                .content(request.getContent())
                .timestamp(LocalDateTime.now())
                .build();

        Message savedMessage = messageRepository.save(message);

        // Update room
        room.addMessage(savedMessage.getId());
        roomRepository.save(room);

        // Try to cache the message (don't fail if caching fails)
        try {
            redisService.cacheMessage(request.getRoomId(), savedMessage);
            redisService.cacheRoom(room);
        } catch (Exception e) {
            log.warn("Failed to cache message, but message was saved: {}", e.getMessage());
        }

        log.info("Message saved with ID: {}", savedMessage.getId());
        return savedMessage;
    }


    @Transactional(readOnly = true)
    public List<Message> getMessages(String roomId, int page, int size) {
        log.debug("Fetching messages for room: {}, page: {}, size: {}", roomId, page, size);

        // For page 0, try cache first with error handling
        if (page == 0) {
            try {
                List<Message> cachedMessages = redisService.getRecentMessages(roomId);
                if (!cachedMessages.isEmpty()) {
                    log.info("Returning {} messages from cache for room: {}", cachedMessages.size(), roomId);

                    if (cachedMessages.size() >= size) {
                        return cachedMessages.subList(0, Math.min(size, cachedMessages.size()));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get messages from cache: {}", e.getMessage());
            }
        }

        // Fallback to database
        if (!roomRepository.existsByRoomId(roomId)) {
            throw new RoomNotFoundException("Room not found: " + roomId);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<Message> messagesPage = messageRepository.findByRoomIdOrderByTimestampDesc(roomId, pageable);

        List<Message> messages = messagesPage.getContent();

        // Try to cache messages for future requests
        if (page == 0 && !messages.isEmpty()) {
            try {
                messages.forEach(msg -> redisService.cacheMessage(roomId, msg));
            } catch (Exception e) {
                log.warn("Failed to cache messages: {}", e.getMessage());
            }
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

    @Transactional
    public Message saveAttachmentMessage(Message message) {
        log.info("Saving attachment message in room: {} from sender: {}",
                message.getRoomId(), message.getSender());

        // Verify room exists
        Room room = roomRepository.findByRoomId(message.getRoomId())
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + message.getRoomId()));

        // Save message - this should preserve ALL fields
        Message savedMessage = messageRepository.save(message);

        // LOG ALL FIELDS to verify they're saved
        log.info("MESSAGE SAVED - ID: {}, hasAttachment: {}, type: {}, url: {}, name: {}",
                savedMessage.getId(),
                savedMessage.isHasAttachment(),
                savedMessage.getAttachmentType(),
                savedMessage.getAttachmentUrl(),
                savedMessage.getAttachmentName());

        // Update room with message reference
        room.addMessage(savedMessage.getId());
        roomRepository.save(room);

        // Cache the message
        redisService.cacheMessage(message.getRoomId(), savedMessage);

        return savedMessage;
    }
}
