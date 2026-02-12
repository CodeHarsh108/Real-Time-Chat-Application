package com.harsh.chat.payload;
import com.harsh.chat.entity.Room;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomResponse {
    private String roomId;
    private LocalDateTime createdAt;
    private Integer totalMessages;

    public static RoomResponse from(Room room) {
        return RoomResponse.builder()
                .roomId(room.getRoomId())
                .createdAt(room.getCreatedAt())
                .totalMessages(room.getTotalMessages())
                .build();
    }
}