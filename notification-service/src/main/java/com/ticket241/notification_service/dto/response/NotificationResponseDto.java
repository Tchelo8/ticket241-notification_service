package com.ticket241.notification_service.dto.response;




import com.ticket241.notification_service.enums.NotificationChannel;
import com.ticket241.notification_service.enums.NotificationStatus;
import com.ticket241.notification_service.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponseDto {

    private Long notificationId;
    private Long userId;
    private NotificationType notificationType;
    private NotificationChannel channel;
    private NotificationStatus status;
    private String message;
    private LocalDateTime sentAt;
    private String externalId; // ID retourné par le service externe soit par le service d'sms de mails ou fcm
}
