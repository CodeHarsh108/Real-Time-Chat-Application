package com.harsh.chat.controllers;

import com.harsh.chat.payload.ReactionDTO;
import com.harsh.chat.service.ReactionService;
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
import java.util.Map;
import java.util.Set;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ReactionController {

    private final ReactionService reactionService;

    /**
     * Add reaction to a message (WebSocket)
     */
    @MessageMapping("/reaction/add/{roomId}")
    public void addReaction(
            @DestinationVariable String roomId,
            @Payload Map<String, String> payload,
            Principal principal
    ) {
        String username = principal.getName();
        String messageId = payload.get("messageId");
        String emoji = payload.get("emoji");

        log.info("WebSocket: User {} adding reaction {} to message {} in room {}",
                username, emoji, messageId, roomId);

        reactionService.addReaction(messageId, roomId, username, emoji);
    }

    /**
     * Remove reaction from a message (WebSocket)
     */
    @MessageMapping("/reaction/remove/{roomId}")
    public void removeReaction(
            @DestinationVariable String roomId,
            @Payload Map<String, String> payload,
            Principal principal
    ) {
        String username = principal.getName();
        String messageId = payload.get("messageId");
        String emoji = payload.get("emoji");

        log.info("WebSocket: User {} removing reaction {} from message {} in room {}",
                username, emoji, messageId, roomId);

        reactionService.removeReaction(messageId, roomId, username, emoji);
    }

    /**
     * Get reactions for a message (REST)
     */
    @GetMapping("/api/v1/messages/{messageId}/reactions")
    public ResponseEntity<?> getMessageReactions(@PathVariable String messageId) {
        Map<String, Set<String>> reactions = reactionService.getMessageReactions(messageId);
        Map<String, Integer> counts = reactionService.getReactionCounts(messageId);

        return ResponseEntity.ok(Map.of(
                "reactions", reactions,
                "counts", counts,
                "total", counts.values().stream().mapToInt(Integer::intValue).sum()
        ));
    }

    /**
     * Get user's reaction to a message
     */
    @GetMapping("/api/v1/messages/{messageId}/reactions/user")
    public ResponseEntity<?> getUserReaction(
            @PathVariable String messageId,
            Authentication authentication
    ) {
        String username = authentication.getName();
        String reaction = reactionService.getUserReaction(messageId, username);

        return ResponseEntity.ok(Map.of(
                "messageId", messageId,
                "username", username,
                "reaction", reaction
        ));
    }
}