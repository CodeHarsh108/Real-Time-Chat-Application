package com.harsh.chat.service;

import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.MessageStatus;
import com.harsh.chat.payload.ReadReceiptDTO;
import com.harsh.chat.repositories.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReadReceiptService {

    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserStatusService userStatusService;

    private static final String MESSAGE_STATUS_PREFIX = "msg:status:";
    private static final String USER_LAST_READ_PREFIX = "user:lastread:";


    @Transactional
    public void markAsDelivered(String messageId, String username, String roomId) {
        log.info("Marking message {} as DELIVERED for user {} in room {}", messageId, username, roomId);
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found: " + messageId));
        // Don't mark sender's own message as delivered
        if (message.getSender().equals(username)) {
            return;
        }
        message.markAsDelivered(username);
        Message savedMessage = messageRepository.save(message);
        // Update Redis cache
        updateMessageStatusInRedis(savedMessage);
        // Broadcast delivery receipt
        ReadReceiptDTO receipt = ReadReceiptDTO.builder()
                .type("DELIVERED")
                .messageId(messageId)
                .roomId(roomId)
                .username(username)
                .status(MessageStatus.DELIVERED)
                .timestamp(LocalDateTime.now())
                .deliveredTo(message.getDeliveredTo())
                .build();

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/receipts", receipt);

        log.info("Message {} marked as DELIVERED for {}", messageId, username);
    }


    @Transactional
    public void markAsRead(String messageId, String username, String roomId) {
        log.info("Marking message {} as READ for user {} in room {}", messageId, username, roomId);
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found: " + messageId));
        // Don't mark sender's own message
        if (message.getSender().equals(username)) {
            return;
        }
        message.markAsRead(username);
        Message savedMessage = messageRepository.save(message);
        // Update Redis cache
        updateMessageStatusInRedis(savedMessage);
        // Update user's last read timestamp
        updateUserLastRead(username, roomId, messageId);
        // Broadcast read receipt
        ReadReceiptDTO receipt = ReadReceiptDTO.builder()
                .type("READ")
                .messageId(messageId)
                .roomId(roomId)
                .username(username)
                .status(MessageStatus.READ)
                .timestamp(LocalDateTime.now())
                .readBy(message.getReadBy())
                .build();

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/receipts", receipt);

        log.info("Message {} marked as READ for {}", messageId, username);
    }


    @Transactional
    public void markBulkAsRead(Set<String> messageIds, String username, String roomId) {
        log.info("Marking {} messages as READ for user {} in room {}", messageIds.size(), username, roomId);

        Set<Message> messages = messageRepository.findAllById(messageIds).stream()
                .filter(msg -> !msg.getSender().equals(username))
                .filter(msg -> !msg.isReadBy(username))
                .collect(Collectors.toSet());

        for (Message message : messages) {
            message.markAsRead(username);
        }

        messageRepository.saveAll(messages);
        // Update Redis cache for each message
        messages.forEach(this::updateMessageStatusInRedis);
        // Update user's last read timestamp
        updateUserLastRead(username, roomId, messages.stream()
                .map(Message::getId)
                .findFirst().orElse(null));
        // Broadcast bulk read receipt
        ReadReceiptDTO receipt = ReadReceiptDTO.builder()
                .type("BULK_READ")
                .roomId(roomId)
                .username(username)
                .status(MessageStatus.READ)
                .timestamp(LocalDateTime.now())
                .readBy(messages.stream().map(Message::getReadBy).flatMap(Set::stream).collect(Collectors.toSet()))
                .build();

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/receipts", receipt);
    }

    public MessageStatus getMessageStatus(String messageId, String username) {
        // Try Redis first
        String key = MESSAGE_STATUS_PREFIX + messageId + ":" + username;
        Object status = redisTemplate.opsForValue().get(key);

        if (status != null) {
            return MessageStatus.valueOf(status.toString());
        }

        // Fall to database
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null) return null;

        if (message.isReadBy(username)) return MessageStatus.READ;
        if (message.isDeliveredTo(username)) return MessageStatus.DELIVERED;
        if (message.getSender().equals(username)) return MessageStatus.SENT;

        return null;
    }

    public Set<Message> getUnreadMessages(String username, String roomId) {
        return messageRepository.findByRoomIdOrderByTimestampDesc(roomId).stream()
                .filter(msg -> !msg.getSender().equals(username))
                .filter(msg -> !msg.isReadBy(username))
                .limit(50)
                .collect(Collectors.toSet());
    }

    public long getUnreadCount(String username, String roomId) {
        String key = USER_LAST_READ_PREFIX + roomId + ":" + username;
        Object lastReadTimestamp = redisTemplate.opsForValue().get(key);

        if (lastReadTimestamp != null) {
            // Count messages after last read timestamp
            return messageRepository.countByRoomIdAndTimestampAfterAndSenderNot(
                    roomId,
                    LocalDateTime.parse(lastReadTimestamp.toString()),
                    username
            );
        }

        return messageRepository.findByRoomIdOrderByTimestampDesc(roomId).stream()
                .filter(msg -> !msg.getSender().equals(username))
                .filter(msg -> !msg.isReadBy(username))
                .count();
    }


    private void updateMessageStatusInRedis(Message message) {
        String key = MESSAGE_STATUS_PREFIX + message.getId();
        redisTemplate.opsForValue().set(key, message, 1, TimeUnit.HOURS);

        for (String user : message.getReadBy()) {
            String userKey = MESSAGE_STATUS_PREFIX + message.getId() + ":" + user;
            redisTemplate.opsForValue().set(userKey, MessageStatus.READ.toString(), 1, TimeUnit.HOURS);
        }

        for (String user : message.getDeliveredTo()) {
            if (!message.getReadBy().contains(user)) {
                String userKey = MESSAGE_STATUS_PREFIX + message.getId() + ":" + user;
                redisTemplate.opsForValue().set(userKey, MessageStatus.DELIVERED.toString(), 1, TimeUnit.HOURS);
            }
        }
    }

    private void updateUserLastRead(String username, String roomId, String lastMessageId) {
        String key = USER_LAST_READ_PREFIX + roomId + ":" + username;
        redisTemplate.opsForValue().set(key, LocalDateTime.now().toString(), 24, TimeUnit.HOURS);
    }
}