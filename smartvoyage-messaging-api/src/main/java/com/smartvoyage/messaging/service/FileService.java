package com.smartvoyage.messaging.service;

import com.smartvoyage.messaging.config.AppProperties;
import com.smartvoyage.messaging.dto.FileUploadResponse;
import com.smartvoyage.messaging.exception.AppException;
import com.smartvoyage.messaging.model.entity.StoredFile;
import com.smartvoyage.messaging.repository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {
    private static final Set<String> ALLOWED = Set.of(
            "image/jpeg", "image/png",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "audio/mpeg", "audio/wav", "audio/ogg", "audio/mp4");

    private final AppProperties appProperties;
    private final StoredFileRepository storedFileRepository;
    private final ConversationService conversationService;

    public FileUploadResponse upload(Long conversationId, Integer userId, MultipartFile file, boolean audioOnly) {
        conversationService.assertParticipant(conversationId, userId);
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        if (!ALLOWED.contains(contentType)) {
            throw new AppException("Unsupported file type: " + contentType);
        }
        if (audioOnly && !contentType.startsWith("audio/")) {
            throw new AppException("Only audio files are allowed for voice message endpoint.");
        }
        try {
            Path base = Paths.get(audioOnly ? appProperties.getUpload().getAudioDir() : appProperties.getUpload().getBaseDir());
            Files.createDirectories(base);
            String safeName = UUID.randomUUID() + "_" + file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
            Path target = base.resolve(safeName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            StoredFile sf = new StoredFile();
            sf.setOriginalFilename(file.getOriginalFilename());
            sf.setStorageFilename(safeName);
            sf.setContentType(contentType);
            sf.setSizeBytes(file.getSize());
            sf.setFilePath(target.toString());
            sf.setUploadedByUserId(userId);
            sf.setConversationId(conversationId);
            sf = storedFileRepository.save(sf);

            return FileUploadResponse.builder()
                    .fileId(sf.getId())
                    .fileUrl("/api/files/" + sf.getStorageFilename() + "?conversationId=" + conversationId + "&userId=" + userId)
                    .contentType(sf.getContentType())
                    .sizeBytes(sf.getSizeBytes())
                    .build();
        } catch (IOException e) {
            throw new AppException("Upload failed: " + e.getMessage());
        }
    }

    public ResponseEntity<Resource> download(String filename, Long conversationId, Integer userId) {
        conversationService.assertParticipant(conversationId, userId);
        StoredFile sf = storedFileRepository.findAll().stream()
                .filter(f -> filename.equals(f.getStorageFilename()))
                .findFirst()
                .orElseThrow(() -> new AppException("File not found"));
        if (!conversationId.equals(sf.getConversationId())) {
            throw new AppException("File does not belong to this conversation.");
        }
        Resource resource = new FileSystemResource(sf.getFilePath());
        if (!resource.exists()) throw new AppException("File missing on disk.");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sf.getOriginalFilename() + "\"")
                .contentType(MediaType.parseMediaType(sf.getContentType()))
                .body(resource);
    }
}
