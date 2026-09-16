package com.kahoot.kahoot_backend.exception;

public class InvalidUploadTypeException extends RuntimeException {
    public InvalidUploadTypeException(String message) {
        super(message);
    }
}
