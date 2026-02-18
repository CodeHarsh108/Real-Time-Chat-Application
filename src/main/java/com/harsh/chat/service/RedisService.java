package com.harsh.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.Room;
import com.harsh.chat.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String MESSAGE_KEY_PREFIX = "message:";
    private static final String RECENT_MESSAGES_KEY_PREFIX = "recent:messages:";
    private static final String ROOM_KEY_PREFIX = "room:";
    private static final String USER_KEY_PREFIX = "user:";
    private static final String ONLINE_USERS_KEY = "online:users";
    private static final String ROOM_USERS_PREFIX = "room:users:";
    private static final String USER_ROOM_PREFIX = "user:room:";
    private static final String USER_TYPING_PREFIX = "typing:";
    private static final String USER_LAST_SEEN_PREFIX = "lastseen:";

    private <T> T safeCast(Object obj, Class<T> clazz) {
        if (obj == null) return null;
        if (clazz.isInstance(obj)) {
            return clazz.cast(obj);
        }
        // Try to convert using Jackson if it's a LinkedHashMap
        try {
            return objectMapper.convertValue(obj, clazz);
        } catch (Exception e) {
            log.error("Failed to convert object to {}: {}", clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }

    public void cacheMessage(String roomId, Message message) {
        try {
            String key = MESSAGE_KEY_PREFIX + message.getId();
            redisTemplate.opsForValue().set(key, message, 1, TimeUnit.HOURS);

            // Add to recent messages list
            String recentKey = RECENT_MESSAGES_KEY_PREFIX + roomId;
            redisTemplate.opsForList().leftPush(recentKey, message);
            // Keep only last 50 messages
            redisTemplate.opsForList().trim(recentKey, 0, 49);
            // Set expiry on recent messages list
            redisTemplate.expire(recentKey, 5, TimeUnit.MINUTES);

            log.debug("Cached message: {} for room: {}", message.getId(), roomId);
        } catch (Exception e) {
            log.error("Failed to cache message: {}", e.getMessage());
        }
    }



    public List<Message> getRecentMessages(String roomId) {
        try {
            String recentKey = RECENT_MESSAGES_KEY_PREFIX + roomId;
            List<Object> messages = redisTemplate.opsForList().range(recentKey, 0, 49);

            if (messages != null && !messages.isEmpty()) {
                List<Message> result = new ArrayList<>();
                for (Object obj : messages) {
                    Message msg = safeCast(obj, Message.class);
                    if (msg != null) {
                        result.add(msg);
                    }
                }
                log.debug("Retrieved {} messages from cache for room: {}", result.size(), roomId);
                return result;
            }
        } catch (Exception e) {
            log.error("Failed to get recent messages from cache: {}", e.getMessage());
        }
        return new ArrayList<>();
    }


    public Message getCachedMessage(String messageId) {
        try {
            String key = MESSAGE_KEY_PREFIX + messageId;
            Object message = redisTemplate.opsForValue().get(key);
            if (message instanceof Message) {
                return (Message) message;
            }
        } catch (Exception e) {
            log.error("Failed to get cached message: {}", e.getMessage());
        }
        return null;
    }

    public Room getCachedRoom(String roomId) {
        try {
            String key = ROOM_KEY_PREFIX + roomId;
            Object obj = redisTemplate.opsForValue().get(key);
            Room room = safeCast(obj, Room.class);
            if (room != null) {
                log.debug("Retrieved room from cache: {}", roomId);
                return room;
            }
        } catch (Exception e) {
            log.error("Failed to get cached room: {}", e.getMessage());
        }
        return null;
    }



    public boolean isRoomCached(String roomId) {
        try {
            String key = ROOM_KEY_PREFIX + roomId;
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("Failed to check cached room: {}", e.getMessage());
            return false;
        }
    }

    public void evictRoom(String roomId) {
        try {
            String key = ROOM_KEY_PREFIX + roomId;
            redisTemplate.delete(key);
            log.debug("Evicted room from cache: {}", roomId);
        } catch (Exception e) {
            log.error("Failed to evict room: {}", e.getMessage());
        }
    }


    public void cacheUserSession(String username, User user) {
        try {
            String key = USER_KEY_PREFIX + username;
            redisTemplate.opsForValue().set(key, user, 1, TimeUnit.HOURS);
            log.debug("Cached user session: {}", username);
        } catch (Exception e) {
            log.error("Failed to cache user session: {}", e.getMessage());
        }
    }


    public User getCachedUserSession(String username) {
        try {
            String key = USER_KEY_PREFIX + username;
            Object user = redisTemplate.opsForValue().get(key);
            if (user instanceof User) {
                return (User) user;
            }
        } catch (Exception e) {
            log.error("Failed to get cached user session: {}", e.getMessage());
        }
        return null;
    }

    public void userOnline(String username) {
        try {
            redisTemplate.opsForSet().add(ONLINE_USERS_KEY, username);
            redisTemplate.expire(ONLINE_USERS_KEY, 1, TimeUnit.HOURS);
            log.debug("User online: {}", username);
        } catch (Exception e) {
            log.error("Failed to mark user online: {}", e.getMessage());
        }
    }

    public void userOffline(String username) {
        try {
            redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, username);
            log.debug("User offline: {}", username);
        } catch (Exception e) {
            log.error("Failed to mark user offline: {}", e.getMessage());
        }
    }

    public void cacheRoom(Room room) {
        try {
            String key = ROOM_KEY_PREFIX + room.getRoomId();
            redisTemplate.opsForValue().set(key, room, 10, TimeUnit.MINUTES);
            log.debug("Cached room: {}", room.getRoomId());
        } catch (Exception e) {
            log.error("Failed to cache room: {}", e.getMessage());
        }
    }

    public Set<Object> getOnlineUsers() {
        try {
            Set<Object> users = redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
            log.debug("Retrieved {} online users", users != null ? users.size() : 0);
            return users;
        } catch (Exception e) {
            log.error("Failed to get online users: {}", e.getMessage());
            return Set.of();
        }
    }

    public boolean isUserOnline(String username) {
        try {
            Boolean isOnline = redisTemplate.opsForSet().isMember(ONLINE_USERS_KEY, username);
            return Boolean.TRUE.equals(isOnline);
        } catch (Exception e) {
            log.error("Failed to check user online status: {}", e.getMessage());
            return false;
        }
    }

    public boolean checkRateLimit(String key, int maxAttempts, int timeWindowSeconds) {
        try {
            String rateKey = "rate:" + key;
            Long currentCount = redisTemplate.opsForValue().increment(rateKey);

            if (currentCount == 1) {
                // First attempt, set expiry
                redisTemplate.expire(rateKey, timeWindowSeconds, TimeUnit.SECONDS);
            }

            boolean allowed = currentCount <= maxAttempts;
            if (!allowed) {
                log.warn("Rate limit exceeded for: {}", key);
            }
            return allowed;
        } catch (Exception e) {
            log.error("Rate limit check failed: {}", e.getMessage());
            return true; // Fail open (allow action if Redis is down)
        }
    }

    public void clearRoomCache(String roomId) {
        try {
            String recentKey = RECENT_MESSAGES_KEY_PREFIX + roomId;
            String roomKey = ROOM_KEY_PREFIX + roomId;
            String typingKey = USER_TYPING_PREFIX + roomId;
            String roomUsersKey = ROOM_USERS_PREFIX + roomId;

            redisTemplate.delete(recentKey);
            redisTemplate.delete(roomKey);
            redisTemplate.delete(typingKey);
            redisTemplate.delete(roomUsersKey);

            log.debug("Cleared cache for room: {}", roomId);
        } catch (Exception e) {
            log.error("Failed to clear room cache: {}", e.getMessage());
        }
    }


    public Map<String, Long> getCacheStats() {
        Map<String, Long> stats = new HashMap<>();
        try {
            stats.put("onlineUsers", (long) getOnlineUsers().size());
            // Add more stats as needed
        } catch (Exception e) {
            log.error("Failed to get cache stats: {}", e.getMessage());
        }
        return stats;
    }



}
