package com.smartvoyage.messaging.dto;

import com.smartvoyage.messaging.model.enums.MessageType;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MessageRequest {
    @Size(max = 5000)
    private String content;
    private MessageType type = MessageType.TEXT;
    private String fileUrl;
    private Long fileId;
}
