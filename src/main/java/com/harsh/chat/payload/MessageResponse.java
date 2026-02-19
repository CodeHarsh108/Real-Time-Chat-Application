package com.harsh.chat.payload;

import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    private String id;
    private String sender;
    private String content;
    private LocalDateTime timestamp;

    // Attachment fields
    private boolean hasAttachment;
    private String attachmentType;
    private String attachmentName;
    private String attachmentUrl;
    private String thumbnailUrl;
    private Long attachmentSize;

    // Read Receipts Fields
    private MessageStatus status;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime readAt;
    private Set<String> readBy;
    private Set<String> deliveredTo;
    private int totalRecipients;

    // Reactions Fields
    private Map<String, Set<String>> reactions;
    private Map<String, Integer> reactionCounts;
    private int totalReactions;
    private String userReaction;

    //  Thread Fields
    private String parentMessageId;
    private boolean hasReplies;
    private int replyCount;
    private Set<String> replyIds;
    private boolean isReply;

    // Preview for reply
    private MessagePreview parentPreview;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessagePreview {
        private String id;
        private String sender;
        private String content;
        private boolean hasAttachment;
        private String attachmentType;
        private String attachmentName;
    }

    public static MessageResponse from(Message message) {
        return from(message, null);
    }

    public static MessageResponse from(Message message, String currentUser) {
        MessageResponseBuilder builder = MessageResponse.builder()
                .id(message.getId())
                .sender(message.getSender())
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .hasAttachment(message.isHasAttachment())
                .attachmentType(message.getAttachmentType())
                .attachmentName(message.getAttachmentName())
                .attachmentUrl(message.getAttachmentUrl())
                .thumbnailUrl(message.getThumbnailUrl())
                .attachmentSize(message.getAttachmentSize())
                .status(message.getStatus())
                .sentAt(message.getSentAt())
                .deliveredAt(message.getDeliveredAt())
                .readAt(message.getReadAt())
                .readBy(message.getReadBy())
                .deliveredTo(message.getDeliveredTo())
                .totalRecipients(message.getReadBy().size() + 1)
                .reactions(message.getReactions())
                .reactionCounts(message.getReactionCounts())
                .totalReactions(message.getReactionCounts().values().stream().mapToInt(Integer::intValue).sum())
                .parentMessageId(message.getParentMessageId())
                .hasReplies(message.isHasReplies())
                .replyCount(message.getReplyCount())
                .replyIds(message.getReplyIds())
                .isReply(message.isReply());

        if (currentUser != null) {
            builder.userReaction(message.getUserReaction(currentUser));
        }

        return builder.build();
    }
}