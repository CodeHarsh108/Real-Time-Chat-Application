package com.harsh.chat.payload;

import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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

    private boolean hasAttachment;
    private String attachmentType;
    private String attachmentName;
    private String attachmentUrl;
    private String thumbnailUrl;
    private Long attachmentSize;

    private MessageStatus status;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime readAt;
    private Set<String> readBy;
    private Set<String> deliveredTo;
    private int totalRecipients;

    public static MessageResponse from(Message message) {
        return MessageResponse.builder()
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
                .build();
    }

}