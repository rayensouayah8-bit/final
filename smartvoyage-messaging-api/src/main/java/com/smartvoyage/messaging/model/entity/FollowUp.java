package com.smartvoyage.messaging.model.entity;

import com.smartvoyage.messaging.model.enums.FollowUpPriority;
import com.smartvoyage.messaging.model.enums.FollowUpStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "sv_follow_up",
        indexes = {
                @Index(name = "idx_followup_conversation_status", columnList = "conversationId,status"),
                @Index(name = "idx_followup_assigned_status", columnList = "assignedToUserId,status")
        })
@Getter
@Setter
public class FollowUp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long conversationId;
    private Integer createdByUserId;
    private Integer assignedToUserId;

    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private FollowUpStatus status;

    @Enumerated(EnumType.STRING)
    private FollowUpPriority priority;

    private LocalDateTime dueDate;
    private LocalDateTime doneAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) status = FollowUpStatus.OPEN;
        if (priority == null) priority = FollowUpPriority.MEDIUM;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
