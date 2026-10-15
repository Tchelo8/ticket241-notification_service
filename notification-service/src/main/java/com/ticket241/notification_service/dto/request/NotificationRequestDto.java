package com.ticket241.notification_service.dto.request;


import com.ticket241.notification_service.enums.NotificationChannel;
import com.ticket241.notification_service.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

// DTO général pour envoyer une notification via n'importe quel canal
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequestDto {

    @NotNull(message = "L'ID utilisateur est obligatoire")
    private Long userId;

    @NotNull(message = "Le type de notification est obligatoire")
    private NotificationType notificationType;

    @NotNull(message = "Le canal de notification est obligatoire")
    private NotificationChannel channel;

    @NotBlank(message = "Le destinataire est obligatoire")
    private String recipient; // Email, numéro de téléphone ou FCM token

    private String title; // cette ligne est pour les envoies d' emails et SMS

    @NotBlank(message = "Le corps du message est obligatoire")
    private String body;

    // Données additionnelles (JSON) pour personnalisation
    private Map<String, Object> data;

    // Pour les notifications push : lien deep linking
    private String clickAction;

    // Pour les notifications push : image
    private String imageUrl;
}
