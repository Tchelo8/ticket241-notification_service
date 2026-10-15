package com.ticket241.notification_service.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

// DTO spécifique pour les notifications push (FCM)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushNotificationRequestDto {

    @NotNull(message = "L'ID utilisateur est obligatoire")
    private Long userId;

    @NotBlank(message = "Le titre est obligatoire")
    private String title;

    @NotBlank(message = "Le corps est obligatoire")
    private String body;

    // URL de l'image pour notification riche
    private String imageUrl;

    // Action au clic (deep link dans l'app)
    private String clickAction;

    // Données custom à envoyer avec la notification
    private Map<String, String> data;

    // Badge count pour iOS
    private Integer badge;

    // Son personnalisé
    private String sound;

    // Priorité (high/normal)
    private String priority = "high";
}