package com.example.SecureVault.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails over SMTP.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Sends the email-verification link containing the raw token.
     */
    public void sendVerificationEmail(String to, String token) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(from);
        mailMessage.setTo(to);
        mailMessage.setSubject("Verify your SecureVault account");
        mailMessage.setText("Please verify your email by clicking the following link: " + frontendUrl + "/verify?token=" + token + " expires in 24 hours");
        mailSender.send(mailMessage);
    }

    /**
     * Sends the password-reset link containing the raw token.
     */
    public void sendPasswordResetEmail(String to, String token) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(from);
        mailMessage.setTo(to);
        mailMessage.setSubject("Reset your SecureVault password");
        mailMessage.setText("Please reset your password by clicking the following link: " + frontendUrl + "/reset-password?token=" + token + " expires in 1 hour");
        mailSender.send(mailMessage);
    }
}
