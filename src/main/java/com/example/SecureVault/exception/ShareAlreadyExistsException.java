package com.example.SecureVault.exception;

public class ShareAlreadyExistsException extends RuntimeException {
    public ShareAlreadyExistsException(String message) {
        super(message);
    }
}
