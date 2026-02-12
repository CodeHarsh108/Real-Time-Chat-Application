package com.harsh.chat.entity;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {
    @Id
    private String id;//Mongo db : unique identifier

    @Indexed(unique = true)
    private String roomId;

    @Builder.Default
    private List<String> recentMessageIds = new ArrayList<>();

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Builder.Default
    private Integer totalMessages = 0;

    public void addMessage(String messageId){
        this.recentMessageIds.add(0, messageId);
        if(this.recentMessageIds.size() > 50){
            this.recentMessageIds = this.recentMessageIds.subList(0, 50);
        }
        this.totalMessages++;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isNew(){
        return this.createdAt == null;
    }

}