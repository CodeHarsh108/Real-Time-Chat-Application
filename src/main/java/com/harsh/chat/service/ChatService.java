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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final EncryptionService encryptionService;

    @Transactional
    public Room createRoom(String roomId) {
        log.info("Creating room with ID: {}", roomId);

        if (roomRepository.existsByRoomId(roomId)) {
            throw new IllegalArgumentException("Room already exists: " + roomId);
        }

//        String encryptedKey = null;
//        try {
//            encryptedKey = encryptionService.generateRoomKey(roomId);
//            log.info("Encryption key generated for room: {}", roomId);
//        } catch (Exception e) {
//            log.warn("Failed to generate encryption key, continuing without encryption: {}", e.getMessage());
//        }

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

        // Rate limiting: max 10 messages per minute per user per room
        String rateKey = "msg:" + request.getRoomId() + ":" + request.getSender();
        if (!redisService.checkRateLimit(rateKey, 10, 60)) {
            throw new RuntimeException("Rate limit exceeded. Too many messages.");
        }

        Room room = roomRepository.findByRoomId(request.getRoomId())
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + request.getRoomId()));


//        // Try to encrypt if room is encrypted
//        if (room.isEncrypted()) {
//            try {
//                String encryptedMessage = encryptionService.encryptMessage(request.getContent(), request.getRoomId());
//                String[] parts = encryptedMessage.split(":");
//                String iv = parts[0];
//                String encryptedContent = parts[1];
//
//                message = Message.createEncrypted(
//                        request.getRoomId(),
//                        request.getSender(),
//                        encryptedContent,
//                        iv
//                );
//                log.debug("Message encrypted for room: {}", request.getRoomId());
//            } catch (Exception e) {
//                log.error("Encryption failed, falling back to plain text: {}", e.getMessage());
//                message = Message.create(
//                        request.getRoomId(),
//                        request.getSender(),
//                        request.getContent()
//                );
//            }
//        } else {
//            message = Message.create(
//                    request.getRoomId(),
//                    request.getSender(),
//                    request.getContent()
//            );
//        }

        Message message = Message.create(
                request.getRoomId(),
                request.getSender(),
                request.getContent()
        );

        Message savedMessage = messageRepository.save(message);

        // Update room with message reference
        room.addMessage(savedMessage.getId());
        roomRepository.save(room);

        // Cache the message
        redisService.cacheMessage(request.getRoomId(), savedMessage);

        log.info("Message saved with ID: {}, status: {}", savedMessage.getId(), savedMessage.getStatus());

        return savedMessage;
    }


    @Transactional(readOnly = true)
    public List<Message> getMessages(String roomId, int page, int size) {
        log.debug("Fetching messages for room: {}, page: {}, size: {}", roomId, page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        List<Message> messages = messageRepository.findByRoomIdOrderByTimestampDesc(roomId, pageable).getContent();

        // 🔥 FIX: Decrypt messages if needed
//        for (Message message : messages) {
//            if (message.isEncrypted()) {
//                try {
//                    String encryptedMessage = message.getEncryptionIv() + ":" + message.getEncryptedContent();
//                    log.debug("Decrypting message {} with IV: {}", message.getId(), message.getEncryptionIv());
//                    String decrypted = encryptionService.decryptMessage(encryptedMessage, roomId);
//                    message.setContent(decrypted);
//                    log.debug("Successfully decrypted message: {}", message.getId());
//                } catch (Exception e) {
//                    log.error("Failed to decrypt message: {}", message.getId(), e);
//                    message.setContent("[Encrypted message - Unable to decrypt]");
//                }
//            }
//        }

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

//        if (room.isEncrypted() && message.getContent() != null && !message.getContent().isEmpty()) {
//            try {
//                String encryptedMessage = encryptionService.encryptMessage(message.getContent(), message.getRoomId());
//                String[] parts = encryptedMessage.split(":");
//                message.setEncryptedContent(parts[1]);
//                message.setEncryptionIv(parts[0]);
//                message.setContent(null);
//                message.setEncrypted(true);
//            } catch (Exception e) {
//                log.error("Failed to encrypt attachment message content: {}", e.getMessage());
//            }
//        }


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
