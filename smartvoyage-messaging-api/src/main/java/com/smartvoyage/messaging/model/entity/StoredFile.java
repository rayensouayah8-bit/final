package com.smartvoyage.messaging.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "sv_file")
@Getter
@Setter
public class StoredFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String originalFilename;
    private String storageFilename;
    private String contentType;
    private Long sizeBytes;
    private String filePath;

    private Integer uploadedByUserId;
    private Long conversationId;
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
