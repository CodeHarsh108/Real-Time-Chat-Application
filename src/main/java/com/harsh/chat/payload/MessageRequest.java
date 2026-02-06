package com.harsh.chat.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class MessageRequest {

    @NotBlank(message = "Content cannot be blank")
    @Size(max = 1000, message = "Message too long")
    private String content;

    @NotBlank(message = "Sender cannot be blank")
    @Size(min = 1, max = 50, message = "Sender name must be between 1-50 characters")
    private String sender;

    @NotBlank(message = "Room ID cannot be blank")
    private String roomId;
}