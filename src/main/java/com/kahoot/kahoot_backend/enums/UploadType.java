package com.kahoot.kahoot_backend.enums;

import com.kahoot.kahoot_backend.exception.InvalidUploadTypeException;

public enum UploadType {
    IMAGE,
    AUDIO;

    public static UploadType fromParam(String param) {
        if (param == null) {
            throw new InvalidUploadTypeException("Upload type must be image or audio");
        }

        return switch (param.toLowerCase()) {
            case "image" -> IMAGE;
            case "audio" -> AUDIO;
            default -> throw new InvalidUploadTypeException("Upload type must be image or audio");
        };
    }

    public String folderName() {
        return name().toLowerCase();
    }
}
