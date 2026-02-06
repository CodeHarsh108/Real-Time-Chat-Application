package com.harsh.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomDTO {
    private String roomId;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivity;
    private int messageCount;
    private List<MessageDTO> recentMessages;
}
