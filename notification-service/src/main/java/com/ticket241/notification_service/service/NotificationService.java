package com.ticket241.notification_service.service;


import com.ticket241.notification_service.dto.request.EmailRequestDto;
import com.ticket241.notification_service.dto.request.NotificationRequestDto;
import com.ticket241.notification_service.dto.request.PushNotificationRequestDto;
import com.ticket241.notification_service.dto.request.SmsRequestDto;
import com.ticket241.notification_service.entity.NotificationPreference;
import com.ticket241.notification_service.enums.NotificationType;
import com.ticket241.notification_service.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final EmailNotificationService emailNotificationService;
    private final PushNotificationService pushNotificationService;
    private final SmsNotificationService smsNotificationService;
    private final NotificationPreferenceRepository notificationPreferenceRepository;

    // Envoyer une notification via tous les canaux activés pour un utilisateur
    public void sendNotification(NotificationRequestDto request) {

        log.info("Envoi de notification {} pour userId: {}",
                request.getNotificationType(), request.getUserId());

        // Récupérer les préférences de l'utilisateur
        Optional<NotificationPreference> preferencesOpt =
                notificationPreferenceRepository.findByUserId(request.getUserId());

        NotificationPreference preferences = preferencesOpt.orElseGet(() -> createDefaultPreferences(request.getUserId()));

        // Envoyer selon le canal demandé et les préférences
        switch (request.getChannel()) {
            case EMAIL -> {
                if (shouldSendEmail(preferences, request.getNotificationType())) {
                    sendEmailNotification(request);
                } else {
                    log.info("Email désactivé pour ce type de notification");
                }
            }
            case PUSH -> {
                if (shouldSendPush(preferences, request.getNotificationType())) {
                    sendPushNotification(request);
                } else {
                    log.info("Push désactivé pour ce type de notification");
                }
            }
            case SMS -> {
                if (shouldSendSms(preferences, request.getNotificationType())) {
                    sendSmsNotification(request);
                } else {
                    log.info("SMS désactivé pour ce type de notification");
                }
            }
        }
    }

    // Envoyer une notification via tous les canaux disponibles
    public void sendMultiChannelNotification(Long userId, NotificationType notificationType,
                                             String title, String body, String recipient) {

        log.info("Envoi multi-canal pour userId: {}, type: {}", userId, notificationType);

        // Récupérer les préférences
        Optional<NotificationPreference> preferencesOpt =
                notificationPreferenceRepository.findByUserId(userId);

        NotificationPreference preferences = preferencesOpt.orElseGet(() -> createDefaultPreferences(userId));

        List<String> sentChannels = new ArrayList<>();

        // Email
        if (shouldSendEmail(preferences, notificationType)) {
            try {
                EmailRequestDto emailRequest = EmailRequestDto.builder()
                        .to(recipient)
                        .subject(title)
                        .body(body)
                        .isHtml(false)
                        .build();
                emailNotificationService.sendSimpleEmail(emailRequest);
                sentChannels.add("EMAIL");
            } catch (Exception e) {
                log.error("Échec email: {}", e.getMessage());
            }
        }

        // Push
        if (shouldSendPush(preferences, notificationType)) {
            try {
                PushNotificationRequestDto pushRequest = PushNotificationRequestDto.builder()
                        .userId(userId)
                        .title(title)
                        .body(body)
                        .build();
                pushNotificationService.sendPushNotificationToUser(pushRequest);
                sentChannels.add("PUSH");
            } catch (Exception e) {
                log.error("Échec push: {}", e.getMessage());
            }
        }

        // SMS
        if (shouldSendSms(preferences, notificationType)) {
            try {
                SmsRequestDto smsRequest = SmsRequestDto.builder()
                        .phoneNumber(recipient)
                        .message(body)
                        .userId(userId)
                        .build();
                smsNotificationService.sendSms(smsRequest);
                sentChannels.add("SMS");
            } catch (Exception e) {
                log.error("Échec SMS: {}", e.getMessage());
            }
        }

        log.info("Notification envoyée via: {}", String.join(", ", sentChannels));
    }

    // === MÉTHODES SPÉCIFIQUES PAR TYPE DE NOTIFICATION ===

    // Notification après inscription
    public void sendRegistrationNotification(Long userId, String email, String firstName) {

        log.info("Notification d'inscription pour userId: {}", userId);

        // Envoyer l'email de bienvenue
        emailNotificationService.sendWelcomeEmail(email, firstName, userId);
    }

    // La confirmation de billets (email + PDF) est déclenchée par le topic ticket-confirmed :
    // voir TicketConfirmedEventConsumer

    // Notification de rappel d'événement
    public void sendEventReminderNotification(Long userId, String email, String firstName,
                                              String eventName, String eventDate,
                                              String eventLocation) {

        log.info("Rappel d'événement pour userId: {}", userId);

        // Email
        emailNotificationService.sendEventReminderEmail(
                email, firstName, eventName, eventDate, eventLocation, userId
        );

        // Push
        PushNotificationRequestDto pushRequest = PushNotificationRequestDto.builder()
                .userId(userId)
                .title("Rappel : " + eventName)
                .body("L'événement commence dans 24h à " + eventLocation)
                .clickAction("EVENT_DETAILS")
                .build();

        pushNotificationService.sendPushNotificationToUser(pushRequest);
    }

    // Notification de paiement confirmé
    public void sendPaymentConfirmationNotification(Long userId, String email,
                                                    String amount, String eventName) {

        log.info("Notification de paiement confirmé pour userId: {}", userId);

        String subject = "Paiement confirmé - " + eventName;
        String body = String.format(
                "Votre paiement de %s FCFA pour %s a été confirmé avec succès.",
                amount, eventName
        );

        sendMultiChannelNotification(userId, NotificationType.PAYMENT_RECEIVED,
                subject, body, email);
    }

    // === MÉTHODES PRIVÉES ===

    private void sendEmailNotification(NotificationRequestDto request) {
        EmailRequestDto emailRequest = EmailRequestDto.builder()
                .to(request.getRecipient())
                .subject(request.getTitle())
                .body(request.getBody())
                .isHtml(false)
                .build();

        emailNotificationService.sendSimpleEmail(emailRequest);
    }

    private void sendPushNotification(NotificationRequestDto request) {
        PushNotificationRequestDto pushRequest = PushNotificationRequestDto.builder()
                .userId(request.getUserId())
                .title(request.getTitle())
                .body(request.getBody())
                .imageUrl(request.getImageUrl())
                .clickAction(request.getClickAction())
                .data(request.getData() != null ?
                        request.getData().entrySet().stream()
                                .collect(java.util.stream.Collectors.toMap(
                                        java.util.Map.Entry::getKey,
                                        e -> String.valueOf(e.getValue())
                                )) : null)
                .build();

        pushNotificationService.sendPushNotificationToUser(pushRequest);
    }

    private void sendSmsNotification(NotificationRequestDto request) {
        SmsRequestDto smsRequest = SmsRequestDto.builder()
                .phoneNumber(request.getRecipient())
                .message(request.getBody())
                .userId(request.getUserId())
                .build();

        smsNotificationService.sendSms(smsRequest);
    }

    // Vérifier si on doit envoyer un email selon les préférences
    private boolean shouldSendEmail(NotificationPreference preferences, NotificationType type) {
        if (!preferences.getEmailEnabled()) return false;

        return switch (type) {
            case TICKET_PURCHASED, TICKET_CONFIRMED, TICKET_CANCELLED -> preferences.getEmailTicketUpdates();
            case EVENT_REMINDER_24H, EVENT_REMINDER_1H -> preferences.getEmailEventReminders();
            case PROMOTIONAL_OFFER, NEW_EVENT_ALERT -> preferences.getEmailPromotions();
            case SYSTEM_MAINTENANCE, SYSTEM_UPDATE -> preferences.getEmailSystemUpdates();
            default -> true;
        };
    }

    // Vérifier si on doit envoyer un push selon les préférences
    private boolean shouldSendPush(NotificationPreference preferences, NotificationType type) {
        if (!preferences.getPushEnabled()) return false;

        return switch (type) {
            case TICKET_PURCHASED, TICKET_CONFIRMED, TICKET_CANCELLED -> preferences.getPushTicketUpdates();
            case EVENT_REMINDER_24H, EVENT_REMINDER_1H -> preferences.getPushEventReminders();
            case PROMOTIONAL_OFFER, NEW_EVENT_ALERT -> preferences.getPushPromotions();
            default -> true;
        };
    }

    // Vérifier si on doit envoyer un SMS selon les préférences
    private boolean shouldSendSms(NotificationPreference preferences, NotificationType type) {
        if (!preferences.getSmsEnabled()) return false;

        return switch (type) {
            case TICKET_PURCHASED, TICKET_CONFIRMED -> preferences.getSmsTicketUpdates();
            case EVENT_REMINDER_24H, EVENT_REMINDER_1H -> preferences.getSmsEventReminders();
            default -> false; // Par défaut, SMS désactivé
        };
    }

    // Créer des préférences par défaut pour un nouvel utilisateur
    private NotificationPreference createDefaultPreferences(Long userId) {
        log.info("Création des préférences par défaut pour userId: {}", userId);

        NotificationPreference preferences = NotificationPreference.builder()
                .userId(userId)
                // Email activé par défaut
                .emailEnabled(true)
                .emailTicketUpdates(true)
                .emailEventReminders(true)
                .emailPromotions(false)
                .emailSystemUpdates(true)
                // Push activé par défaut
                .pushEnabled(true)
                .pushTicketUpdates(true)
                .pushEventReminders(true)
                .pushPromotions(false)
                // SMS désactivé par défaut
                .smsEnabled(false)
                .smsTicketUpdates(false)
                .smsEventReminders(false)
                .build();

        return notificationPreferenceRepository.save(preferences);
    }
}
