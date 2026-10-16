package com.example.SecureVault.exception;

public class UserDeactivatedException extends RuntimeException {
    public UserDeactivatedException(String message) {
        super(message);
    }
}
