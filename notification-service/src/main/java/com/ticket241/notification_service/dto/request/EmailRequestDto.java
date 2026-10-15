package com.ticket241.notification_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

// DTO spécifique pour l'envoi d'emails
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailRequestDto {

    @NotBlank(message = "L'email du destinataire est obligatoire")
    @Email(message = "Format d'email invalide")
    private String to;

    @NotBlank(message = "Le sujet est obligatoire")
    private String subject;

    @NotBlank(message = "Le corps du message est obligatoire")
    private String body;

    // Indique si le body est du HTML ou du texte brut
    private Boolean isHtml = false;

    // Nom du template Thymeleaf à utiliser (optionnel)
    private String templateName;

    // Variables pour remplir le template Thymeleaf
    private Map<String, Object> templateVariables;

    // Liste des pièces jointes (chemins vers les fichiers)
    private String[] attachments;

    // CC et BCC (optionnel)
    private String[] cc;
    private String[] bcc;
}
