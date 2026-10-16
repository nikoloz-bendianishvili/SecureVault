package com.example.SecureVault.config;

import com.example.SecureVault.entity.User;
import com.example.SecureVault.enums.Roles;
import com.example.SecureVault.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the initial admin account on startup from the ADMIN_MAIL and ADMIN_PASSWORD configuration.
 */
@Component
@RequiredArgsConstructor
public class SystemDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Transactional
    @Override
    public void run(String... args) throws Exception {
        initAdminUser();
    }

    private void initAdminUser() {
        if (userRepository.existsByEmail(adminEmail)) return;
        User admin = new User("admin", adminEmail, passwordEncoder.encode(adminPassword));
        admin.setRole(Roles.ADMIN);
        admin.setEmailVerified(true);
        userRepository.save(admin);
    }
}
