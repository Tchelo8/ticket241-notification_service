package com.ticket241.notification_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// DTO de réponse après génération d'un OTP
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpResponseDto {

    private Boolean success;
    private String message;
    private LocalDateTime expiresAt;


    // Usage interne uniquement (envoi de l'email) : les appelants HTTP/Kafka doivent le mettre à null
    // avant toute réponse ou tout log — voir NotificationController.generateOtp
    private String otpCode;
}
