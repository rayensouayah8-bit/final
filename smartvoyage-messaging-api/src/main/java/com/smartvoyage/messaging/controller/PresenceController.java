package com.smartvoyage.messaging.controller;

import com.smartvoyage.messaging.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
public class PresenceController {
    private final MessageService messageService;

    @PostMapping("/online")
    public Map<String, Object> online(@RequestParam Integer userId, @RequestParam boolean online) {
        messageService.setOnline(userId, online);
        return Map.of("userId", userId, "online", online);
    }
}
