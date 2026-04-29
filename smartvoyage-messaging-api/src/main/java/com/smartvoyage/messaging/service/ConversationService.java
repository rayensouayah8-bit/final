package com.smartvoyage.messaging.service;

import com.smartvoyage.messaging.exception.AppException;
import com.smartvoyage.messaging.model.entity.Conversation;
import com.smartvoyage.messaging.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {
    private final ConversationRepository conversationRepository;

    @Transactional
    public Conversation ensureConversation(Long eventId, Integer travelerUserId, Integer agencyUserId) {
        if (travelerUserId == null || agencyUserId == null) {
            throw new AppException("Both traveler and agency users are required.");
        }
        if (travelerUserId.equals(agencyUserId)) {
            throw new AppException("Traveler and agency cannot be same user.");
        }
        if (eventId != null) {
            return conversationRepository
                    .findByEventIdAndTravelerUserIdAndAgencyUserId(eventId, travelerUserId, agencyUserId)
                    .orElseGet(() -> saveNew(eventId, travelerUserId, agencyUserId));
        }
        return conversationRepository
                .findByEventIdIsNullAndTravelerUserIdAndAgencyUserId(travelerUserId, agencyUserId)
                .orElseGet(() -> saveNew(null, travelerUserId, agencyUserId));
    }

    public List<Conversation> myConversations(Integer userId) {
        return conversationRepository.findByTravelerUserIdOrAgencyUserIdOrderByUpdatedAtDesc(userId, userId);
    }

    public Conversation getOrThrow(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException("Conversation not found."));
    }

    public void assertParticipant(Long conversationId, Integer userId) {
        Conversation c = getOrThrow(conversationId);
        if (!userId.equals(c.getTravelerUserId()) && !userId.equals(c.getAgencyUserId())) {
            throw new AppException("Access denied for this conversation.");
        }
    }

    public Integer counterpartUserId(Long conversationId, Integer userId) {
        Conversation c = getOrThrow(conversationId);
        if (userId.equals(c.getTravelerUserId())) return c.getAgencyUserId();
        if (userId.equals(c.getAgencyUserId())) return c.getTravelerUserId();
        throw new AppException("User is not participant.");
    }

    @Transactional
    public void touchConversation(Long conversationId) {
        Conversation c = getOrThrow(conversationId);
        c.setLastMessageAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(c);
    }

    public long totalConversations() {
        return conversationRepository.count();
    }

    private Conversation saveNew(Long eventId, Integer travelerUserId, Integer agencyUserId) {
        Conversation c = new Conversation();
        c.setEventId(eventId);
        c.setTravelerUserId(travelerUserId);
        c.setAgencyUserId(agencyUserId);
        return conversationRepository.save(c);
    }
}
