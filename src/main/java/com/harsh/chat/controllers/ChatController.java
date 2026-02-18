package com.harsh.chat.controllers;

import com.harsh.chat.entity.Attachment;
import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.User;
import com.harsh.chat.payload.MessageRequest;
import com.harsh.chat.payload.TypingIndicatorDTO;
import com.harsh.chat.payload.MessageResponse;
import com.harsh.chat.repositories.AttachmentRepository;
import com.harsh.chat.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final AuthService authService;
    private final UserStatusService userStatusService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AttachmentService attachmentService;
    private final AttachmentRepository attachmentRepository;
    private final ReadReceiptService readReceiptService;

    @MessageMapping("/sendMessage/{roomId}")
    @SendTo("/topic/room/{roomId}")
    public MessageResponse sendMessage(
            @DestinationVariable String roomId,
            @Payload MessageRequest request,
            Principal principal
    ) {
        String username = principal.getName();
        log.info("User {} sending message to room {}", username, roomId);

        request.setSender(username);

        if (!roomId.equals(request.getRoomId())) {
            throw new IllegalArgumentException("Room ID mismatch");
        }

        Message savedMessage = chatService.saveMessage(request);

        userStatusService.userStoppedTyping(username, roomId);

        return MessageResponse.from(savedMessage);
    }


    @MessageMapping("/typing/start/{roomId}")
    public void startTyping(
            @DestinationVariable String roomId,
            Principal principal
    ) {
        String username = principal.getName();
        log.debug("User {} started typing in room {}", username, roomId);

        userStatusService.userTyping(username, roomId);
    }


    @MessageMapping("/typing/stop/{roomId}")
    public void stopTyping(
            @DestinationVariable String roomId,
            Principal principal
    ) {
        String username = principal.getName();
        log.debug("User {} stopped typing in room {}", username, roomId);

        userStatusService.userStoppedTyping(username, roomId);
    }


    @MessageMapping("/join/{roomId}")
    @SendTo("/topic/room/{roomId}/status")
    public Map<String, Object> joinRoom(
            @DestinationVariable String roomId,
            Principal principal
    ) {
        String username = principal.getName();
        log.info("User {} joining room {}", username, roomId);

        userStatusService.userJoinedRoom(username, roomId);

        return Map.of(
                "type", "USER_JOINED",
                "username", username,
                "roomId", roomId,
                "timestamp", System.currentTimeMillis()
        );
    }


    @MessageMapping("/leave/{roomId}")
    @SendTo("/topic/room/{roomId}/status")
    public Map<String, Object> leaveRoom(
            @DestinationVariable String roomId,
            Principal principal
    ) {
        String username = principal.getName();
        log.info("User {} leaving room {}", username, roomId);

        userStatusService.userLeftRoom(username, roomId);

        return Map.of(
                "type", "USER_LEFT",
                "username", username,
                "roomId", roomId,
                "timestamp", System.currentTimeMillis()
        );
    }


    @MessageMapping("/users/{roomId}")
    @SendTo("/topic/room/{roomId}/users")
    public Map<String, Object> getOnlineUsers(
            @DestinationVariable String roomId
    ) {
        return Map.of(
                "type", "ROOM_USERS",
                "roomId", roomId,
                "users", userStatusService.getOnlineUsersInRoom(roomId),
                "count", userStatusService.getOnlineUsersCount(roomId),
                "timestamp", System.currentTimeMillis()
        );
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public String handleWebSocketException(Exception exception, Principal principal) {
        log.error("WebSocket error for user {}: {}",
                principal != null ? principal.getName() : "anonymous",
                exception.getMessage());
        return "Error: " + exception.getMessage();
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendAttachmentMessage(
            @RequestBody Map<String, Object> payload,
            Authentication authentication
    ) {
        String username = authentication.getName();
        String attachmentId = (String) payload.get("attachmentId");
        String roomId = (String) payload.get("roomId");
        String content = (String) payload.get("content");

        log.info("Sending attachment message: attachmentId={}, roomId={}, user={}",
                attachmentId, roomId, username);

        try {
            // Get attachment
            Attachment attachment = attachmentService.getAttachment(attachmentId);

            // Determine attachment type
            String attachmentType = "document";
            if (attachment.getFileType().startsWith("image/")) {
                attachmentType = "image";
            } else if (attachment.getFileType().startsWith("video/")) {
                attachmentType = "video";
            } else if (attachment.getFileType().startsWith("audio/")) {
                attachmentType = "audio";
            }

            // Create message with ALL attachment fields
            Message message = Message.builder()
                    .roomId(roomId)
                    .sender(username)
                    .content(content != null ? content : "")
                    .timestamp(LocalDateTime.now())
                    .hasAttachment(true)
                    .attachmentId(attachmentId)
                    .attachmentType(attachmentType)
                    .attachmentName(attachment.getFileName())
                    .attachmentUrl(attachment.getFileUrl())
                    .thumbnailUrl(attachment.getThumbnailUrl())
                    .attachmentSize(attachment.getFileSize())
                    .build();

            log.info("CREATED MESSAGE WITH ATTACHMENT: type={}, url={}, name={}",
                    attachmentType, attachment.getFileUrl(), attachment.getFileName());

            // Save message
            Message savedMessage = chatService.saveAttachmentMessage(message);

            // Link attachment to message
            attachment.setMessageId(savedMessage.getId());
            attachmentRepository.save(attachment);

            // Create response with ALL fields
            MessageResponse response = MessageResponse.from(savedMessage);

            // Log what we're broadcasting
            log.info("BROADCASTING: hasAttachment={}, type={}, url={}",
                    response.isHasAttachment(), response.getAttachmentType(), response.getAttachmentUrl());

            // Broadcast via WebSocket
            messagingTemplate.convertAndSend("/topic/room/" + roomId, response);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to send attachment message: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }


    @MessageMapping("/delivered/{roomId}")
    public void markAsDelivered(
            @DestinationVariable String roomId,
            @Payload Map<String, String> payload,
            Principal principal
    ) {
        String username = principal.getName();
        String messageId = payload.get("messageId");

        log.debug("User {} marking message {} as delivered in room {}", username, messageId, roomId);

        readReceiptService.markAsDelivered(messageId, username, roomId);
    }

    @MessageMapping("/read/{roomId}")
    public void markAsRead(
            @DestinationVariable String roomId,
            @Payload Map<String, String> payload,
            Principal principal
    ) {
        String username = principal.getName();
        String messageId = payload.get("messageId");

        log.debug("User {} marking message {} as read in room {}", username, messageId, roomId);

        readReceiptService.markAsRead(messageId, username, roomId);
    }

    @MessageMapping("/read/bulk/{roomId}")
    public void markBulkAsRead(
            @DestinationVariable String roomId,
            @Payload Map<String, Set<String>> payload,
            Principal principal
    ) {
        String username = principal.getName();
        Set<String> messageIds = payload.get("messageIds");

        log.debug("User {} marking {} messages as read in room {}", username, messageIds.size(), roomId);

        readReceiptService.markBulkAsRead(messageIds, username, roomId);
    }

    @MessageMapping("/unread/{roomId}")
    @SendToUser("/queue/unread")
    public Map<String, Object> getUnreadCount(
            @DestinationVariable String roomId,
            Principal principal
    ) {
        String username = principal.getName();
        long count = readReceiptService.getUnreadCount(username, roomId);

        return Map.of(
                "roomId", roomId,
                "unreadCount", count,
                "timestamp", System.currentTimeMillis()
        );
    }
}