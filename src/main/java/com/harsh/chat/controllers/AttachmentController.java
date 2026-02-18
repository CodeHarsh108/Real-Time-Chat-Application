package com.harsh.chat.controllers;

import com.harsh.chat.entity.Attachment;
import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.MessageStatus;
import com.harsh.chat.payload.MessageResponse;
import com.harsh.chat.repositories.AttachmentRepository;
import com.harsh.chat.service.ChatService;
import com.harsh.chat.service.FileStorageService;
import com.harsh.chat.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
@Slf4j
public class AttachmentController {

    private final FileStorageService fileStorageService;
    private final AttachmentService attachmentService;
    private final ChatService chatService;
    private final AttachmentRepository attachmentRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Upload file attachment
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("roomId") String roomId,
            Authentication authentication
    ) {
        String username = authentication.getName();
        log.info("File upload request from user: {} for room: {}, file: {}",
                username, roomId, file.getOriginalFilename());

        try {
            Attachment attachment = fileStorageService.storeFile(file, username, roomId);

            // DEBUG: Log the complete attachment object
            log.info("ATTACHMENT CREATED: ID={}, fileName={}, fileUrl={}, thumbnailUrl={}",
                    attachment.getId(), attachment.getFileName(), attachment.getFileUrl(), attachment.getThumbnailUrl());

            Map<String, Object> response = new HashMap<>();
            response.put("attachmentId", attachment.getId());
            response.put("fileName", attachment.getFileName());
            response.put("fileType", attachment.getFileType());
            response.put("fileSize", attachment.getFileSize());
            response.put("fileUrl", attachment.getFileUrl());
            response.put("thumbnailUrl", attachment.getThumbnailUrl());
            response.put("message", "File uploaded successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("File upload failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Download file
     */
    @GetMapping("/download/{category}/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String category,
            @PathVariable String fileName,
            HttpServletRequest request
    ) {
        log.info("File download request: {}/{}", category, fileName);

        try {
            Resource resource = fileStorageService.loadFileAsResource(fileName, category);

            String contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (IOException e) {
            log.error("File download failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }


    /**
     * Serve file for preview (inline)
     */
    @GetMapping("/view/{category}/{fileName:.+}")
    public ResponseEntity<Resource> viewFile(
            @PathVariable String category,
            @PathVariable String fileName,
            HttpServletRequest request
    ) {
        log.info("File view request: {}/{}", category, fileName);

        try {
            Resource resource = fileStorageService.loadFileAsResource(fileName, category);

            String contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
            if (contentType == null) {
                if (category.equals("images")) {
                    contentType = "image/jpeg";
                } else if (category.equals("videos")) {
                    contentType = "video/mp4";
                } else if (category.equals("audio")) {
                    contentType = "audio/mpeg";
                } else if (category.equals("documents")) {
                    contentType = "application/pdf";
                } else {
                    contentType = "application/octet-stream";
                }
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (IOException e) {
            log.error("File view failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Get attachment info
     */
    @GetMapping("/{attachmentId}")
    public ResponseEntity<?> getAttachment(@PathVariable String attachmentId) {
        try {
            Attachment attachment = attachmentService.getAttachment(attachmentId);
            return ResponseEntity.ok(attachment);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete attachment
     */
    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<?> deleteAttachment(@PathVariable String attachmentId) {
        try {
            boolean deleted = fileStorageService.deleteFile(attachmentId);
            if (deleted) {
                return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Could not delete file"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
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

        log.info("Sending attachment message: attachmentId={}, roomId={}, content='{}'",
                attachmentId, roomId, content);

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

            Message message = Message.builder()
                    .roomId(roomId)
                    .sender(username)
                    .content(content != null ? content : "")
                    .timestamp(LocalDateTime.now())
                    .sentAt(LocalDateTime.now())
                    .status(MessageStatus.SENT)
                    .hasAttachment(true)
                    .attachmentId(attachmentId)
                    .attachmentType(attachmentType)
                    .attachmentName(attachment.getFileName())
                    .attachmentUrl(attachment.getFileUrl())
                    .thumbnailUrl(attachment.getThumbnailUrl())
                    .attachmentSize(attachment.getFileSize())
                    .readBy(new HashSet<>())
                    .deliveredTo(new HashSet<>())
                    .userStatus(new HashMap<>())
                    .build();

            log.info("CREATED MESSAGE WITH ATTACHMENT: content='{}', status={}",
                    message.getContent(), message.getStatus());

            // Save message
            Message savedMessage = chatService.saveAttachmentMessage(message);

            // Link attachment to message
            attachment.setMessageId(savedMessage.getId());
            attachmentRepository.save(attachment);

            // Create response with ALL fields
            MessageResponse response = MessageResponse.from(savedMessage);

            // Broadcast via WebSocket
            messagingTemplate.convertAndSend("/topic/room/" + roomId, response);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to send attachment message: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }



}