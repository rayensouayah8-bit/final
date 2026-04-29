package com.smartvoyage.messaging.model.entity;

import com.smartvoyage.messaging.model.enums.NotificationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "sv_notification",
        indexes = {
                @Index(name = "idx_notif_recipient_read_created", columnList = "recipientUserId,isRead,createdAt")
        })
@Getter
@Setter
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer recipientUserId;
    private Integer actorUserId;
    private Long conversationId;
    private Long followUpId;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private String title;
    @Column(columnDefinition = "TEXT")
    private String body;
    private Boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        if (isRead == null) isRead = Boolean.FALSE;
    }
}
