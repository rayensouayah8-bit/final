package com.smartvoyage.messaging.service;

import com.smartvoyage.messaging.dto.MessageRequest;
import com.smartvoyage.messaging.dto.MessageResponse;
import com.smartvoyage.messaging.exception.AppException;
import com.smartvoyage.messaging.model.entity.Message;
import com.smartvoyage.messaging.model.enums.MessageStatus;
import com.smartvoyage.messaging.model.enums.MessageType;
import com.smartvoyage.messaging.model.enums.NotificationType;
import com.smartvoyage.messaging.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class MessageService {
    private final MessageRepository messageRepository;
    private final ConversationService conversationService;
    private final AntiSpamService antiSpamService;
    private final NotificationService notificationService;
    private final FollowUpService followUpService;
    private final SimpMessagingTemplate messagingTemplate;

    private final Map<Integer, Boolean> onlineUsers = new ConcurrentHashMap<>();

    @Transactional
    public MessageResponse send(Long conversationId, Integer senderUserId, MessageRequest request) {
        conversationService.assertParticipant(conversationId, senderUserId);
        String content = request.getContent() == null ? "" : request.getContent().trim();
        boolean spam = antiSpamService.isSpam(conversationId, senderUserId, content);
        if (spam) {
            throw new AppException("Message rejected by anti-spam protection.");
        }

        Message m = new Message();
        m.setConversationId(conversationId);
        m.setSenderUserId(senderUserId);
        m.setContent(content);
        m.setType(request.getType() == null ? MessageType.TEXT : request.getType());
        m.setFileUrl(request.getFileUrl());
        m.setFileId(request.getFileId());
        m.setStatus(MessageStatus.SENT);
        m.setIsSpam(false);
        m = messageRepository.save(m);

        conversationService.touchConversation(conversationId);
        Integer recipient = conversationService.counterpartUserId(conversationId, senderUserId);
        notificationService.create(recipient, senderUserId, conversationId, null,
                NotificationType.NEW_MESSAGE, "New message received", content);
        followUpService.maybeAutoCreate(conversationId, senderUserId, recipient, content);

        MessageResponse payload = map(m);
        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, payload);
        messagingTemplate.convertAndSendToUser(String.valueOf(recipient), "/queue/messages", payload);
        return payload;
    }

    public Page<MessageResponse> messages(Long conversationId, Integer userId, Pageable pageable) {
        conversationService.assertParticipant(conversationId, userId);
        return messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable).map(this::map);
    }

    public Page<MessageResponse> search(Integer userId, String keyword, Pageable pageable) {
        Page<Message> page = messageRepository.findByContentContainingIgnoreCaseOrderByCreatedAtDesc(keyword, pageable);
        Set<Long> allowed = conversationService.myConversations(userId).stream().map(c -> c.getId()).collect(java.util.stream.Collectors.toSet());
        var filtered = page.getContent().stream().filter(m -> allowed.contains(m.getConversationId())).map(this::map).toList();
        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    @Transactional
    public MessageResponse update(Long id, Integer userId, MessageRequest request) {
        Message m = messageRepository.findById(id).orElseThrow(() -> new AppException("Message not found."));
        conversationService.assertParticipant(m.getConversationId(), userId);
        if (!userId.equals(m.getSenderUserId())) throw new AppException("Only sender can edit.");
        m.setContent(request.getContent());
        return map(messageRepository.save(m));
    }

    @Transactional
    public void delete(Long id, Integer userId) {
        Message m = messageRepository.findById(id).orElseThrow(() -> new AppException("Message not found."));
        conversationService.assertParticipant(m.getConversationId(), userId);
        if (!userId.equals(m.getSenderUserId())) throw new AppException("Only sender can delete.");
        messageRepository.delete(m);
    }

    @Transactional
    public MessageResponse markDelivered(Long id, Integer userId) {
        Message m = messageRepository.findById(id).orElseThrow(() -> new AppException("Message not found."));
        conversationService.assertParticipant(m.getConversationId(), userId);
        m.setStatus(MessageStatus.DELIVERED);
        return map(messageRepository.save(m));
    }

    @Transactional
    public MessageResponse markRead(Long id, Integer userId) {
        Message m = messageRepository.findById(id).orElseThrow(() -> new AppException("Message not found."));
        conversationService.assertParticipant(m.getConversationId(), userId);
        m.setStatus(MessageStatus.READ);
        return map(messageRepository.save(m));
    }

    public void typing(Long conversationId, Integer userId, boolean typing) {
        conversationService.assertParticipant(conversationId, userId);
        Integer recipient = conversationService.counterpartUserId(conversationId, userId);
        messagingTemplate.convertAndSendToUser(String.valueOf(recipient), "/queue/messages",
                Map.of("type", "TYPING", "conversationId", conversationId, "userId", userId, "typing", typing));
    }

    public void setOnline(Integer userId, boolean online) {
        onlineUsers.put(userId, online);
    }

    public long totalMessages() {
        return messageRepository.count();
    }

    public long spamCount() {
        return messageRepository.findAll().stream().filter(m -> Boolean.TRUE.equals(m.getIsSpam())).count();
    }

    private MessageResponse map(Message m) {
        return MessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversationId())
                .senderUserId(m.getSenderUserId())
                .content(m.getContent())
                .type(m.getType())
                .status(m.getStatus())
                .fileUrl(m.getFileUrl())
                .isSpam(m.getIsSpam())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
