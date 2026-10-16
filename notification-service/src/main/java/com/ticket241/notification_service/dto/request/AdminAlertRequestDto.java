package com.ticket241.notification_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Alerte envoyée par un autre service aux administrateurs Ticket241
// (ex: auth-service quand une structure perd son dernier administrateur actif)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAlertRequestDto {

    @NotEmpty(message = "Au moins un destinataire est requis")
    private List<@NotBlank @Email(message = "Format d'email invalide") String> recipients;

    @NotBlank(message = "Le sujet est obligatoire")
    @Size(max = 200, message = "Le sujet ne peut pas dépasser 200 caractères")
    private String subject;

    @NotBlank(message = "Le message est obligatoire")
    @Size(max = 5000, message = "Le message ne peut pas dépasser 5000 caractères")
    private String message;
}
