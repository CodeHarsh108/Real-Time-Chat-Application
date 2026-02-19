package com.harsh.chat.payload;

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
public class ReactionDTO {
    private String type;           // "ADD", "REMOVE", "UPDATE"
    private String messageId;
    private String roomId;
    private String username;
    private String emoji;
    private LocalDateTime timestamp;

    // For summary updates
    private Map<String, Integer> reactionCounts;
    private Map<String, Set<String>> reactions;
    private int totalReactions;
}