package com.ticket241.notification_service.service;



import com.ticket241.notification_service.dto.request.OtpRequestDto;
import com.ticket241.notification_service.dto.request.OtpVerificationRequestDto;
import com.ticket241.notification_service.dto.response.OtpResponseDto;
import com.ticket241.notification_service.dto.response.OtpVerificationResponseDto;
import com.ticket241.notification_service.entity.OtpCode;
import com.ticket241.notification_service.repository.OtpCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpCodeRepository otpCodeRepository;
    private final EmailNotificationService emailNotificationService;

    @Value("${notification.otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${notification.otp.length:6}")
    private int otpLength;

    // Pepper applicatif mélangé au code avant hash : une fuite de la base seule ne suffit pas à retrouver les codes
    @Value("${notification.otp.hash-secret}")
    private String otpHashSecret;

    private static final int MAX_OTP_ATTEMPTS = 5;
    // Filet de sécurité : auth-service applique déjà 3 envois max en 15 min avant de publier,
    // cette limite plus large ne doit se déclencher que sur un appel anormal
    private static final int MAX_OTP_PER_PERIOD = 5;
    private static final int RATE_LIMIT_MINUTES = 15;

    // Génère un OTP et l'envoie par email dans la même transaction : si l'email ne part pas,
    // le code n'est pas conservé (pas de code fantôme qui consomme le quota) et l'ancien code
    // reste valide. L'exception remonte à l'appelant (retry Kafka, réponse HTTP).
    // Le code en clair n'est jamais renvoyé dans la réponse.
    @Transactional
    public OtpResponseDto generateAndSendOtp(OtpRequestDto request) {
        OtpResponseDto response = generateOtp(request);

        if (Boolean.TRUE.equals(response.getSuccess())) {
            emailNotificationService.sendOtpEmail(
                    request.getEmail(),
                    request.getFirstName(),
                    response.getOtpCode(),
                    request.getUserId()
            );
        }

        response.setOtpCode(null);
        return response;
    }

    @Transactional
    public OtpResponseDto generateOtp(OtpRequestDto request) {

        log.info("Génération d'un OTP pour userId: {}, email: {}", request.getUserId(), request.getEmail());

        // Vérifier le rate limiting
        LocalDateTime rateLimitThreshold = LocalDateTime.now().minusMinutes(RATE_LIMIT_MINUTES);
        long recentOtpCount = otpCodeRepository.findByUserIdOrderByCreatedAtDesc(request.getUserId())
                .stream()
                .filter(otp -> otp.getCreatedAt().isAfter(rateLimitThreshold))
                .count();

        if (recentOtpCount >= MAX_OTP_PER_PERIOD) {
            log.warn("Limite de tentatives atteintes pour userId: {}. {} OTP en {} minutes",
                    request.getUserId(), recentOtpCount, RATE_LIMIT_MINUTES);
            return OtpResponseDto.builder()
                    .success(false)
                    .message("Trop de tentatives. Veuillez réessayer dans " + RATE_LIMIT_MINUTES + " minutes.")
                    .build();
        }

        // Expirer tous les anciens OTP non utilisés de cet utilisateur
        otpCodeRepository.expireAllUnusedCodesForUser(request.getUserId());
        log.debug("Anciens OTP expirés pour userId: {}", request.getUserId());

        // Générer un code OTP sécurisé
        String otpCode = generateSecureOtpCode();
        log.debug("Code OTP généré mais ne sera pas loggé en production");

        // Calculer la date d'expiration
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(otpExpiryMinutes);

        // Créer et sauvegarder l'entité OTP (seul le hash du code est persisté)
        OtpCode otpEntity = OtpCode.builder()
                .userId(request.getUserId())
                .email(request.getEmail())
                .code(hashOtp(otpCode))
                .isUsed(false)
                .isExpired(false)
                .expiresAt(expiresAt)
                .attemptCount(0)
                .build();

        otpCodeRepository.save(otpEntity);

        log.info("OTP créé avec succès. ID: {}, expire à: {}", otpEntity.getId(), expiresAt);

        return OtpResponseDto.builder()
                .success(true)
                .message("Code OTP généré avec succès")
                .expiresAt(expiresAt)
                .otpCode(otpCode) // Supprimer en production !
                .build();
    }

    // Vérifier un code OTP
    @Transactional
    public OtpVerificationResponseDto verifyOtp(OtpVerificationRequestDto request) {

        log.info("Vérification OTP pour userId: {}, code: ****", request.getUserId());

        // On charge le code actif de l'utilisateur (au plus un : generateOtp expire les précédents)
        // plutôt que de chercher par hash, sinon un mauvais code ne serait jamais compté comme tentative.
        // Verrou pessimiste : deux vérifications simultanées ne peuvent pas contourner le compteur.
        Optional<OtpCode> otpOptional = otpCodeRepository
                .findFirstByUserIdAndIsUsedFalseAndIsExpiredFalseOrderByCreatedAtDesc(request.getUserId());

        if (otpOptional.isEmpty()) {
            log.warn("Aucun OTP actif pour userId: {}", request.getUserId());
            return OtpVerificationResponseDto.builder()
                    .valid(false)
                    .message("Code OTP invalide ou expiré. Demandez un nouveau code.")
                    .remainingAttempts(0)
                    .build();
        }

        OtpCode otpCode = otpOptional.get();

        if (otpCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("OTP expiré pour userId: {}. Expiré à: {}",
                    request.getUserId(), otpCode.getExpiresAt());

            otpCode.setIsExpired(true);
            otpCodeRepository.save(otpCode);

            return OtpVerificationResponseDto.builder()
                    .valid(false)
                    .message("Code OTP expiré. Demandez-en un nouveau.")
                    .remainingAttempts(0)
                    .build();
        }

        // Chaque vérification compte comme une tentative, bonne ou mauvaise
        otpCode.setAttemptCount(otpCode.getAttemptCount() + 1);

        boolean codeMatches = MessageDigest.isEqual(
                hashOtp(request.getCode()).getBytes(StandardCharsets.UTF_8),
                otpCode.getCode().getBytes(StandardCharsets.UTF_8));

        if (!codeMatches) {
            int remainingAttempts = Math.max(0, MAX_OTP_ATTEMPTS - otpCode.getAttemptCount());

            if (remainingAttempts == 0) {
                // Code brûlé : il faudra en redemander un
                log.warn("Nombre maximum de tentatives atteint pour userId: {}", request.getUserId());
                otpCode.setIsExpired(true);
                otpCodeRepository.save(otpCode);

                return OtpVerificationResponseDto.builder()
                        .valid(false)
                        .message("Nombre maximum de tentatives atteint. Demandez un nouveau code.")
                        .remainingAttempts(0)
                        .build();
            }

            log.warn("Code OTP incorrect pour userId: {} ({} tentative(s) restante(s))",
                    request.getUserId(), remainingAttempts);
            otpCodeRepository.save(otpCode);

            return OtpVerificationResponseDto.builder()
                    .valid(false)
                    .message("Code OTP incorrect. Il vous reste " + remainingAttempts + " tentative(s).")
                    .remainingAttempts(remainingAttempts)
                    .build();
        }

        // OTP valide ! Marquer comme utilisé
        otpCode.setIsUsed(true);
        otpCode.setVerifiedAt(LocalDateTime.now());
        otpCodeRepository.save(otpCode);

        log.info("OTP vérifié avec succès pour userId: {}", request.getUserId());

        return OtpVerificationResponseDto.builder()
                .valid(true)
                .message("Code OTP validé avec succès")
                .remainingAttempts(MAX_OTP_ATTEMPTS - otpCode.getAttemptCount())
                .build();
    }

    // Générer un code OTP sécurisé
    private String generateSecureOtpCode() {
        SecureRandom random = new SecureRandom();

        // Génère un nombre aléatoire entre 0 et 999999
        int max = (int) Math.pow(10, otpLength);
        int otpNumber = random.nextInt(max);

        // Formatter avec des zéros à gauche si nécessaire
        String format = "%0" + otpLength + "d";
        return String.format(format, otpNumber);
    }

    // Hash SHA-256 du code + pepper applicatif : seul ce hash est persisté, jamais le code en clair
    private String hashOtp(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((code + otpHashSecret).getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    // Job planifié pour nettoyer les OTP expirés toutes les heures
    @Scheduled(cron = "0 0 * * * *") // Chaque heure
    @Transactional
    public void cleanupExpiredOtps() {
        log.info("Démarrage du nettoyage des OTP expirés...");

        LocalDateTime now = LocalDateTime.now();
        int expiredCount = otpCodeRepository.expireOldCodes(now);

        if (expiredCount > 0) {
            log.info("{} OTP expirés marqués comme expirés", expiredCount);
        } else {
            log.debug("Aucun OTP expiré trouvé");
        }
    }
}
