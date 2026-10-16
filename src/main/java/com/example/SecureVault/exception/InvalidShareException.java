package com.example.SecureVault.exception;

public class InvalidShareException extends RuntimeException {
    public InvalidShareException(String message) {
        super(message);
    }
}
