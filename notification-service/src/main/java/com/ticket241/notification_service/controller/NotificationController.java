package com.ticket241.notification_service.controller;

//import com.ticket241.notification_service.dto.*;
import com.ticket241.notification_service.dto.request.*;
import com.ticket241.notification_service.dto.response.OtpResponseDto;
import com.ticket241.notification_service.dto.response.OtpVerificationResponseDto;
import com.ticket241.notification_service.entity.FcmToken;
import com.ticket241.notification_service.entity.NotificationHistory;
import com.ticket241.notification_service.entity.NotificationPreference;
import com.ticket241.notification_service.repository.FcmTokenRepository;
import com.ticket241.notification_service.repository.NotificationHistoryRepository;
import com.ticket241.notification_service.repository.NotificationPreferenceRepository;
import com.ticket241.notification_service.security.CallerContext;
import com.ticket241.notification_service.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    // Identité de l'appelant, injectée par la gateway depuis le JWT (route protégée par JwtAuthenticationFilter)
    private static final String ACCOUNT_ID_HEADER = "X-User-Account-Id";
    private static final String ROLES_HEADER = "X-User-Roles";

    private final NotificationService notificationService;
    private final EmailNotificationService emailNotificationService;
    private final PushNotificationService pushNotificationService;
    private final SmsNotificationService smsNotificationService;
    private final OtpService otpService;
    private final FcmTokenRepository fcmTokenRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;


    @PostMapping("/otp/generate")
    public ResponseEntity<OtpResponseDto> generateOtp(@Valid @RequestBody OtpRequestDto request) {
        log.info("POST /api/notifications/otp/generate - userId: {}", request.getUserId());

        // L'OTP ne transite que par email : generateAndSendOtp ne renvoie jamais le code en clair
        OtpResponseDto response = otpService.generateAndSendOtp(request);

        if (response.getSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }
    }

    // Vérifier un OTP
    @PostMapping("/otp/verify")
    public ResponseEntity<OtpVerificationResponseDto> verifyOtp(@Valid @RequestBody OtpVerificationRequestDto request) {
        log.info("POST /api/notifications/otp/verify - userId: {}", request.getUserId());

        OtpVerificationResponseDto response = otpService.verifyOtp(request);

        if (response.getValid()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }




    // Enregistrer un token FCM depuis l'app Flutter
    @PostMapping("/fcm/register")
    public ResponseEntity<Map<String, Object>> registerFcmToken(
            @Valid @RequestBody FcmTokenRequestDto request,
            @RequestHeader(value = ACCOUNT_ID_HEADER, required = false) String accountId,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        CallerContext.of(accountId, roles).requireAccessTo(request.getUserId());

        log.info("POST /api/notifications/fcm/register - userId: {}, deviceType: {}",
                request.getUserId(), request.getDeviceType());

        Map<String, Object> response = new HashMap<>();

        try {
            // Vérifier si le token existe déjà
            Optional<FcmToken> existingToken = fcmTokenRepository.findByFcmToken(request.getFcmToken());

            if (existingToken.isPresent()) {
                // Mettre à jour le token existant
                FcmToken token = existingToken.get();
                token.setUserId(request.getUserId());
                token.setDeviceType(request.getDeviceType());
                token.setDeviceId(request.getDeviceId());
                token.setIsActive(true);
                token.setLastUsedAt(LocalDateTime.now());

                fcmTokenRepository.save(token);

                log.info("Token FCM mis à jour");
                response.put("success", true);
                response.put("message", "Token FCM mis à jour avec succès");
                response.put("tokenId", token.getId());

            } else {
                // Créer un nouveau token
                FcmToken newToken = FcmToken.builder()
                        .userId(request.getUserId())
                        .fcmToken(request.getFcmToken())
                        .deviceType(request.getDeviceType())
                        .deviceId(request.getDeviceId())
                        .isActive(true)
                        .lastUsedAt(LocalDateTime.now())
                        .build();

                fcmTokenRepository.save(newToken);

                log.info("Nouveau token FCM enregistré");
                response.put("success", true);
                response.put("message", "Token FCM enregistré avec succès");
                response.put("tokenId", newToken.getId());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement du token FCM : {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Désactiver un token FCM logout de l'app
    @DeleteMapping("/fcm/unregister")
    public ResponseEntity<Map<String, Object>> unregisterFcmToken(
            @RequestParam String fcmToken,
            @RequestHeader(value = ACCOUNT_ID_HEADER, required = false) String accountId,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        log.info("DELETE /api/notifications/fcm/unregister");

        // On ne désactive que ses propres appareils
        CallerContext caller = CallerContext.of(accountId, roles);
        fcmTokenRepository.findByFcmToken(fcmToken)
                .ifPresent(token -> caller.requireAccessTo(token.getUserId()));

        Map<String, Object> response = new HashMap<>();

        try {
            fcmTokenRepository.deactivateToken(fcmToken);

            log.info("Token FCM désactivé");
            response.put("success", true);
            response.put("message", "Token désactivé avec succès");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur : {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Récupérer tous les tokens d'un utilisateur
    @GetMapping("/fcm/tokens/{userId}")
    public ResponseEntity<List<FcmToken>> getUserFcmTokens(
            @PathVariable Long userId,
            @RequestHeader(value = ACCOUNT_ID_HEADER, required = false) String accountId,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        log.info("GET /api/notifications/fcm/tokens/{}", userId);
        CallerContext.of(accountId, roles).requireAccessTo(userId);

        List<FcmToken> tokens = fcmTokenRepository.findByUserIdAndIsActiveTrue(userId);

        log.info("{} token(s) trouvé(s)", tokens.size());
        return ResponseEntity.ok(tokens);
    }


    // Envois libres (test, email, push, SMS) : admin uniquement, sinon n'importe qui enverrait des
    // messages au nom de Ticket241
    @PostMapping("/test/send-email")
    public ResponseEntity<String> sendTestEmail(
            @RequestParam String email,
            @RequestHeader(value = ACCOUNT_ID_HEADER, required = false) String accountId,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        CallerContext.of(accountId, roles).requireAdmin();

        try {
            EmailRequestDto request = EmailRequestDto.builder()
                    .to(email)
                    .subject("Test Email - Ticket241")
                    .body("Si vous recevez cet email, la configuration Gmail fonctionne parfaitement !")
                    .isHtml(false)
                    .build();

            emailNotificationService.sendSimpleEmail(request);

            return ResponseEntity.ok("Email envoyé avec succès à : " + email);

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Erreur : " + e.getMessage());
        }
    }



    // Envoyer un email simple
    @PostMapping("/email/send")
    public ResponseEntity<Map<String, Object>> sendEmail(
            @Valid @RequestBody EmailRequestDto request,
            @RequestHeader(value = ACCOUNT_ID_HEADER, required = false) String accountId,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        CallerContext.of(accountId, roles).requireAdmin();
        log.info("POST /api/notifications/email/send - to: {}", request.getTo());

        Map<String, Object> response = new HashMap<>();

        try {
            if (request.getIsHtml()) {
                emailNotificationService.sendHtmlEmail(request);
            } else {
                emailNotificationService.sendSimpleEmail(request);
            }

            log.info("Email envoyé avec succès");
            response.put("success", true);
            response.put("message", "Email envoyé avec succès");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur : {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Envoyer une notification push
    @PostMapping("/push/send")
    public ResponseEntity<Map<String, Object>> sendPushNotification(
            @Valid @RequestBody PushNotificationRequestDto request,
            @RequestHeader(value = ACCOUNT_ID_HEADER, required = false) String accountId,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        CallerContext.of(accountId, roles).requireAdmin();
        log.info("POST /api/notifications/push/send - userId: {}", request.getUserId());

        Map<String, Object> response = new HashMap<>();

        try {
            pushNotificationService.sendPushNotificationToUser(request);

            log.info("Notification push envoyée");
            response.put("success", true);
            response.put("message", "Notification push envoyée avec succès");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur : {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Envoyer un SMS
    @PostMapping("/sms/send")
    public ResponseEntity<Map<String, Object>> sendSms(
            @Valid @RequestBody SmsRequestDto request,
            @RequestHeader(value = ACCOUNT_ID_HEADER, required = false) String accountId,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        CallerContext.of(accountId, roles).requireAdmin();
        log.info("POST /api/notifications/sms/send - phoneNumber: {}", request.getPhoneNumber());

        Map<String, Object> response = new HashMap<>();

        try {
            smsNotificationService.sendSms(request);

            log.info("SMS envoyé");
            response.put("success", true);
            response.put("message", "SMS envoyé avec succès");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur : {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }



    // Récupérer les préférences d'un utilisateur
    @GetMapping("/preferences/{userId}")
    public ResponseEntity<NotificationPreference> getUserPreferences(
            @PathVariable Long userId,
            @RequestHeader(value = ACCOUNT_ID_HEADER, required = false) String accountId,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        log.info("GET /api/notifications/preferences/{}", userId);
        CallerContext.of(accountId, roles).requireAccessTo(userId);

        Optional<NotificationPreference> preferences = notificationPreferenceRepository.findByUserId(userId);

        if (preferences.isPresent()) {
            return ResponseEntity.ok(preferences.get());
        } else {
            // Créer des préférences par défaut
            NotificationPreference defaultPreferences = NotificationPreference.builder()
                    .userId(userId)
                    .emailEnabled(true)
                    .emailTicketUpdates(true)
                    .emailEventReminders(true)
                    .emailPromotions(false)
                    .emailSystemUpdates(true)
                    .pushEnabled(true)
                    .pushTicketUpdates(true)
                    .pushEventReminders(true)
                    .pushPromotions(false)
                    .smsEnabled(false)
                    .smsTicketUpdates(false)
                    .smsEventReminders(false)
                    .build();

            notificationPreferenceRepository.save(defaultPreferences);

            return ResponseEntity.ok(defaultPreferences);
        }
    }

    // Mettre à jour les préférences
    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreference> updatePreferences(
            @Valid @RequestBody NotificationPreferenceRequestDto request,
            @RequestHeader(value = ACCOUNT_ID_HEADER, required = false) String accountId,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        log.info("PUT /api/notifications/preferences - userId: {}", request.getUserId());
        CallerContext.of(accountId, roles).requireAccessTo(request.getUserId());

        Optional<NotificationPreference> existingPreferences =
                notificationPreferenceRepository.findByUserId(request.getUserId());

        NotificationPreference preferences;

        if (existingPreferences.isPresent()) {
            preferences = existingPreferences.get();
        } else {
            preferences = new NotificationPreference();
            preferences.setUserId(request.getUserId());
        }

        // Mettre à jour les préférences (seulement si non null)
        if (request.getEmailEnabled() != null) preferences.setEmailEnabled(request.getEmailEnabled());
        if (request.getEmailTicketUpdates() != null) preferences.setEmailTicketUpdates(request.getEmailTicketUpdates());
        if (request.getEmailEventReminders() != null) preferences.setEmailEventReminders(request.getEmailEventReminders());
        if (request.getEmailPromotions() != null) preferences.setEmailPromotions(request.getEmailPromotions());
        if (request.getEmailSystemUpdates() != null) preferences.setEmailSystemUpdates(request.getEmailSystemUpdates());

        if (request.getPushEnabled() != null) preferences.setPushEnabled(request.getPushEnabled());
        if (request.getPushTicketUpdates() != null) preferences.setPushTicketUpdates(request.getPushTicketUpdates());
        if (request.getPushEventReminders() != null) preferences.setPushEventReminders(request.getPushEventReminders());
        if (request.getPushPromotions() != null) preferences.setPushPromotions(request.getPushPromotions());

        if (request.getSmsEnabled() != null) preferences.setSmsEnabled(request.getSmsEnabled());
        if (request.getSmsTicketUpdates() != null) preferences.setSmsTicketUpdates(request.getSmsTicketUpdates());
        if (request.getSmsEventReminders() != null) preferences.setSmsEventReminders(request.getSmsEventReminders());

        notificationPreferenceRepository.save(preferences);

        log.info("Préférences mises à jour");
        return ResponseEntity.ok(preferences);
    }



    // Récupérer l'historique des notifications d'un utilisateur
    @GetMapping("/history/{userId}")
    public ResponseEntity<Page<NotificationHistory>> getUserNotificationHistory(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = ACCOUNT_ID_HEADER, required = false) String accountId,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {

        log.info("GET /api/notifications/history/{} - page: {}, size: {}", userId, page, size);
        CallerContext.of(accountId, roles).requireAccessTo(userId);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("sentAt").descending());
        Page<NotificationHistory> history = notificationHistoryRepository.findByUserId(userId, pageRequest);

        log.info("{} notification(s) trouvée(s)", history.getTotalElements());
        return ResponseEntity.ok(history);
    }

    // Récupérer les statistiques des notifications d'un utilisateur
    @GetMapping("/stats/{userId}")
    public ResponseEntity<Map<String, Object>> getUserNotificationStats(
            @PathVariable Long userId,
            @RequestHeader(value = ACCOUNT_ID_HEADER, required = false) String accountId,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        log.info("GET /api/notifications/stats/{}", userId);
        CallerContext.of(accountId, roles).requireAccessTo(userId);

        Map<String, Object> stats = new HashMap<>();

        // Compter les notifications par statut
        Long sentCount = notificationHistoryRepository.countByUserIdAndStatus(
                userId, com.ticket241.notification_service.enums.NotificationStatus.SENT);
        Long failedCount = notificationHistoryRepository.countByUserIdAndStatus(
                userId, com.ticket241.notification_service.enums.NotificationStatus.FAILED);
        Long deliveredCount = notificationHistoryRepository.countByUserIdAndStatus(
                userId, com.ticket241.notification_service.enums.NotificationStatus.DELIVERED);

        stats.put("sent", sentCount);
        stats.put("failed", failedCount);
        stats.put("delivered", deliveredCount);
        stats.put("total", sentCount + failedCount + deliveredCount);

        log.info("Statistiques récupérées");
        return ResponseEntity.ok(stats);
    }


    // Tester l'envoi d'une notification test
    @PostMapping("/test/send")
    public ResponseEntity<Map<String, Object>> sendTestNotification(
            @RequestParam Long userId,
            @RequestParam(required = false) String channel,
            @RequestHeader(value = ACCOUNT_ID_HEADER, required = false) String accountId,
            @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        CallerContext.of(accountId, roles).requireAdmin();

        log.info("POST /api/notifications/test/send - userId: {}, channel: {}", userId, channel);

        Map<String, Object> response = new HashMap<>();

        try {
            // Envoyer une notification de test
            String title = "Test Notification";
            String body = "Ceci est une notification de test de Ticket241. Tout fonctionne correctement !";

            if (channel == null || channel.equalsIgnoreCase("ALL")) {
                // Tester tous les canaux
                notificationService.sendMultiChannelNotification(
                        userId,
                        com.ticket241.notification_service.enums.NotificationType.SYSTEM_UPDATE,
                        title,
                        body,
                        "test@ticket241.com"
                );
            } else {
                // Tester un canal spécifique
                NotificationRequestDto request = NotificationRequestDto.builder()
                        .userId(userId)
                        .notificationType(com.ticket241.notification_service.enums.NotificationType.SYSTEM_UPDATE)
                        .channel(com.ticket241.notification_service.enums.NotificationChannel.valueOf(channel.toUpperCase()))
                        .recipient("test@ticket241.com")
                        .title(title)
                        .body(body)
                        .build();

                notificationService.sendNotification(request);
            }

            log.info("Notification de test envoyée");
            response.put("success", true);
            response.put("message", "Notification de test envoyée avec succès");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur : {}", e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
