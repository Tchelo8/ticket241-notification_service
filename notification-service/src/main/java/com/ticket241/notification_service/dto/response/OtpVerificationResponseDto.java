package com.ticket241.notification_service.dto.response;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpVerificationResponseDto {

    private Boolean valid;
    private String message;
    private Integer remainingAttempts;
}
