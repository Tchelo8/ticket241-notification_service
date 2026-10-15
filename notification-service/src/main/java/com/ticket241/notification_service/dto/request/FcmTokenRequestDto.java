package com.ticket241.notification_service.dto.request;

import com.ticket241.notification_service.enums.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO pour enregistrer un token FCM depuis l'app Flutter
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FcmTokenRequestDto {

    @NotNull(message = "L'ID utilisateur est obligatoire")
    private Long userId;

    @NotBlank(message = "Le token FCM est obligatoire")
    private String fcmToken;

    @NotNull(message = "Le type d'appareil est obligatoire")
    private DeviceType deviceType;

    // Identifiant unique de l'appareil (optionnel)
    private String deviceId;
}
