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
public class ReplyDTO {
    private String type;           // "REPLY", "THREAD_UPDATE"
    private String parentMessageId;
    private String replyMessageId;
    private String roomId;
    private String sender;
    private String content;
    private LocalDateTime timestamp;

    // Preview of parent message
    private String parentSender;
    private String parentContent;
    private boolean parentHasAttachment;
    private String parentAttachmentType;

    // Thread info
    private int replyCount;
    private boolean hasReplies;
}