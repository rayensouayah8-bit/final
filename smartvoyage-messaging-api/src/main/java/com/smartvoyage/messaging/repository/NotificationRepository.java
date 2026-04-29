package com.smartvoyage.messaging.repository;

import com.smartvoyage.messaging.model.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(Integer recipientUserId, Pageable pageable);

    long countByRecipientUserIdAndIsReadFalse(Integer recipientUserId);
}
