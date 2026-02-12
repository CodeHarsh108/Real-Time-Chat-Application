package com.harsh.chat.entity;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Document(collection = "messages")
public class Message {

    @Id
    private String id;

    @Indexed
    private String roomId;

    private String sender;
    private String content;


    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public static Message create(String roomId, String sender, String content){
        return Message.builder()
                .roomId(roomId)
                .sender(sender)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
    }
}