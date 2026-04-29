package com.smartvoyage.messaging.dto;

import lombok.Data;

@Data
public class TypingPayload {
    private Long conversationId;
    private Integer userId;
    private Boolean typing;
}
