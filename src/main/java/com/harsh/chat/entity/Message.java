package com.harsh.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Document(collection = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    private String id;

    @Indexed
    private String roomId;

    private String sender;
    private String content;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    private String attachmentId;
    private boolean hasAttachment;
    private String attachmentType;
    private String attachmentName;
    private String attachmentUrl;
    private String thumbnailUrl;
    private Long attachmentSize;

    @Builder.Default
    private MessageStatus status = MessageStatus.SENT;

    @Builder.Default
    private Map<String, MessageStatus> userStatus = new HashMap<>();

    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime readAt;

    @Builder.Default
    private Set<String> readBy = new HashSet<>();
    @Builder.Default
    private Set<String> deliveredTo = new HashSet<>();


    private String parentMessageId;  // If this is a reply, ID of parent message
    private boolean hasReplies;       // Does this message have replies?
    private int replyCount;           // Number of replies

    @Builder.Default
    private Set<String> replyIds = new HashSet<>(); // IDs of all replies

    @Builder.Default
    private Map<String, Set<String>> reactions = new HashMap<>(); // emoji -> Set of usernames

    @Builder.Default
    private Map<String, Integer> reactionCounts = new HashMap<>(); // emoji -> count


    // Encryption
//    private boolean isEncrypted;
//    private String encryptedContent; // Store encrypted content
//    private String encryptionIv;      // Initialization vector

    public static Message create(String roomId, String sender, String content) {
        return Message.builder()
                .roomId(roomId)
                .sender(sender)
                .content(content)
                .timestamp(LocalDateTime.now())
                .sentAt(LocalDateTime.now())
                .status(MessageStatus.SENT)
                .hasAttachment(false)
                .readBy(new HashSet<>())
                .deliveredTo(new HashSet<>())
                .userStatus(new HashMap<>())
                .reactions(new HashMap<>())
                .reactionCounts(new HashMap<>())
                .replyIds(new HashSet<>())
                .hasReplies(false)
                .replyCount(0)
                .build();
    }


    /**
     * Add a reaction to this message
     */
    public boolean addReaction(String username, String emoji) {
        if (!reactions.containsKey(emoji)) {
            reactions.put(emoji, new HashSet<>());
        }

        Set<String> users = reactions.get(emoji);
        if (users.add(username)) {
            reactionCounts.put(emoji, users.size());
            return true;
        }
        return false;
    }



    /**
     * Remove a reaction from this message
     */
    public boolean removeReaction(String username, String emoji) {
        if (reactions.containsKey(emoji)) {
            Set<String> users = reactions.get(emoji);
            if (users.remove(username)) {
                if (users.isEmpty()) {
                    reactions.remove(emoji);
                    reactionCounts.remove(emoji);
                } else {
                    reactionCounts.put(emoji, users.size());
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Get user's reaction to this message
     */
    public String getUserReaction(String username) {
        for (Map.Entry<String, Set<String>> entry : reactions.entrySet()) {
            if (entry.getValue().contains(username)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Check if user reacted with specific emoji
     */
    public boolean hasUserReactedWith(String username, String emoji) {
        return reactions.containsKey(emoji) && reactions.get(emoji).contains(username);
    }

    /**
     * Get all reactions summary
     */
    @JsonIgnore
    public Map<String, Object> getReactionsSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("counts", reactionCounts);
        summary.put("total", reactionCounts.values().stream().mapToInt(Integer::intValue).sum());
        return summary;
    }

    // ============== THREAD METHODS ==============

    /**
     * Add a reply to this message
     */
    public void addReply(String replyId) {
        this.replyIds.add(replyId);
        this.replyCount = this.replyIds.size();
        this.hasReplies = true;
    }

    /**
     * Remove a reply
     */
    public void removeReply(String replyId) {
        this.replyIds.remove(replyId);
        this.replyCount = this.replyIds.size();
        this.hasReplies = !this.replyIds.isEmpty();
    }

    /**
     * Check if this is a reply
     */
    public boolean isReply() {
        return parentMessageId != null && !parentMessageId.isEmpty();
    }

    /**
     * Get thread info
     */
    @JsonIgnore
    public Map<String, Object> getThreadInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("hasReplies", hasReplies);
        info.put("replyCount", replyCount);
        info.put("replyIds", replyIds);
        info.put("isReply", isReply());
        info.put("parentMessageId", parentMessageId);
        return info;
    }

    // ============== READ RECEIPTS METHODS ==============

    public void markAsDelivered(String username) {
        this.deliveredTo.add(username);
        this.userStatus.put(username, MessageStatus.DELIVERED);
        if (this.deliveredAt == null && !this.sender.equals(username)) {
            this.deliveredAt = LocalDateTime.now();
            this.status = MessageStatus.DELIVERED;
        }
    }

    public void markAsRead(String username) {
        this.readBy.add(username);
        this.userStatus.put(username, MessageStatus.READ);
        if (!this.sender.equals(username)) {
            this.status = MessageStatus.READ;
            this.readAt = LocalDateTime.now();
        }
    }

    public boolean isReadBy(String username) {
        return this.readBy.contains(username);
    }

    public boolean isDeliveredTo(String username) {
        return this.deliveredTo.contains(username);
    }

//    public static Message createEncrypted(String roomId, String sender, String encryptedContent, String iv) {
//        return Message.builder()
//                .roomId(roomId)
//                .sender(sender)
//                .isEncrypted(true)
//                .encryptedContent(encryptedContent)
//                .encryptionIv(iv)
//                .timestamp(LocalDateTime.now())
//                .sentAt(LocalDateTime.now())
//                .status(MessageStatus.SENT)
//                .hasAttachment(false)
//                .readBy(new HashSet<>())
//                .deliveredTo(new HashSet<>())
//                .userStatus(new HashMap<>())
//                .reactions(new HashMap<>())
//                .reactionCounts(new HashMap<>())
//                .replyIds(new HashSet<>())
//                .hasReplies(false)
//                .replyCount(0)
//                .build();
//    }


}