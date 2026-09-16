package com.kahoot.kahoot_backend.controller;

import com.kahoot.kahoot_backend.DTOs.upload.FileUploadResponse;
import com.kahoot.kahoot_backend.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class UploadController {
    private final UploadService uploadService;

    @PostMapping(value = "/api/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type) {
        FileUploadResponse response = uploadService.store(file, type);

        return ResponseEntity.ok(response);
    }
}
