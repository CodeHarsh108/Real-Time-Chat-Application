package com.harsh.chat.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class CreateRoomRequest {

    @NotBlank(message = "Room ID cannot be blank")
    @Size(min = 3, max = 50, message = "Room ID must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Room ID can only contain letters, numbers, hyphens, and underscores")
    private String roomId;
}
