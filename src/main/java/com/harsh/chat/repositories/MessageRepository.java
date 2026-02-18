package com.harsh.chat.repositories;

import com.harsh.chat.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    Page<Message> findByRoomIdOrderByTimestampDesc(String roomId, Pageable pageable);

    List<Message> findTop50ByRoomIdOrderByTimestampDesc(String roomId);

    long countByRoomId(String roomId);

    void deleteByRoomId(String roomId);

}
