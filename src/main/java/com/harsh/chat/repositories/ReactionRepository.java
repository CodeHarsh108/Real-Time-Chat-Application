package com.harsh.chat.repositories;

import com.harsh.chat.entity.Reaction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReactionRepository extends MongoRepository<Reaction, String> {

    List<Reaction> findByMessageId(String messageId);

    List<Reaction> findByMessageIdAndEmoji(String messageId, String emoji);

    Optional<Reaction> findByMessageIdAndUsername(String messageId, String username);

    @Query("{ 'messageId': ?0, 'username': ?1 }")
    Reaction findUserReaction(String messageId, String username);

    void deleteByMessageIdAndUsername(String messageId, String username);

    long countByMessageId(String messageId);

    long countByMessageIdAndEmoji(String messageId, String emoji);
}