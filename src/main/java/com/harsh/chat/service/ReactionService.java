package com.harsh.chat.service;

import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.Reaction;
import com.harsh.chat.payload.ReactionDTO;
import com.harsh.chat.repositories.MessageRepository;
import com.harsh.chat.repositories.ReactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReactionService {

    private final MessageRepository messageRepository;
    private final ReactionRepository reactionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REACTION_CACHE_PREFIX = "reaction:";
    private static final String MESSAGE_REACTIONS_PREFIX = "msg:reactions:";

    /**
     * Add reaction to a message
     */
    @Transactional
    public ReactionDTO addReaction(String messageId, String roomId, String username, String emoji) {
        log.info("User {} adding reaction {} to message {} in room {}", username, emoji, messageId, roomId);

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found: " + messageId));

        String oldEmoji = message.getUserReaction(username);

        if (oldEmoji != null && !oldEmoji.equals(emoji)) {
            removeReaction(messageId, roomId, username, oldEmoji, false);
        }

        boolean added = message.addReaction(username, emoji);

        if (added) {
            messageRepository.save(message);

            Reaction reaction = Reaction.builder()
                    .messageId(messageId)
                    .roomId(roomId)
                    .username(username)
                    .emoji(emoji)
                    .reactedAt(LocalDateTime.now())
                    .build();
            reactionRepository.save(reaction);

            updateReactionCache(message);

            ReactionDTO reactionDTO = ReactionDTO.builder()
                    .type(oldEmoji != null ? "UPDATE" : "ADD")
                    .messageId(messageId)
                    .roomId(roomId)
                    .username(username)
                    .emoji(emoji)
                    .timestamp(LocalDateTime.now())
                    .reactionCounts(message.getReactionCounts())
                    .reactions(message.getReactions())
                    .totalReactions(message.getReactionCounts().values().stream().mapToInt(Integer::intValue).sum())
                    .build();

            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/reactions", reactionDTO);

            log.info("Reaction added: {} to message {} by {}", emoji, messageId, username);

            return reactionDTO;
        }

        return null;
    }

    /**
     * Remove reaction from a message
     */
    @Transactional
    public ReactionDTO removeReaction(String messageId, String roomId, String username, String emoji) {
        return removeReaction(messageId, roomId, username, emoji, true);
    }

    private ReactionDTO removeReaction(String messageId, String roomId, String username, String emoji, boolean broadcast) {
        log.info("User {} removing reaction {} from message {} in room {}", username, emoji, messageId, roomId);

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found: " + messageId));

        boolean removed = message.removeReaction(username, emoji);

        if (removed) {
            messageRepository.save(message);

            reactionRepository.deleteByMessageIdAndUsername(messageId, username);

            updateReactionCache(message);

            ReactionDTO reactionDTO = ReactionDTO.builder()
                    .type("REMOVE")
                    .messageId(messageId)
                    .roomId(roomId)
                    .username(username)
                    .emoji(emoji)
                    .timestamp(LocalDateTime.now())
                    .reactionCounts(message.getReactionCounts())
                    .reactions(message.getReactions())
                    .totalReactions(message.getReactionCounts().values().stream().mapToInt(Integer::intValue).sum())
                    .build();

            if (broadcast) {
                messagingTemplate.convertAndSend("/topic/room/" + roomId + "/reactions", reactionDTO);
            }

            log.info("Reaction removed: {} from message {} by {}", emoji, messageId, username);

            return reactionDTO;
        }

        return null;
    }

    /**
     * Get all reactions for a message
     */
    public Map<String, Set<String>> getMessageReactions(String messageId) {
        // Cache first
        String cacheKey = MESSAGE_REACTIONS_PREFIX + messageId;
        Map<String, Set<String>> cached = (Map<String, Set<String>>) redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            return cached;
        }

        Message message = messageRepository.findById(messageId).orElse(null);
        if (message != null) {
            updateReactionCache(message);
            return message.getReactions();
        }

        return Map.of();
    }

    /**
     * Get reaction counts for a message
     */
    public Map<String, Integer> getReactionCounts(String messageId) {
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message != null) {
            return message.getReactionCounts();
        }
        return Map.of();
    }

    /**
     * Get user's reaction to a message
     */
    public String getUserReaction(String messageId, String username) {
        // Cache first
        String cacheKey = REACTION_CACHE_PREFIX + messageId + ":" + username;
        String cached = (String) redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            return cached;
        }

        Reaction reaction = reactionRepository.findByMessageIdAndUsername(messageId, username).orElse(null);
        if (reaction != null) {
            redisTemplate.opsForValue().set(cacheKey, reaction.getEmoji(), 1, TimeUnit.HOURS);
            return reaction.getEmoji();
        }

        return null;
    }

    /**
     * Get all users who reacted with specific emoji
     */
    public Set<String> getUsersByReaction(String messageId, String emoji) {
        return reactionRepository.findByMessageIdAndEmoji(messageId, emoji).stream()
                .map(Reaction::getUsername)
                .collect(Collectors.toSet());
    }

    /**
     * Get reaction summary for multiple messages
     */
    public Map<String, Map<String, Integer>> getBulkReactionCounts(Set<String> messageIds) {
        return messageIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> getReactionCounts(id)
                ));
    }

    // ============== PRIVATE METHODS ==============

    private void updateReactionCache(Message message) {
        String cacheKey = MESSAGE_REACTIONS_PREFIX + message.getId();
        redisTemplate.opsForValue().set(cacheKey, message.getReactions(), 1, TimeUnit.HOURS);

        // Update per-user reaction cache
        for (Map.Entry<String, Set<String>> entry : message.getReactions().entrySet()) {
            String emoji = entry.getKey();
            for (String user : entry.getValue()) {
                String userKey = REACTION_CACHE_PREFIX + message.getId() + ":" + user;
                redisTemplate.opsForValue().set(userKey, emoji, 1, TimeUnit.HOURS);
            }
        }
    }
}