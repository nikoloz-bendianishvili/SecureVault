package com.example.SecureVault.entity;

import com.example.SecureVault.enums.Roles;
import com.example.SecureVault.enums.Status;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false)
    private String fullName;

    @Setter
    @Column(nullable = false, unique = true)
    private String email;

    @Setter
    @Column(nullable = false)
    private String passwordHash;

    @Setter
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Roles role;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Setter
    @Column(nullable = false)
    private boolean emailVerified;

    @Version
    private Long version;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @PreUpdate
    void onUpdate(){this.updatedAt = Instant.now();}

    public User(String fullName, String email, String passwordHash) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = Roles.USER;
        this.emailVerified = false;
        this.status = Status.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
