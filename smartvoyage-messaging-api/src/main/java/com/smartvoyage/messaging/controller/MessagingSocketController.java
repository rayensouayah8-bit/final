package com.smartvoyage.messaging.controller;

import com.smartvoyage.messaging.dto.MessageRequest;
import com.smartvoyage.messaging.dto.MessageResponse;
import com.smartvoyage.messaging.dto.TypingPayload;
import com.smartvoyage.messaging.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class MessagingSocketController {
    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/conversations/{conversationId}/send")
    public void send(@DestinationVariable Long conversationId, @Payload SocketMessageEnvelope envelope) {
        MessageRequest req = new MessageRequest();
        req.setContent(envelope.content());
        MessageResponse response = messageService.send(conversationId, envelope.userId(), req);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, response);
    }

    @MessageMapping("/conversations/{conversationId}/typing")
    public void typing(@DestinationVariable Long conversationId, @Payload TypingPayload payload) {
        messageService.typing(conversationId, payload.getUserId(), Boolean.TRUE.equals(payload.getTyping()));
    }

    public record SocketMessageEnvelope(Integer userId, String content) {}
}
