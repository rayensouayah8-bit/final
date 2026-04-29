package com.smartvoyage.messaging.controller;

import com.smartvoyage.messaging.dto.FileUploadResponse;
import com.smartvoyage.messaging.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FileController {
    private final FileService fileService;

    @PostMapping("/messages/upload")
    public FileUploadResponse upload(@RequestParam Long conversationId,
                                     @RequestParam Integer userId,
                                     @RequestParam("file") MultipartFile file) {
        return fileService.upload(conversationId, userId, file, false);
    }

    @PostMapping("/messages/voice")
    public FileUploadResponse uploadVoice(@RequestParam Long conversationId,
                                          @RequestParam Integer userId,
                                          @RequestParam("file") MultipartFile file) {
        return fileService.upload(conversationId, userId, file, true);
    }

    @GetMapping("/files/{filename}")
    public ResponseEntity<Resource> download(@PathVariable String filename,
                                             @RequestParam Long conversationId,
                                             @RequestParam Integer userId) {
        return fileService.download(filename, conversationId, userId);
    }
}
