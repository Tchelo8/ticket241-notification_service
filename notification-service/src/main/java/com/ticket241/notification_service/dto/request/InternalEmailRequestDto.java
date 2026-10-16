package com.ticket241.notification_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Email texte envoyé pour le compte d'un autre service (ex: code d'accès invité de ticket-service).
// Réservé aux appels internes : clé interne exigée (voir InternalSecurityConfig).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalEmailRequestDto {

    @NotBlank(message = "L'email du destinataire est obligatoire")
    @Email(message = "Format d'email invalide")
    private String to;

    @NotBlank(message = "Le sujet est obligatoire")
    @Size(max = 200, message = "Le sujet ne peut pas dépasser 200 caractères")
    private String subject;

    @NotBlank(message = "Le message est obligatoire")
    @Size(max = 5000, message = "Le message ne peut pas dépasser 5000 caractères")
    private String body;
}
