package com.smartvoyage.messaging.service;

import com.smartvoyage.messaging.config.AppProperties;
import com.smartvoyage.messaging.model.entity.Message;
import com.smartvoyage.messaging.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
@RequiredArgsConstructor
public class AntiSpamService {
    private final AppProperties appProperties;
    private final MessageRepository messageRepository;

    private final ConcurrentHashMap<Integer, ConcurrentLinkedDeque<Long>> perUserTimestamps = new ConcurrentHashMap<>();

    public boolean isSpam(Long conversationId, Integer userId, String content) {
        return exceedsRate(userId) || isDuplicate(conversationId, userId, content) || containsBadWords(content);
    }

    private boolean exceedsRate(Integer userId) {
        long now = System.currentTimeMillis();
        long windowMs = appProperties.getAntiSpam().getWindowSeconds() * 1000L;
        int maxMessages = appProperties.getAntiSpam().getMaxMessages();

        ConcurrentLinkedDeque<Long> deque = perUserTimestamps.computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());
        while (!deque.isEmpty() && now - deque.peekFirst() > windowMs) {
            deque.pollFirst();
        }
        deque.addLast(now);
        return deque.size() > maxMessages;
    }

    private boolean isDuplicate(Long conversationId, Integer userId, String content) {
        if (content == null || content.isBlank()) return false;
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(appProperties.getAntiSpam().getDuplicateWindowSeconds());
        return messageRepository.findByConversationIdAndSenderUserIdAndCreatedAtAfter(conversationId, userId, threshold).stream()
                .map(Message::getContent)
                .anyMatch(c -> c != null && c.trim().equalsIgnoreCase(content.trim()));
    }

    private boolean containsBadWords(String content) {
        if (content == null || content.isBlank()) return false;
        String normalized = content.toLowerCase(Locale.ROOT);
        Set<String> words = new HashSet<>(appProperties.getAntiSpam().getBadWords());
        for (String word : words) {
            if (word != null && !word.isBlank() && normalized.contains(word.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
