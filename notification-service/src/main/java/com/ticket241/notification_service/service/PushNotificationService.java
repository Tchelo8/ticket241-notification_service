package com.ticket241.notification_service.service;


import com.google.firebase.messaging.*;

import com.ticket241.notification_service.dto.request.PushNotificationRequestDto;
import com.ticket241.notification_service.entity.FcmToken;
import com.ticket241.notification_service.entity.NotificationHistory;
import com.ticket241.notification_service.enums.NotificationChannel;
import com.ticket241.notification_service.enums.NotificationStatus;
import com.ticket241.notification_service.repository.FcmTokenRepository;
import com.ticket241.notification_service.repository.NotificationHistoryRepository;
import com.google.firebase.messaging.MessagingErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    private final FcmTokenRepository fcmTokenRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;

    @Value("${notification.fcm.enabled:false}")
    private boolean fcmEnabled;

    // Envoyer une notification push à un utilisateur spécifique
    public void sendPushNotificationToUser(PushNotificationRequestDto request) {

        if (!fcmEnabled) {
            log.warn("FCM est désactivé. Notification non envoyée.");
            return;
        }

        log.info("Envoi de notification push pour userId: {}", request.getUserId());

        // Récupérer tous les tokens actifs de l'utilisateur
        List<FcmToken> userTokens = fcmTokenRepository.findByUserIdAndIsActiveTrue(request.getUserId());

        if (userTokens.isEmpty()) {
            log.warn("Aucun token FCM trouvé pour userId: {}", request.getUserId());
            return;
        }

        log.info("{} token(s) FCM trouvé(s) pour userId: {}", userTokens.size(), request.getUserId());

        // Envoyer à chaque token
        for (FcmToken fcmToken : userTokens) {
            try {
                sendToToken(fcmToken.getFcmToken(), request);

                // Mettre à jour la date de dernière utilisation
                fcmToken.setLastUsedAt(LocalDateTime.now());
                fcmTokenRepository.save(fcmToken);

            } catch (Exception e) {
                log.error("Erreur lors de l'envoi à token {}: {}",
                        fcmToken.getFcmToken().substring(0, 20), e.getMessage());

                // Si le token est invalide, le désactiver
                if (e.getMessage().contains("invalid") || e.getMessage().contains("not-registered")) {
                    log.warn("Token invalide, désactivation...");
                    fcmToken.setIsActive(false);
                    fcmTokenRepository.save(fcmToken);
                }
            }
        }
    }

    // Envoyer une notification à un token spécifique
    private void sendToToken(String fcmToken, PushNotificationRequestDto request) throws FirebaseMessagingException {

        log.debug("Envoi vers token: {}...", fcmToken.substring(0, 20));

        // Construire la notification
        Notification notification = Notification.builder()
                .setTitle(request.getTitle())
                .setBody(request.getBody())
                .setImage(request.getImageUrl())
                .build();

        // Construire le message
        Message.Builder messageBuilder = Message.builder()
                .setToken(fcmToken)
                .setNotification(notification);

        // Ajouter des données custom si présentes
        if (request.getData() != null && !request.getData().isEmpty()) {
            messageBuilder.putAllData(request.getData());
        }

        // Configuration Android
        AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder()
                        .setSound("default")
                        .setClickAction(request.getClickAction())
                        .build())
                .build();

        messageBuilder.setAndroidConfig(androidConfig);

        // Configuration iOS (APNS)
        ApnsConfig apnsConfig = ApnsConfig.builder()
                .setAps(Aps.builder()
                        .setSound("default")
                        .setBadge(request.getBadge() != null ? request.getBadge() : 1)
                        .build())
                .build();

        messageBuilder.setApnsConfig(apnsConfig);

        Message message = messageBuilder.build();

        // Envoyer via Firebase
        String response = FirebaseMessaging.getInstance().send(message);

        log.info("Notification envoyée avec succès. Message ID: {}", response);

        // Sauvegarder dans l'historique
        saveNotificationHistory(request, fcmToken, NotificationStatus.SENT, null, response);
    }

    // Envoyer une notification à plusieurs utilisateurs
    public void sendPushNotificationToMultipleUsers(List<Long> userIds, PushNotificationRequestDto request) {

        log.info("Envoi de notification push à {} utilisateurs", userIds.size());

        for (Long userId : userIds) {
            PushNotificationRequestDto userRequest = PushNotificationRequestDto.builder()
                    .userId(userId)
                    .title(request.getTitle())
                    .body(request.getBody())
                    .imageUrl(request.getImageUrl())
                    .clickAction(request.getClickAction())
                    .data(request.getData())
                    .badge(request.getBadge())
                    .sound(request.getSound())
                    .priority(request.getPriority())
                    .build();

            try {
                sendPushNotificationToUser(userRequest);
            } catch (Exception e) {
                log.error("Erreur lors de l'envoi à userId {}: {}", userId, e.getMessage());
                // Continuer avec les autres utilisateurs
            }
        }
    }

    // Envoyer une notification à tous les utilisateurs (broadcast)
    public void sendBroadcastNotification(PushNotificationRequestDto request) {

        log.info("Envoi d'une notification broadcast");

        // Récupérer tous les tokens actifs
        List<FcmToken> allTokens = fcmTokenRepository.findAll()
                .stream()
                .filter(FcmToken::getIsActive)
                .toList();

        log.info("{} tokens actifs trouvés", allTokens.size());

        // Firebase permet l'envoi groupé (jusqu'à 500 tokens à la fois)
        // On va envoyer par batch de 500
        int batchSize = 500;
        for (int i = 0; i < allTokens.size(); i += batchSize) {
            List<FcmToken> batch = allTokens.subList(i, Math.min(i + batchSize, allTokens.size()));
            sendMulticastMessage(batch, request);
        }
    }

    // Envoyer à plusieurs tokens en une seule requête (multicast)
    private void sendMulticastMessage(List<FcmToken> tokens, PushNotificationRequestDto request) {

        List<String> tokenStrings = tokens.stream()
                .map(FcmToken::getFcmToken)
                .toList();

        try {
            // Construire la notification
            Notification notification = Notification.builder()
                    .setTitle(request.getTitle())
                    .setBody(request.getBody())
                    .setImage(request.getImageUrl())
                    .build();

            // Construire le message multicast
            MulticastMessage message = MulticastMessage.builder()
                    .setNotification(notification)
                    .addAllTokens(tokenStrings)
                    .putAllData(request.getData() != null ? request.getData() : Map.of())
                    .build();

            // Envoyer
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);

            log.info("Multicast envoyé : {} succès, {} échecs",
                    response.getSuccessCount(), response.getFailureCount());

            // Traiter les échecs pour désactiver les tokens invalides
            if (response.getFailureCount() > 0) {
                List<SendResponse> responses = response.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    if (!responses.get(i).isSuccessful()) {
                        FcmToken failedToken = tokens.get(i);

                        // Récupérer l'exception
                        FirebaseMessagingException exception = responses.get(i).getException();

                        if (exception != null) {
                            MessagingErrorCode errorCode = exception.getMessagingErrorCode();

                            log.warn("Échec pour token {}... - ErrorCode: {}",
                                    failedToken.getFcmToken().substring(0, 20), errorCode);

                            // Désactiver si le token est invalide ou non enregistré
                            if (errorCode == MessagingErrorCode.INVALID_ARGUMENT ||
                                    errorCode == MessagingErrorCode.UNREGISTERED) {
                                log.warn("Token invalide, désactivation...");
                                failedToken.setIsActive(false);
                                fcmTokenRepository.save(failedToken);
                            }
                        }
                    }
                }
            }

        } catch (FirebaseMessagingException e) {
            log.error("Erreur lors de l'envoi multicast: {}", e.getMessage());
        }
    }

    // Sauvegarder l'historique de notification
    private void saveNotificationHistory(PushNotificationRequestDto request, String token,
                                         NotificationStatus status, String errorMessage,
                                         String externalId) {
        try {
            NotificationHistory history = NotificationHistory.builder()
                    .userId(request.getUserId())
                    .notificationType(null) // Sera défini par le contexte
                    .channel(NotificationChannel.PUSH)
                    .recipient(token.substring(0, 20) + "...") // Tronquer pour sécurité
                    .title(request.getTitle())
                    .body(request.getBody())
                    .status(status)
                    .errorMessage(errorMessage)
                    .externalId(externalId)
                    .sentAt(LocalDateTime.now())
                    .build();

            notificationHistoryRepository.save(history);

        } catch (Exception e) {
            log.error("Erreur lors de la sauvegarde de l'historique: {}", e.getMessage());
        }
    }
}
