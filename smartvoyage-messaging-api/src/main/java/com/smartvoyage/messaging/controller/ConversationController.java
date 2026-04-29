package com.smartvoyage.messaging.controller;

import com.smartvoyage.messaging.dto.ConversationResponse;
import com.smartvoyage.messaging.model.entity.Conversation;
import com.smartvoyage.messaging.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {
    private final ConversationService conversationService;

    @PostMapping
    public ConversationResponse ensureConversation(@RequestParam(required = false) Long eventId,
                                                   @RequestParam Integer travelerUserId,
                                                   @RequestParam Integer agencyUserId) {
        return map(conversationService.ensureConversation(eventId, travelerUserId, agencyUserId));
    }

    @GetMapping
    public List<ConversationResponse> myConversations(@RequestParam Integer userId) {
        return conversationService.myConversations(userId).stream().map(this::map).toList();
    }

    private ConversationResponse map(Conversation c) {
        return ConversationResponse.builder()
                .id(c.getId())
                .eventId(c.getEventId())
                .travelerUserId(c.getTravelerUserId())
                .agencyUserId(c.getAgencyUserId())
                .updatedAt(c.getUpdatedAt())
                .lastMessageAt(c.getLastMessageAt())
                .build();
    }
}
