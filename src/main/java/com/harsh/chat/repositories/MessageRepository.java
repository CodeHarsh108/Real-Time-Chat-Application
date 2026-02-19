package com.harsh.chat.repositories;

import com.harsh.chat.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

 Page<Message> findByRoomIdOrderByTimestampDesc(String roomId, Pageable pageable);

 Set<Message> findByRoomIdOrderByTimestampDesc(String roomId);

 List<Message> findTop50ByRoomIdOrderByTimestampDesc(String roomId);

 long countByRoomId(String roomId);

 void deleteByRoomId(String roomId);

 @Query("{ 'roomId': ?0, 'timestamp': { $gt: ?1 }, 'sender': { $ne: ?2 } }")
 long countByRoomIdAndTimestampAfterAndSenderNot(String roomId, LocalDateTime timestamp, String sender);

 @Query("{ 'roomId': ?0, 'readBy': { $ne: ?1 }, 'sender': { $ne: ?1 } }")
 List<Message> findUnreadMessagesByRoom(String roomId, String username);

 Page<Message> findByParentMessageId(String parentMessageId, Pageable pageable);

 List<Message> findByParentMessageIdOrderByTimestampAsc(String parentMessageId);

 long countByParentMessageId(String parentMessageId);

 @Query("{ 'roomId': ?0, 'parentMessageId': { $exists: true, $ne: null } }")
 List<Message> findAllRepliesInRoom(String roomId);

 @Query("{ 'sender': ?0, 'parentMessageId': { $ne: null } }")
 List<Message> findRepliesByUser(String username);


}