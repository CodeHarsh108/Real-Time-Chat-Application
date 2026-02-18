package com.harsh.chat.payload;

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
public class ReadReceiptDTO {
    private String type;
    private String messageId;
    private String roomId;
    private String username;
    private MessageStatus status;
    private LocalDateTime timestamp;
    private Set<String> readBy;     // For bulk updates
    private Set<String> deliveredTo; // For bulk updates
}