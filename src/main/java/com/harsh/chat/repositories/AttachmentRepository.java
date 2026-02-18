package com.harsh.chat.repositories;

import com.harsh.chat.entity.Attachment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttachmentRepository extends MongoRepository<Attachment, String> {
    Optional<Attachment> findByMessageId(String messageId);

    List<Attachment> findByRoomId(String roomId);

    List<Attachment> findByUploadedBy(String username);

    void deleteByMessageId(String messageId);
}
