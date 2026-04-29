package com.smartvoyage.messaging.repository;

import com.smartvoyage.messaging.model.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByTravelerUserIdOrAgencyUserIdOrderByUpdatedAtDesc(Integer travelerUserId, Integer agencyUserId);

    Optional<Conversation> findByEventIdAndTravelerUserIdAndAgencyUserId(Long eventId, Integer travelerUserId, Integer agencyUserId);

    Optional<Conversation> findByEventIdIsNullAndTravelerUserIdAndAgencyUserId(Integer travelerUserId, Integer agencyUserId);
}
