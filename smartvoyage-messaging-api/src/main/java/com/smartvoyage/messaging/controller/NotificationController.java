package com.smartvoyage.messaging.controller;

import com.smartvoyage.messaging.dto.NotificationResponse;
import com.smartvoyage.messaging.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> list(@RequestParam Integer userId,
                                           @RequestParam(defaultValue = "20") int size) {
        return notificationService.latest(userId, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread(@RequestParam Integer userId) {
        return Map.of("unreadCount", notificationService.unreadCount(userId));
    }
}
