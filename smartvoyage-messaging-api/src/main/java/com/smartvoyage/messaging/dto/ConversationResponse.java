package com.smartvoyage.messaging.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConversationResponse {
    private Long id;
    private Long eventId;
    private Integer travelerUserId;
    private Integer agencyUserId;
    private LocalDateTime updatedAt;
    private LocalDateTime lastMessageAt;
}
