package com.smartvoyage.messaging.dto;

import com.smartvoyage.messaging.model.enums.FollowUpPriority;
import com.smartvoyage.messaging.model.enums.FollowUpStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FollowUpResponse {
    private Long id;
    private Long conversationId;
    private String title;
    private String description;
    private Integer createdByUserId;
    private Integer assignedToUserId;
    private FollowUpStatus status;
    private FollowUpPriority priority;
    private LocalDateTime dueDate;
    private LocalDateTime doneAt;
    private LocalDateTime createdAt;
}
