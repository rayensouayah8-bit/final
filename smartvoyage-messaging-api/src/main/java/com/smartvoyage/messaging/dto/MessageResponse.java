package com.smartvoyage.messaging.dto;

import com.smartvoyage.messaging.model.enums.MessageStatus;
import com.smartvoyage.messaging.model.enums.MessageType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageResponse {
    private Long id;
    private Long conversationId;
    private Integer senderUserId;
    private String content;
    private MessageType type;
    private MessageStatus status;
    private String fileUrl;
    private Boolean isSpam;
    private LocalDateTime createdAt;
}
