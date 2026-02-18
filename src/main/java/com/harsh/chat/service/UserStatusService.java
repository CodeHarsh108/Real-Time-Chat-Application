package com.harsh.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserStatusService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String ROOM_USERS_PREFIX = "room:users:";
    private static final String USER_ROOM_PREFIX = "user:room:";
    private static final String USER_TYPING_PREFIX = "typing:";
    private static final String USER_LAST_SEEN_PREFIX = "lastseen:";


    public void userJoinedRoom(String username, String roomId) {
        log.info("User {} joined room {}", username, roomId);

        String roomUsersKey = ROOM_USERS_PREFIX + roomId;
        redisTemplate.opsForSet().add(roomUsersKey, username);

        redisTemplate.expire(roomUsersKey, 1, TimeUnit.HOURS);

        String userRoomKey = USER_ROOM_PREFIX + username;
        redisTemplate.opsForValue().set(userRoomKey, roomId, 1, TimeUnit.HOURS);

        updateLastSeen(username);

        broadcastRoomUsers(roomId);

        Map<String, Object> joinMessage = new HashMap<>();
        joinMessage.put("type", "USER_JOINED");
        joinMessage.put("username", username);
        joinMessage.put("timestamp", System.currentTimeMillis());
        joinMessage.put("userCount", getOnlineUsersCount(roomId));

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/status", joinMessage);
    }


    public void userLeftRoom(String username, String roomId) {
        log.info("User {} left room {}", username, roomId);

        String roomUsersKey = ROOM_USERS_PREFIX + roomId;
        redisTemplate.opsForSet().remove(roomUsersKey, username);

        String userRoomKey = USER_ROOM_PREFIX + username;
        redisTemplate.delete(userRoomKey);

        removeTypingStatus(username, roomId);

        updateLastSeen(username);

        broadcastRoomUsers(roomId);

        Map<String, Object> leaveMessage = new HashMap<>();
        leaveMessage.put("type", "USER_LEFT");
        leaveMessage.put("username", username);
        leaveMessage.put("timestamp", System.currentTimeMillis());
        leaveMessage.put("userCount", getOnlineUsersCount(roomId));

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/status", leaveMessage);
    }


    public Set<String> getOnlineUsersInRoom(String roomId) {
        String roomUsersKey = ROOM_USERS_PREFIX + roomId;
        Set<Object> users = redisTemplate.opsForSet().members(roomUsersKey);

        if (users != null) {
            return users.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());
        }
        return Set.of();
    }


    public Long getOnlineUsersCount(String roomId) {
        String roomUsersKey = ROOM_USERS_PREFIX + roomId;
        Long count = redisTemplate.opsForSet().size(roomUsersKey);
        return count != null ? count : 0L;
    }


    public boolean isUserOnlineInRoom(String username, String roomId) {
        String roomUsersKey = ROOM_USERS_PREFIX + roomId;
        Boolean isMember = redisTemplate.opsForSet().isMember(roomUsersKey, username);
        return Boolean.TRUE.equals(isMember);
    }


    public String getUserRoom(String username) {
        String userRoomKey = USER_ROOM_PREFIX + username;
        Object roomId = redisTemplate.opsForValue().get(userRoomKey);
        return roomId != null ? roomId.toString() : null;
    }

    // TYPING INDICATORS


    public void userTyping(String username, String roomId) {
        String typingKey = USER_TYPING_PREFIX + roomId;

        redisTemplate.opsForSet().add(typingKey, username);
        redisTemplate.expire(typingKey, 5, TimeUnit.SECONDS);

        Map<String, Object> typingMessage = new HashMap<>();
        typingMessage.put("type", "TYPING_START");
        typingMessage.put("username", username);
        typingMessage.put("roomId", roomId);
        typingMessage.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/typing", typingMessage);

        log.debug("User {} is typing in room {}", username, roomId);
    }


    public void userStoppedTyping(String username, String roomId) {
        removeTypingStatus(username, roomId);

        Map<String, Object> stoppedMessage = new HashMap<>();
        stoppedMessage.put("type", "TYPING_STOP");
        stoppedMessage.put("username", username);
        stoppedMessage.put("roomId", roomId);
        stoppedMessage.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/typing", stoppedMessage);

        log.debug("User {} stopped typing in room {}", username, roomId);
    }

    private void removeTypingStatus(String username, String roomId) {
        String typingKey = USER_TYPING_PREFIX + roomId;
        redisTemplate.opsForSet().remove(typingKey, username);
    }


    public Set<String> getUsersTyping(String roomId) {
        String typingKey = USER_TYPING_PREFIX + roomId;
        Set<Object> users = redisTemplate.opsForSet().members(typingKey);

        if (users != null) {
            return users.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());
        }
        return Set.of();
    }


    public boolean isUserTyping(String username, String roomId) {
        String typingKey = USER_TYPING_PREFIX + roomId;
        Boolean isMember = redisTemplate.opsForSet().isMember(typingKey, username);
        return Boolean.TRUE.equals(isMember);
    }

    // LAST SEEN


    public void updateLastSeen(String username) {
        String lastSeenKey = USER_LAST_SEEN_PREFIX + username;
        redisTemplate.opsForValue().set(lastSeenKey, System.currentTimeMillis(), 24, TimeUnit.HOURS);
    }


    public Long getLastSeen(String username) {
        String lastSeenKey = USER_LAST_SEEN_PREFIX + username;
        Object lastSeen = redisTemplate.opsForValue().get(lastSeenKey);
        return lastSeen != null ? Long.parseLong(lastSeen.toString()) : null;
    }

    // BROADCAST UTILITIES


    public void broadcastRoomUsers(String roomId) {
        Set<String> onlineUsers = getOnlineUsersInRoom(roomId);
        Long userCount = getOnlineUsersCount(roomId);

        Map<String, Object> statusMessage = new HashMap<>();
        statusMessage.put("type", "ROOM_USERS");
        statusMessage.put("roomId", roomId);
        statusMessage.put("users", onlineUsers);
        statusMessage.put("count", userCount);
        statusMessage.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/users", statusMessage);
    }


    public void cleanupStaleUsers() {
        log.info("Cleaning up stale user data...");
    }
}