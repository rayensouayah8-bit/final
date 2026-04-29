package com.smartvoyage.messaging.repository;

import com.smartvoyage.messaging.model.entity.FollowUp;
import com.smartvoyage.messaging.model.enums.FollowUpStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FollowUpRepository extends JpaRepository<FollowUp, Long> {
    List<FollowUp> findByConversationIdOrderByCreatedAtDesc(Long conversationId);

    boolean existsByConversationIdAndStatusAndTitleIgnoreCase(Long conversationId, FollowUpStatus status, String title);
}
