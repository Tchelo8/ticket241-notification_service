package com.ticket241.notification_service.enums;

public enum NotificationChannel {
    PUSH,   // Firebase Cloud Messaging
    EMAIL,  // pour SMTP/Gmail
    SMS,    // pour les requêtes sms
    IN_APP  // Future: in-app notifications
}
