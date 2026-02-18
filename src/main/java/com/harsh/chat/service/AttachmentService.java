package com.harsh.chat.service;

import com.harsh.chat.entity.Attachment;
import com.harsh.chat.entity.Message;
import com.harsh.chat.repositories.AttachmentRepository;
import com.harsh.chat.repositories.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final MessageRepository messageRepository;

    /**
     * Get attachment by ID
     */
    public Attachment getAttachment(String attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found: " + attachmentId));
    }

    /**
     * Get attachments for a message
     */
    public Attachment getAttachmentByMessageId(String messageId) {
        return attachmentRepository.findByMessageId(messageId)
                .orElse(null);
    }

    /**
     * Get all attachments for a room
     */
    public List<Attachment> getRoomAttachments(String roomId) {
        return attachmentRepository.findByRoomId(roomId);
    }

    /**
     * Link attachment to message
     */
    @Transactional
    public Message linkAttachmentToMessage(String messageId, String attachmentId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found: " + messageId));

        Attachment attachment = getAttachment(attachmentId);

        message.setAttachmentId(attachmentId);
        message.setHasAttachment(true);
        message.setAttachmentName(attachment.getFileName());
        message.setAttachmentUrl(attachment.getFileUrl());
        message.setThumbnailUrl(attachment.getThumbnailUrl());
        message.setAttachmentSize(attachment.getFileSize());

        // Set attachment type based on mime type
        if (attachment.getFileType().startsWith("image/")) {
            message.setAttachmentType("image");
        } else if (attachment.getFileType().startsWith("video/")) {
            message.setAttachmentType("video");
        } else if (attachment.getFileType().startsWith("audio/")) {
            message.setAttachmentType("audio");
        } else {
            message.setAttachmentType("document");
        }

        Message savedMessage = messageRepository.save(message);

        // Update attachment with message ID
        attachment.setMessageId(messageId);
        attachmentRepository.save(attachment);

        log.info("Linked attachment {} to message {}", attachmentId, messageId);

        return savedMessage;
    }

    /**
     * Delete attachment and update message
     */
    @Transactional
    public void deleteAttachment(String attachmentId) {
        Attachment attachment = getAttachment(attachmentId);

        if (attachment.getMessageId() != null) {
            Message message = messageRepository.findById(attachment.getMessageId()).orElse(null);
            if (message != null) {
                message.setHasAttachment(false);
                message.setAttachmentId(null);
                message.setAttachmentType(null);
                message.setAttachmentName(null);
                message.setAttachmentUrl(null);
                message.setThumbnailUrl(null);
                messageRepository.save(message);
            }
        }

        attachmentRepository.delete(attachment);
        log.info("Deleted attachment: {}", attachmentId);
    }
}