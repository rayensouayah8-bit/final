package com.smartvoyage.messaging.dto;

import com.smartvoyage.messaging.model.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationResponse {
    private Long id;
    private NotificationType type;
    private String title;
    private String body;
    private Long conversationId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
