package com.smartvoyage.messaging.controller;

import com.smartvoyage.messaging.dto.FollowUpRequest;
import com.smartvoyage.messaging.dto.FollowUpResponse;
import com.smartvoyage.messaging.service.FollowUpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FollowUpController {
    private final FollowUpService followUpService;

    @GetMapping("/conversations/{id}/follow-ups")
    public List<FollowUpResponse> byConversation(@PathVariable Long id, @RequestParam Integer userId) {
        return followUpService.byConversation(id, userId);
    }

    @PostMapping("/conversations/{id}/follow-ups")
    public FollowUpResponse create(@PathVariable Long id,
                                   @RequestParam Integer userId,
                                   @Valid @RequestBody FollowUpRequest request) {
        return followUpService.create(id, userId, request);
    }

    @PatchMapping("/follow-ups/{id}")
    public FollowUpResponse update(@PathVariable Long id,
                                   @RequestParam Integer userId,
                                   @Valid @RequestBody FollowUpRequest request) {
        return followUpService.update(id, userId, request);
    }

    @PatchMapping("/follow-ups/{id}/done")
    public FollowUpResponse done(@PathVariable Long id, @RequestParam Integer userId) {
        return followUpService.done(id, userId);
    }

    @DeleteMapping("/follow-ups/{id}")
    public void delete(@PathVariable Long id, @RequestParam Integer userId) {
        followUpService.delete(id, userId);
    }
}
