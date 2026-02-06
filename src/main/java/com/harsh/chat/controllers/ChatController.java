package com.harsh.chat.controllers;
import com.harsh.chat.entity.Message;
import com.harsh.chat.payload.MessageRequest;
import com.harsh.chat.services.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/sendMessage/{roomId}")
    @SendTo("/topic/room/{roomId}")
    public Message sendMessage(
            @DestinationVariable String roomId,
            @Valid MessageRequest request
    ) {
        return chatService.sendMessage(request);
    }

    @MessageMapping("/userTyping/{roomId}")
    @SendTo("/topic/typing/{roomId}")
    public TypingNotification handleTyping(
            @DestinationVariable String roomId,
            TypingRequest request
    ) {
        return new TypingNotification(request.getUsername(), request.isTyping());
    }
}

// Add Typing classes
class TypingRequest {
    private String username;
    private boolean typing;

    public TypingRequest(String username, boolean typing) {
        this.username = username;
        this.typing = typing;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isTyping() {
        return typing;
    }

    public void setTyping(boolean typing) {
        this.typing = typing;
    }
}

class TypingNotification {
    private String username;
    private boolean typing;

    public TypingNotification(String username, boolean typing) {
        this.username = username;
        this.typing = typing;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isTyping() {
        return typing;
    }

    public void setTyping(boolean typing) {
        this.typing = typing;
    }
}

