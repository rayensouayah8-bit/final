package com.smartvoyage.messaging.dto;

import com.smartvoyage.messaging.model.enums.FollowUpPriority;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FollowUpRequest {
    @NotBlank
    private String title;
    private String description;
    private Integer assignedToUserId;
    private FollowUpPriority priority = FollowUpPriority.MEDIUM;
    private LocalDateTime dueDate;
}
