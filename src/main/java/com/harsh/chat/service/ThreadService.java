package com.harsh.chat.service;

import com.harsh.chat.entity.Message;
import com.harsh.chat.entity.MessageStatus;
import com.harsh.chat.payload.ReplyDTO;
import com.harsh.chat.payload.MessageRequest;
import com.harsh.chat.payload.MessageResponse;
import com.harsh.chat.repositories.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThreadService {

    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;

    /**
     * Reply to a message (create thread)
     */
    @Transactional
    public MessageResponse replyToMessage(String parentMessageId, String roomId, String sender, String content) {
        log.info("User {} replying to message {} in room {}", sender, parentMessageId, roomId);

        Message parentMessage = messageRepository.findById(parentMessageId)
                .orElseThrow(() -> new RuntimeException("Parent message not found: " + parentMessageId));

        Message reply = Message.builder()
                .roomId(roomId)
                .sender(sender)
                .content(content)
                .timestamp(LocalDateTime.now())
                .sentAt(LocalDateTime.now())
                .status(MessageStatus.SENT)
                .parentMessageId(parentMessageId)
                .hasReplies(false)
                .replyCount(0)
                .readBy(new java.util.HashSet<>())
                .deliveredTo(new java.util.HashSet<>())
                .reactions(new java.util.HashMap<>())
                .reactionCounts(new java.util.HashMap<>())
                .build();

        Message savedReply = messageRepository.save(reply);

        parentMessage.addReply(savedReply.getId());
        messageRepository.save(parentMessage);

        MessageResponse response = MessageResponse.from(savedReply);

        ReplyDTO replyDTO = ReplyDTO.builder()
                .type("REPLY")
                .parentMessageId(parentMessageId)
                .replyMessageId(savedReply.getId())
                .roomId(roomId)
                .sender(sender)
                .content(content)
                .timestamp(LocalDateTime.now())
                .parentSender(parentMessage.getSender())
                .parentContent(parentMessage.getContent())
                .parentHasAttachment(parentMessage.isHasAttachment())
                .parentAttachmentType(parentMessage.getAttachmentType())
                .replyCount(parentMessage.getReplyCount())
                .hasReplies(true)
                .build();

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/replies", replyDTO);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, response);

        log.info("Reply created: {} for parent: {}", savedReply.getId(), parentMessageId);

        return response;
    }

    /**
     * Get all replies for a message (thread)
     */
    public List<MessageResponse> getThreadReplies(String parentMessageId, int page, int size) {
        log.info("Getting replies for message: {}, page: {}, size: {}", parentMessageId, page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").ascending());

        Page<Message> repliesPage = messageRepository.findByParentMessageId(parentMessageId, pageable);

        return repliesPage.getContent().stream()
                .map(MessageResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Get thread info for a message
     */
    public ReplyDTO getThreadInfo(String messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found: " + messageId));

        return ReplyDTO.builder()
                .type("THREAD_UPDATE")
                .parentMessageId(messageId)
                .roomId(message.getRoomId())
                .replyCount(message.getReplyCount())
                .hasReplies(message.isHasReplies())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Delete a reply
     */
    @Transactional
    public void deleteReply(String replyId, String username) {
        Message reply = messageRepository.findById(replyId)
                .orElseThrow(() -> new RuntimeException("Reply not found: " + replyId));

        if (!reply.getSender().equals(username)) {
            throw new RuntimeException("You can only delete your own replies");
        }

        String parentId = reply.getParentMessageId();

        messageRepository.delete(reply);

        if (parentId != null) {
            Message parent = messageRepository.findById(parentId).orElse(null);
            if (parent != null) {
                parent.removeReply(replyId);
                messageRepository.save(parent);

                ReplyDTO update = ReplyDTO.builder()
                        .type("THREAD_UPDATE")
                        .parentMessageId(parentId)
                        .roomId(parent.getRoomId())
                        .replyCount(parent.getReplyCount())
                        .hasReplies(parent.isHasReplies())
                        .timestamp(LocalDateTime.now())
                        .build();

                messagingTemplate.convertAndSend("/topic/room/" + parent.getRoomId() + "/replies", update);
            }
        }

        log.info("Reply deleted: {}", replyId);
    }
}