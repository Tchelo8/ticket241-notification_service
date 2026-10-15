package com.ticket241.notification_service.dto.request;


import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceRequestDto {

    @NotNull(message = "L'ID utilisateur est obligatoire")
    private Long userId;

    // Préférences Email
    private Boolean emailEnabled;
    private Boolean emailTicketUpdates;
    private Boolean emailEventReminders;
    private Boolean emailPromotions;
    private Boolean emailSystemUpdates;

    // Préférences Push
    private Boolean pushEnabled;
    private Boolean pushTicketUpdates;
    private Boolean pushEventReminders;
    private Boolean pushPromotions;

    // Préférences SMS
    private Boolean smsEnabled;
    private Boolean smsTicketUpdates;
    private Boolean smsEventReminders;
}
