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
    private String sender;
    private String content;
    private LocalDateTime timestamp;

    public static MessageResponse from(Message message) {
        return MessageResponse.builder()
                .sender(message.getSender())
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .build();
    }
}