package com.smartvoyage.messaging.model.entity;

import com.smartvoyage.messaging.model.enums.MessageStatus;
import com.smartvoyage.messaging.model.enums.MessageType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "sv_message",
        indexes = {
                @Index(name = "idx_msg_conversation_created", columnList = "conversationId,createdAt"),
                @Index(name = "idx_msg_sender_created", columnList = "senderUserId,createdAt")
        })
@Getter
@Setter
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long conversationId;
    private Integer senderUserId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    private MessageType type;

    @Enumerated(EnumType.STRING)
    private MessageStatus status;

    private String fileUrl;
    private Long fileId;
    private Boolean isSpam;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) {
            status = MessageStatus.SENT;
        }
        if (type == null) {
            type = MessageType.TEXT;
        }
        if (isSpam == null) {
            isSpam = Boolean.FALSE;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
