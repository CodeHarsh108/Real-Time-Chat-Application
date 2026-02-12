package com.harsh.chat.payload;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageRequest {

    @NotBlank(message = "Content cannot be blank")
    @Size(max = 5000, message = "Content cannot exceed 5000 characters")
    private String content;

    @NotBlank(message = "Sender cannot be blank")
    @Size(min = 1, max = 100, message = "Sender name must be between 1 and 100 characters")
    private String sender;

    @NotBlank(message = "Room ID cannot be blank")
    private String roomId;


}