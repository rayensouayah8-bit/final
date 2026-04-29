package com.smartvoyage.messaging.service;

import com.smartvoyage.messaging.dto.FollowUpRequest;
import com.smartvoyage.messaging.dto.FollowUpResponse;
import com.smartvoyage.messaging.exception.AppException;
import com.smartvoyage.messaging.model.entity.FollowUp;
import com.smartvoyage.messaging.model.enums.FollowUpPriority;
import com.smartvoyage.messaging.model.enums.FollowUpStatus;
import com.smartvoyage.messaging.model.enums.NotificationType;
import com.smartvoyage.messaging.repository.FollowUpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FollowUpService {
    private final FollowUpRepository followUpRepository;
    private final ConversationService conversationService;
    private final NotificationService notificationService;
    private final TwilioService twilioService;

    @Transactional
    public FollowUpResponse create(Long conversationId, Integer userId, FollowUpRequest request) {
        conversationService.assertParticipant(conversationId, userId);
        Integer assignedTo = request.getAssignedToUserId() != null
                ? request.getAssignedToUserId()
                : conversationService.counterpartUserId(conversationId, userId);

        FollowUp fu = new FollowUp();
        fu.setConversationId(conversationId);
        fu.setCreatedByUserId(userId);
        fu.setAssignedToUserId(assignedTo);
        fu.setTitle(request.getTitle().trim());
        fu.setDescription(request.getDescription());
        fu.setPriority(request.getPriority() == null ? FollowUpPriority.MEDIUM : request.getPriority());
        fu.setDueDate(request.getDueDate());

        FollowUp saved = followUpRepository.save(fu);
        notificationService.create(assignedTo, userId, conversationId, saved.getId(),
                NotificationType.FOLLOW_UP_ASSIGNED, "Follow-up assigned", saved.getTitle());
        twilioService.sendSms(null, "[SmartVoyage] Follow-up assigned: " + saved.getTitle());
        return map(saved);
    }

    public List<FollowUpResponse> byConversation(Long conversationId, Integer userId) {
        conversationService.assertParticipant(conversationId, userId);
        return followUpRepository.findByConversationIdOrderByCreatedAtDesc(conversationId).stream().map(this::map).toList();
    }

    @Transactional
    public FollowUpResponse update(Long id, Integer userId, FollowUpRequest request) {
        FollowUp fu = followUpRepository.findById(id).orElseThrow(() -> new AppException("Follow-up not found."));
        conversationService.assertParticipant(fu.getConversationId(), userId);
        fu.setTitle(request.getTitle().trim());
        fu.setDescription(request.getDescription());
        fu.setDueDate(request.getDueDate());
        if (request.getPriority() != null) fu.setPriority(request.getPriority());
        return map(followUpRepository.save(fu));
    }

    @Transactional
    public FollowUpResponse done(Long id, Integer userId) {
        FollowUp fu = followUpRepository.findById(id).orElseThrow(() -> new AppException("Follow-up not found."));
        conversationService.assertParticipant(fu.getConversationId(), userId);
        fu.setStatus(FollowUpStatus.DONE);
        fu.setDoneAt(LocalDateTime.now());
        return map(followUpRepository.save(fu));
    }

    @Transactional
    public void delete(Long id, Integer userId) {
        FollowUp fu = followUpRepository.findById(id).orElseThrow(() -> new AppException("Follow-up not found."));
        conversationService.assertParticipant(fu.getConversationId(), userId);
        followUpRepository.delete(fu);
    }

    @Transactional
    public void maybeAutoCreate(Long conversationId, Integer senderUserId, Integer recipientUserId, String content) {
        String title = infer(content);
        if (title == null) return;
        if (followUpRepository.existsByConversationIdAndStatusAndTitleIgnoreCase(conversationId, FollowUpStatus.OPEN, title)) return;

        FollowUpRequest req = new FollowUpRequest();
        req.setTitle(title);
        req.setDescription("Auto-generated from message");
        req.setAssignedToUserId(recipientUserId);
        req.setPriority(FollowUpPriority.MEDIUM);
        create(conversationId, senderUserId, req);
    }

    private String infer(String content) {
        if (content == null || content.isBlank()) return null;
        String n = content.toLowerCase(Locale.ROOT);
        if (n.contains("programme")) return "Share travel programme details";
        if (n.contains("devis") || n.contains("prix")) return "Prepare pricing/devis";
        if (n.contains("reservation") || n.contains("book")) return "Handle booking reservation";
        return null;
    }

    private FollowUpResponse map(FollowUp f) {
        return FollowUpResponse.builder()
                .id(f.getId())
                .conversationId(f.getConversationId())
                .title(f.getTitle())
                .description(f.getDescription())
                .createdByUserId(f.getCreatedByUserId())
                .assignedToUserId(f.getAssignedToUserId())
                .status(f.getStatus())
                .priority(f.getPriority())
                .dueDate(f.getDueDate())
                .doneAt(f.getDoneAt())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
