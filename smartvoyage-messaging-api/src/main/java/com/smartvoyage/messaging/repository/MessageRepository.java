package com.smartvoyage.messaging.repository;

import com.smartvoyage.messaging.model.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {
    Page<Message> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    Page<Message> findByContentContainingIgnoreCaseOrderByCreatedAtDesc(String keyword, Pageable pageable);

    Optional<Message> findFirstBySenderUserIdAndConversationIdOrderByCreatedAtDesc(Integer senderUserId, Long conversationId);

    long countBySenderUserIdAndCreatedAtAfter(Integer senderUserId, LocalDateTime threshold);

    List<Message> findByConversationIdAndSenderUserIdAndCreatedAtAfter(Long conversationId, Integer senderUserId, LocalDateTime threshold);
}
