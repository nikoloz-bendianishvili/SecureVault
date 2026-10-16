package com.example.SecureVault.exception;

public class ShareNotFoundException extends RuntimeException {
    public ShareNotFoundException(String message) {
        super(message);
    }
}
