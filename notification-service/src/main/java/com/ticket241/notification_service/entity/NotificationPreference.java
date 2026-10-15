package com.ticket241.notification_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_preferences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    // Email Preferences
    @Column(name = "email_enabled")
    private Boolean emailEnabled = true;

    @Column(name = "email_ticket_updates")
    private Boolean emailTicketUpdates = true;

    @Column(name = "email_event_reminders")
    private Boolean emailEventReminders = true;

    @Column(name = "email_promotions")
    private Boolean emailPromotions = false;

    @Column(name = "email_system_updates")
    private Boolean emailSystemUpdates = true;

    // Push Preferences
    @Column(name = "push_enabled")
    private Boolean pushEnabled = true;

    @Column(name = "push_ticket_updates")
    private Boolean pushTicketUpdates = true;

    @Column(name = "push_event_reminders")
    private Boolean pushEventReminders = true;

    @Column(name = "push_promotions")
    private Boolean pushPromotions = false;

    // SMS Preferences
    @Column(name = "sms_enabled")
    private Boolean smsEnabled = false;

    @Column(name = "sms_ticket_updates")
    private Boolean smsTicketUpdates = false;

    @Column(name = "sms_event_reminders")
    private Boolean smsEventReminders = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
