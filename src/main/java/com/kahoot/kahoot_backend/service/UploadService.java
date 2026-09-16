package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.DTOs.upload.FileUploadResponse;
import com.kahoot.kahoot_backend.enums.UploadType;
import com.kahoot.kahoot_backend.exception.EmptyFileException;
import com.kahoot.kahoot_backend.exception.FileStorageException;
import com.kahoot.kahoot_backend.exception.InvalidFileTypeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class UploadService {
    private static final Map<UploadType, List<String>> ALLOWED_EXTENSIONS = Map.of(
            UploadType.IMAGE, List.of("jpg", "jpeg", "png", "gif", "webp"),
            UploadType.AUDIO, List.of("mp3", "wav", "ogg", "m4a")
    );

    private final String uploadDir;
    private final String baseUrl;

    public UploadService(@Value("${app.upload.dir}") String uploadDir,
                         @Value("${app.upload.base-url}") String baseUrl) {
        this.uploadDir = uploadDir;
        this.baseUrl = baseUrl;
    }

    public FileUploadResponse store(MultipartFile file, String typeParam) {
        UploadType type = UploadType.fromParam(typeParam);

        if (file == null || file.isEmpty()) {
            throw new EmptyFileException("File is empty");
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (extension == null || !ALLOWED_EXTENSIONS.get(type).contains(extension.toLowerCase())) {
            throw new InvalidFileTypeException("Invalid file type");
        }

        String filename = UUID.randomUUID() + "." + extension.toLowerCase();
        Path targetDir = Path.of(uploadDir, type.folderName());
        Path targetPath = targetDir.resolve(filename);

        try {
            Files.createDirectories(targetDir);
            Files.copy(file.getInputStream(), targetPath);
        } catch (IOException e) {
            log.error("Failed to store uploaded file", e);
            throw new FileStorageException("Failed to store file");
        }

        String url = baseUrl + "/uploads/" + type.folderName() + "/" + filename;

        return FileUploadResponse.builder()
                .url(url)
                .build();
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return null;
        }

        return originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
    }
}
