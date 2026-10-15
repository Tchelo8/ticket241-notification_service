package com.ticket241.notification_service.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsRequestDto {

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "Format de numéro invalide entrez lz au format international")
    private String phoneNumber; // Format international : +241XXXXXXXXX

    @NotBlank(message = "Le message de la notification est obligatoire")
    private String message;

    private Long userId;
}
