package com.kahoot.kahoot_backend.service;

import com.kahoot.kahoot_backend.DTOs.upload.FileUploadResponse;
import com.kahoot.kahoot_backend.exception.EmptyFileException;
import com.kahoot.kahoot_backend.exception.InvalidFileTypeException;
import com.kahoot.kahoot_backend.exception.InvalidUploadTypeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadServiceTest {
    private static final String BASE_URL = "http://localhost:8080";

    @TempDir
    private Path tempDir;

    private UploadService uploadService;

    @BeforeEach
    void setUp() {
        uploadService = new UploadService(tempDir.toString(), BASE_URL);
    }

    @Test
    void store_validImage_shouldReturnUrlAndSaveFileOnDisk() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "image-bytes".getBytes());

        FileUploadResponse response = uploadService.store(file, "image");

        assertThat(response.getUrl()).startsWith(BASE_URL + "/uploads/image/");
        assertThat(response.getUrl()).endsWith(".jpg");
        assertThat(Files.exists(pathFromUrl(response.getUrl(), "image"))).isTrue();
    }

    @Test
    void store_validAudio_shouldReturnUrlAndSaveFileOnDisk() {
        MockMultipartFile file = new MockMultipartFile("file", "sound.mp3", "audio/mpeg", "audio-bytes".getBytes());

        FileUploadResponse response = uploadService.store(file, "audio");

        assertThat(response.getUrl()).startsWith(BASE_URL + "/uploads/audio/");
        assertThat(response.getUrl()).endsWith(".mp3");
        assertThat(Files.exists(pathFromUrl(response.getUrl(), "audio"))).isTrue();
    }

    @Test
    void store_typeParamUppercase_shouldStillSucceed() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "image-bytes".getBytes());

        FileUploadResponse response = uploadService.store(file, "IMAGE");

        assertThat(response.getUrl()).startsWith(BASE_URL + "/uploads/image/");
    }

    @Test
    void store_extensionUppercase_shouldStillSucceed() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.JPG", "image/jpeg", "image-bytes".getBytes());

        FileUploadResponse response = uploadService.store(file, "image");

        assertThat(response.getUrl()).endsWith(".jpg");
    }

    @Test
    void store_emptyFile_shouldThrowEmptyFileException() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[0]);

        assertThrows(EmptyFileException.class, () -> uploadService.store(file, "image"));
    }

    @Test
    void store_wrongExtensionForImage_shouldThrowInvalidFileTypeException() {
        MockMultipartFile file = new MockMultipartFile("file", "document.pdf", "application/pdf", "bytes".getBytes());

        assertThrows(InvalidFileTypeException.class, () -> uploadService.store(file, "image"));
    }

    @Test
    void store_wrongExtensionForAudio_shouldThrowInvalidFileTypeException() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "bytes".getBytes());

        assertThrows(InvalidFileTypeException.class, () -> uploadService.store(file, "audio"));
    }

    @Test
    void store_noExtension_shouldThrowInvalidFileTypeException() {
        MockMultipartFile file = new MockMultipartFile("file", "noextension", "image/jpeg", "bytes".getBytes());

        assertThrows(InvalidFileTypeException.class, () -> uploadService.store(file, "image"));
    }

    @Test
    void store_invalidTypeParam_shouldThrowInvalidUploadTypeException() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "bytes".getBytes());

        assertThrows(InvalidUploadTypeException.class, () -> uploadService.store(file, "video"));
    }

    private Path pathFromUrl(String url, String folder) {
        String filename = url.substring(url.lastIndexOf('/') + 1);

        return tempDir.resolve(folder).resolve(filename);
    }
}
