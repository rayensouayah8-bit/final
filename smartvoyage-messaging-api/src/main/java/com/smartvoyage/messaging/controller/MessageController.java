package com.smartvoyage.messaging.controller;

import com.smartvoyage.messaging.dto.MessageRequest;
import com.smartvoyage.messaging.dto.MessageResponse;
import com.smartvoyage.messaging.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MessageController {
    private final MessageService messageService;

    @GetMapping("/conversations/{id}/messages")
    public Page<MessageResponse> paginatedMessages(@PathVariable Long id,
                                                   @RequestParam Integer userId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return messageService.messages(id, userId, PageRequest.of(page, size));
    }

    @PostMapping("/conversations/{id}/messages")
    public MessageResponse send(@PathVariable Long id,
                                @RequestParam Integer userId,
                                @Valid @RequestBody MessageRequest request) {
        return messageService.send(id, userId, request);
    }

    @PatchMapping("/messages/{id}")
    public MessageResponse update(@PathVariable Long id,
                                  @RequestParam Integer userId,
                                  @RequestBody MessageRequest request) {
        return messageService.update(id, userId, request);
    }

    @DeleteMapping("/messages/{id}")
    public void delete(@PathVariable Long id, @RequestParam Integer userId) {
        messageService.delete(id, userId);
    }

    @GetMapping("/messages/search")
    public Page<MessageResponse> search(@RequestParam Integer userId,
                                        @RequestParam String keyword,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return messageService.search(userId, keyword, PageRequest.of(page, size));
    }

    @PatchMapping("/messages/{id}/delivered")
    public MessageResponse delivered(@PathVariable Long id, @RequestParam Integer userId) {
        return messageService.markDelivered(id, userId);
    }

    @PatchMapping("/messages/{id}/read")
    public MessageResponse read(@PathVariable Long id, @RequestParam Integer userId) {
        return messageService.markRead(id, userId);
    }
}
