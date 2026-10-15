package com.ticket241.notification_service.consumer;

import com.ticket241.notification_service.dto.request.OtpRequestDto;
import com.ticket241.notification_service.dto.response.OtpResponseDto;
import com.ticket241.notification_service.event.UserRegisteredEvent;
import com.ticket241.notification_service.service.EmailNotificationService;
import com.ticket241.notification_service.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredEventConsumer {

    private final EmailNotificationService emailNotificationService;
    private final OtpService otpService;

    @KafkaListener(
            topics = "user.registered",
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleUserRegistered(
            @Payload UserRegisteredEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        log.info("[Kafka] user.registered reçu — partition={}, offset={}, userId={}",
                partition, offset, event.getUserId());

        // Email de bienvenue : non-critique, un échec ne doit pas déclencher de retry
        // (qui renverrait aussi l'OTP en double)
        try {
            emailNotificationService.sendWelcomeEmail(event.getEmail(), event.getFirstName(), event.getUserId());
        } catch (Exception e) {
            log.warn("[Kafka] Échec envoi email de bienvenue pour userId={} : {}", event.getUserId(), e.getMessage());
        }

        // OTP de vérification : critique. Une exception ici remonte au conteneur, qui applique
        // le retry borné + recovery configurés dans KafkaConsumerConfig.kafkaErrorHandler().
        OtpRequestDto otpRequest = OtpRequestDto.builder()
                .userId(event.getUserId())
                .email(event.getEmail())
                .firstName(event.getFirstName())
                .build();

        // Génération + envoi email atomiques : si l'email échoue, l'exception remonte et le code
        // n'est pas conservé, ce qui déclenche le retry puis le DLT
        OtpResponseDto otpResponse = otpService.generateAndSendOtp(otpRequest);

        if (Boolean.TRUE.equals(otpResponse.getSuccess())) {
            log.info("[Kafka] OTP envoyé à {} pour userId={}", event.getEmail(), event.getUserId());
        } else {
            // Rejet métier (rate limit) : rejouer le message n'y changerait rien. auth-service limite
            // déjà les envois en amont, donc ce cas signale un appel anormal.
            log.error("[Kafka] OTP refusé pour userId={} : {}", event.getUserId(), otpResponse.getMessage());
        }

        acknowledgment.acknowledge();
    }
}