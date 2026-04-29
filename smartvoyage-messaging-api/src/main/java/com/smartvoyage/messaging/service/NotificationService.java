package com.smartvoyage.messaging.service;

import com.smartvoyage.messaging.config.AppProperties;
import com.smartvoyage.messaging.dto.NotificationResponse;
import com.smartvoyage.messaging.model.entity.Notification;
import com.smartvoyage.messaging.model.enums.NotificationType;
import com.smartvoyage.messaging.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AppProperties appProperties;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<Integer, Integer> pendingCount = new ConcurrentHashMap<>();
    private final Map<Integer, ScheduledFuture<?>> pendingTasks = new ConcurrentHashMap<>();

    @Transactional
    public Notification create(Integer recipientUserId, Integer actorUserId, Long conversationId, Long followUpId,
                               NotificationType type, String title, String body) {
        Notification n = new Notification();
        n.setRecipientUserId(recipientUserId);
        n.setActorUserId(actorUserId);
        n.setConversationId(conversationId);
        n.setFollowUpId(followUpId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        Notification saved = notificationRepository.save(n);
        batchAndPush(saved);
        return saved;
    }

    public long unreadCount(Integer userId) {
        return notificationRepository.countByRecipientUserIdAndIsReadFalse(userId);
    }

    public java.util.List<NotificationResponse> latest(Integer userId, int size) {
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, size))
                .stream().map(this::map).toList();
    }

    private void batchAndPush(Notification n) {
        pendingCount.merge(n.getRecipientUserId(), 1, Integer::sum);
        pendingTasks.compute(n.getRecipientUserId(), (k, existing) -> {
            if (existing != null && !existing.isDone()) existing.cancel(false);
            return scheduler.schedule(() -> flush(k), appProperties.getNotification().getBatchDelayMs(), TimeUnit.MILLISECONDS);
        });
    }

    private void flush(Integer userId) {
        int count = pendingCount.getOrDefault(userId, 0);
        if (count <= 0) return;
        pendingCount.remove(userId);

        if (count > 1) {
            Notification grouped = new Notification();
            grouped.setRecipientUserId(userId);
            grouped.setType(NotificationType.GROUPED_MESSAGES);
            grouped.setTitle("You have " + count + " new messages");
            grouped.setBody("Open conversations to review latest updates.");
            grouped = notificationRepository.save(grouped);
            messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/messages", map(grouped));
        } else {
            latest(userId, 1).stream().findFirst()
                    .ifPresent(payload -> messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/messages", payload));
        }
    }

    private NotificationResponse map(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .body(n.getBody())
                .conversationId(n.getConversationId())
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
