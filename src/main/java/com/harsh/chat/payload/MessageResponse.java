package com.harsh.chat.payload;

import com.harsh.chat.entity.Message;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
                .build();
    }
}