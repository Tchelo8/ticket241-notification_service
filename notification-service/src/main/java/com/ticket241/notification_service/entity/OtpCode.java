package com.ticket241.notification_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "otp_codes", indexes = {
        @Index(name = "idx_otp_user_id", columnList = "user_id"),
        @Index(name = "idx_code", columnList = "code")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "email", nullable = false)
    private String email;

    // Stocke le hash SHA-256 (hex, 64 caractères) du code, jamais le code en clair
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "is_used")
    private Boolean isUsed = false;

    @Column(name = "is_expired")
    private Boolean isExpired = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "attempt_count")
    private Integer attemptCount = 0;
}
