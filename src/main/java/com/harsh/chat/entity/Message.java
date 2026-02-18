package com.harsh.chat.entity;

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
import java.util.Map;

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
    private java.util.Set<String> readBy = new java.util.HashSet<>();
    @Builder.Default
    private java.util.Set<String> deliveredTo = new java.util.HashSet<>();

    public static Message create(String roomId, String sender, String content) {
        return Message.builder()
                .roomId(roomId)
                .sender(sender)
                .content(content)
                .timestamp(LocalDateTime.now())
                .sentAt(LocalDateTime.now())
                .status(MessageStatus.SENT)
                .hasAttachment(false)
                .readBy(new java.util.HashSet<>())
                .deliveredTo(new java.util.HashSet<>())
                .userStatus(new HashMap<>())
                .build();
    }

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
}