package com.harsh.chat.controllers;

import com.harsh.chat.payload.ReplyDTO;
import com.harsh.chat.payload.MessageRequest;
import com.harsh.chat.payload.MessageResponse;
import com.harsh.chat.service.ThreadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ThreadController {

    private final ThreadService threadService;

    /**
     * Reply to a message (WebSocket)
     */
    @MessageMapping("/reply/{roomId}")
    public void replyToMessage(
            @DestinationVariable String roomId,
            @Payload Map<String, String> payload,
            Principal principal
    ) {
        String username = principal.getName();
        String parentMessageId = payload.get("parentMessageId");
        String content = payload.get("content");

        log.info("WebSocket: User {} replying to message {} in room {}",
                username, parentMessageId, roomId);

        threadService.replyToMessage(parentMessageId, roomId, username, content);
    }

    /**
     * Get thread replies (REST)
     */
    @GetMapping("/api/v1/messages/{parentMessageId}/replies")
    public ResponseEntity<List<MessageResponse>> getThreadReplies(
            @PathVariable String parentMessageId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<MessageResponse> replies = threadService.getThreadReplies(parentMessageId, page, size);
        return ResponseEntity.ok(replies);
    }

    /**
     * Get thread info
     */
    @GetMapping("/api/v1/messages/{messageId}/thread")
    public ResponseEntity<?> getThreadInfo(@PathVariable String messageId) {
        ReplyDTO threadInfo = threadService.getThreadInfo(messageId);
        return ResponseEntity.ok(threadInfo);
    }

    /**
     * Delete a reply
     */
    @DeleteMapping("/api/v1/messages/{replyId}")
    public ResponseEntity<?> deleteReply(
            @PathVariable String replyId,
            Authentication authentication
    ) {
        String username = authentication.getName();
        threadService.deleteReply(replyId, username);
        return ResponseEntity.ok(Map.of("message", "Reply deleted successfully"));
    }
}