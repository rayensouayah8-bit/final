package com.smartvoyage.messaging.controller;

import com.smartvoyage.messaging.service.ConversationService;
import com.smartvoyage.messaging.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {
    private final MessageService messageService;
    private final ConversationService conversationService;

    @GetMapping("/messages")
    public Map<String, Object> messageStats() {
        return Map.of(
                "totalMessages", messageService.totalMessages(),
                "spamMessages", messageService.spamCount(),
                "totalConversations", conversationService.totalConversations()
        );
    }
}
