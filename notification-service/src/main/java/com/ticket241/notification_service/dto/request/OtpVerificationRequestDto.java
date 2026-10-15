package com.ticket241.notification_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO pour vérifier un OTP
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerificationRequestDto {

    @NotNull(message = "L'ID utilisateur est obligatoire")
    private Long userId;

    @NotBlank(message = "Le code OTP est obligatoire")
    @Size(min = 6, max = 6, message = "Le code OTP doit contenir 6 chiffres")
    private String code;
}
