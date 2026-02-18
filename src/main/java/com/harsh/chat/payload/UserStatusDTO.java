package com.harsh.chat.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusDTO {
    private String type;         // "USER_JOINED", "USER_LEFT", "ROOM_USERS"
    private String username;
    private String roomId;
    private Set<String> users;    // For ROOM_USERS type
    private Long count;           // User count
    private long timestamp;
}