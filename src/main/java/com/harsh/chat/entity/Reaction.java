package com.harsh.chat.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "reactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reaction {

    @Id
    private String id;

    @Indexed
    private String messageId;

    @Indexed
    private String roomId;

    private String username;
    private String emoji;
    private String emojiCode;

    @Builder.Default
    private LocalDateTime reactedAt = LocalDateTime.now();

    // For unique constraint: one user can only react once per message with same emoji
    // But they can change emoji
}