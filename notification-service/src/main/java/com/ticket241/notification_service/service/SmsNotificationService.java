package com.ticket241.notification_service.service;


import com.ticket241.notification_service.dto.request.SmsRequestDto;
import com.ticket241.notification_service.entity.NotificationHistory;
import com.ticket241.notification_service.enums.NotificationChannel;
import com.ticket241.notification_service.enums.NotificationStatus;
import com.ticket241.notification_service.enums.NotificationType;
import com.ticket241.notification_service.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsNotificationService {

    private final NotificationHistoryRepository notificationHistoryRepository;

    @Value("${notification.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${notification.sms.provider:twilio}")
    private String smsProvider;

    // Envoyer un SMS
    public void sendSms(SmsRequestDto request) {

        if (!smsEnabled) {
            log.warn("SMS désactivé dans la configuration");
            return;
        }

        log.info("Envoi de SMS à: {}", request.getPhoneNumber());

        try {
            // Vérifier le format du numéro le format international est requis
            if (!request.getPhoneNumber().startsWith("+")) {
                throw new IllegalArgumentException("Le numéro doit être au format international (+241XXXXXXXXX)");
            }

            // Router vers le bon provider
            switch (smsProvider.toLowerCase()) {
                case "twilio" -> sendViaTwilio(request);
                case "africastalking" -> sendViaAfricasTalking(request);
                case "nexmo" -> sendViaNexmo(request);
                default -> throw new IllegalArgumentException("Provider SMS non supporté: " + smsProvider);
            }

            log.info("SMS envoyé avec succès à: {}", request.getPhoneNumber());

            // Sauvegarder dans l'historique
            saveNotificationHistory(request, NotificationStatus.SENT, null);

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi du SMS à {}: {}",
                    request.getPhoneNumber(), e.getMessage());

            // Sauvegarder l'échec
            saveNotificationHistory(request, NotificationStatus.FAILED, e.getMessage());

            throw new RuntimeException("Échec de l'envoi du SMS", e);
        }
    }

    // Envoyer un OTP par SMS
    public void sendOtpSms(String phoneNumber, String otpCode, Long userId) {

        log.info("Envoi de l'OTP par SMS à: {}", phoneNumber);

        String message = String.format(
                "Votre code de vérification Ticket241 est : %s\n" +
                        "Ce code expire dans 5 minutes.\n" +
                        "Ne partagez jamais ce code.",
                otpCode
        );

        SmsRequestDto request = SmsRequestDto.builder()
                .phoneNumber(phoneNumber)
                .message(message)
                .userId(userId)
                .build();

        sendSms(request);
    }

    // === IMPLÉMENTATIONS SPÉCIFIQUES PAR PROVIDER ===

    // Twilio (Provider populaire, international)
    private void sendViaTwilio(SmsRequestDto request) {
        log.info("Envoi via Twilio...");

        // TODO: Implémenter avec Twilio SDK
        // Dépendance Maven: com.twilio.sdk:twilio
        // https://www.twilio.com/docs/sms/quickstart/java

        /*
        Exemple d'implémentation:

        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);

        Message message = Message.creator(
                new PhoneNumber(request.getPhoneNumber()),  // To
                new PhoneNumber(TWILIO_PHONE_NUMBER),       // From
                request.getMessage()
        ).create();

        log.info("Twilio Message SID: {}", message.getSid());
        */

        throw new UnsupportedOperationException("Twilio non encore implémenté. Ajoutez les credentials dans application.yml");
    }

    // Africa's Talking (Provider africain)
    private void sendViaAfricasTalking(SmsRequestDto request) {
        log.info("Envoi via Africa's Talking...");

        // TODO: Implémenter avec Africa's Talking SDK
        // Dépendance Maven: com.africastalking:core
        // https://africastalking.com/

        throw new UnsupportedOperationException("Africa's Talking non encore implémenté");
    }

    // Vonage (anciennement Nexmo)
    private void sendViaNexmo(SmsRequestDto request) {
        log.info("Envoi via Vonage/Nexmo...");

        // TODO: Implémenter avec Vonage SDK
        // https://developer.vonage.com/messaging/sms/overview

        throw new UnsupportedOperationException("Nexmo/Vonage non encore implémenté");
    }

    // Sauvegarder l'historique de notification
    private void saveNotificationHistory(SmsRequestDto request, NotificationStatus status,
                                         String errorMessage) {

        if (request.getUserId() == null) {
            return; // Ne pas sauvegarder si pas d'userId
        }

        try {
            NotificationHistory history = NotificationHistory.builder()
                    .userId(request.getUserId())
                    .notificationType(NotificationType.OTP_VERIFICATION) // Peut être dynamique
                    .channel(NotificationChannel.SMS)
                    .recipient(request.getPhoneNumber())
                    .title(null) // Les SMS n'ont pas de titre
                    .body(request.getMessage().length() > 500 ?
                            request.getMessage().substring(0, 500) : request.getMessage())
                    .status(status)
                    .errorMessage(errorMessage)
                    .sentAt(LocalDateTime.now())
                    .build();

            notificationHistoryRepository.save(history);
            log.debug("Historique SMS sauvegardé");

        } catch (Exception e) {
            log.error("Erreur lors de la sauvegarde de l'historique SMS: {}", e.getMessage());
        }
    }

    // Valider un numéro de téléphone international
    public boolean isValidInternationalPhoneNumber(String phoneNumber) {
        // Format international: +[code pays][numéro]
        // Exemple: +241XXXXXXXXX (Gabon)
        return phoneNumber != null &&
                phoneNumber.matches("^\\+[1-9]\\d{1,14}$");
    }
}
