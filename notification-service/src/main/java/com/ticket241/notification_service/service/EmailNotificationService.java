package com.ticket241.notification_service.service;


import com.ticket241.notification_service.dto.request.AdminAlertRequestDto;
import com.ticket241.notification_service.dto.request.EmailRequestDto;
import com.ticket241.notification_service.entity.NotificationHistory;
import com.ticket241.notification_service.enums.NotificationChannel;
import com.ticket241.notification_service.enums.NotificationStatus;
import com.ticket241.notification_service.enums.NotificationType;
import com.ticket241.notification_service.event.TicketConfirmedEvent;
import com.ticket241.notification_service.exception.NotificationException;
import com.ticket241.notification_service.repository.NotificationHistoryRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class EmailNotificationService {

    private final JavaMailSender javaMailSender;
    private final SpringTemplateEngine templateEngine;
    private final NotificationHistoryRepository notificationHistoryRepository;
    private final TransactionTemplate historyTransactionTemplate;

    public EmailNotificationService(JavaMailSender javaMailSender,
                                    SpringTemplateEngine templateEngine,
                                    NotificationHistoryRepository notificationHistoryRepository,
                                    PlatformTransactionManager transactionManager) {
        this.javaMailSender = javaMailSender;
        this.templateEngine = templateEngine;
        this.notificationHistoryRepository = notificationHistoryRepository;
        this.historyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.historyTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${notification.email.from-name:Ticket241}")
    private String fromName;

    // Envoyer un email simple (texte brut)
    public void sendSimpleEmail(EmailRequestDto request) {
        sendEmail(request, false);
    }

    // Envoyer un email HTML
    public void sendHtmlEmail(EmailRequestDto request) {
        sendEmail(request, true);
    }

    // Envoyer un email avec un template Thymeleaf
    public void sendTemplateEmail(String to, String subject, String templateName,
                                  Map<String, Object> variables, Long userId,
                                  NotificationType notificationType) {
        sendTemplateEmail(to, subject, templateName, variables, userId, notificationType, null, null);
    }

    // Envoyer un email avec un template Thymeleaf et un PDF en pièce jointe (null = pas de pièce jointe)
    public void sendTemplateEmail(String to, String subject, String templateName,
                                  Map<String, Object> variables, Long userId,
                                  NotificationType notificationType,
                                  String pdfAttachmentName, byte[] pdfAttachment) {

        log.info("Préparation email avec template: {} pour: {}", templateName, to);

        // Créer le contexte Thymeleaf avec les variables
        Context context = new Context();
        context.setVariables(variables);

        // Générer le HTML depuis le template
        String htmlContent;
        try {
            htmlContent = templateEngine.process(templateName, context);
        } catch (Exception e) {
            log.error("Erreur lors de la génération du template {}: {}", templateName, e.getMessage());
            saveNotificationHistory(userId, to, subject, null, notificationType,
                    NotificationStatus.FAILED, e.getMessage());
            throw new NotificationException("Échec de la génération du template " + templateName, e);
        }

        // Créer la requête email
        EmailRequestDto emailRequest = EmailRequestDto.builder()
                .to(to)
                .subject(subject)
                .body(htmlContent)
                .isHtml(true)
                .build();

        // Envoyer l'email : un échec remonte à l'appelant (ex: retry Kafka pour l'OTP)
        sendEmail(emailRequest, true, userId, notificationType, pdfAttachmentName, pdfAttachment);
    }

    // Méthode privée pour envoyer l'email
    private void sendEmail(EmailRequestDto request, boolean isHtml) {
        sendEmail(request, isHtml, null, null, null, null);
    }

    // Méthode privée complète pour envoyer l'email
    private void sendEmail(EmailRequestDto request, boolean isHtml, Long userId,
                           NotificationType notificationType,
                           String pdfAttachmentName, byte[] pdfAttachment) {

        log.info("Envoi d'email à: {}", request.getTo());
        log.debug("Sujet: {}", request.getSubject());

        try {
            // Créer le message MIME
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            // Configuration de l'expéditeur
            helper.setFrom(fromEmail, fromName);
            helper.setTo(request.getTo());
            helper.setSubject(request.getSubject());
            helper.setText(request.getBody(), isHtml);

            // Ajouter CC si présent
            if (request.getCc() != null && request.getCc().length > 0) {
                helper.setCc(request.getCc());
            }

            // Ajouter BCC si présent
            if (request.getBcc() != null && request.getBcc().length > 0) {
                helper.setBcc(request.getBcc());
            }

            // Ajouter la pièce jointe PDF si présente (ex: billets de la commande)
            if (pdfAttachment != null && pdfAttachment.length > 0) {
                helper.addAttachment(pdfAttachmentName, new ByteArrayResource(pdfAttachment), "application/pdf");
            }

            // Envoyer l'email
            javaMailSender.send(mimeMessage);

            log.info("Email envoyé avec succès à: {}", request.getTo());

            // Sauvegarder dans l'historique
            saveNotificationHistory(userId, request.getTo(), request.getSubject(),
                    request.getBody(), notificationType, NotificationStatus.SENT, null);

        } catch (MessagingException | java.io.UnsupportedEncodingException | MailException e) {
            // MailException couvre les échecs SMTP (auth refusée, serveur injoignable…) levés par send()
            log.error("Erreur lors de l'envoi de l'email à {}: {}", request.getTo(), e.getMessage());

            // Sauvegarder l'échec dans l'historique
            saveNotificationHistory(userId, request.getTo(), request.getSubject(),
                    request.getBody(), notificationType, NotificationStatus.FAILED, e.getMessage());

            throw new NotificationException("Échec de l'envoi de l'email", e);
        }
    }

    // Alerte aux administrateurs Ticket241 (texte brut). Envoi destinataire par destinataire :
    // un email refusé ne doit pas empêcher les autres admins d'être prévenus. Renvoie le nombre d'envois réussis.
    public int sendAdminAlert(AdminAlertRequestDto request) {
        int sent = 0;
        for (String recipient : request.getRecipients()) {
            try {
                sendSimpleEmail(EmailRequestDto.builder()
                        .to(recipient)
                        .subject("[Ticket241 - Alerte] " + request.getSubject())
                        .body(request.getMessage())
                        .isHtml(false)
                        .build());
                sent++;
            } catch (NotificationException e) {
                log.error("Alerte admin non envoyée à {} : {}", recipient, e.getMessage());
            }
        }
        return sent;
    }

    // Envoyer un OTP par email
    public void sendOtpEmail(String to, String firstName, String otpCode, Long userId) {

        log.info("Envoi de l'OTP par email à: {}", to);

        // Variables pour le template
        Map<String, Object> variables = Map.of(
                "firstName", firstName != null ? firstName : "Utilisateur",
                "otpCode", otpCode,
                "expiryMinutes", 5
        );

        // Envoyer avec le template OTP
        sendTemplateEmail(
                to,
                "Votre code de vérification Ticket241",
                "email/otp",
                variables,
                userId,
                NotificationType.OTP_VERIFICATION
        );
    }

    // Envoyer un email de bienvenue
    public void sendWelcomeEmail(String to, String firstName, Long userId) {

        log.info("Envoi de l'email de bienvenue à: {}", to);

        Map<String, Object> variables = Map.of(
                "firstName", firstName != null ? firstName : "Utilisateur"
        );

        sendTemplateEmail(
                to,
                "Bienvenue sur Ticket241 ! 🎉",
                "email/welcome",
                variables,
                userId,
                NotificationType.USER_REGISTRATION
        );
    }

    // Envoyer l'email de confirmation d'une commande payée, avec le PDF des billets en pièce jointe.
    // Le QR code n'est pas affiché dans l'email (les messageries bloquent les images intégrées) :
    // il est dans le PDF, qui est le billet à présenter à l'entrée.
    public void sendTicketConfirmationEmail(TicketConfirmedEvent event, byte[] ticketsPdf) {

        log.info("Envoi de la confirmation de la commande {} à: {}", event.getOrderId(), event.getRecipientEmail());

        int ticketCount = event.getTicketCount() != null ? event.getTicketCount() : 1;

        // HashMap et non Map.of : certaines valeurs (lieu, nom du titulaire…) peuvent être absentes
        Map<String, Object> variables = new HashMap<>();
        variables.put("firstName", event.getRecipientFirstName() != null ? event.getRecipientFirstName() : "cher client");
        variables.put("eventName", event.getEventName());
        variables.put("eventDate", formatEventDate(event.getEventStartDate()));
        variables.put("eventLocation", formatLocation(event.getEventVenueName(), event.getEventVenueCityName()));
        variables.put("ticketCount", ticketCount);
        variables.put("totalAmount", formatAmount(event.getTotalAmount(), event.getCurrency()));
        variables.put("tickets", event.getTickets() != null ? event.getTickets() : List.of());
        variables.put("orderReference", shortReference(event.getOrderId()));

        String subject = ticketCount > 1
                ? "Vos " + ticketCount + " billets pour " + event.getEventName()
                : "Votre billet pour " + event.getEventName();

        sendTemplateEmail(
                event.getRecipientEmail(),
                subject,
                "email/ticket-confirmation",
                variables,
                null, // userId de l'historique = Long, alors que les billets sont rattachés au téléphone
                NotificationType.TICKET_CONFIRMED,
                "billets-ticket241-" + shortReference(event.getOrderId()) + ".pdf",
                ticketsPdf
        );
    }

    private String formatEventDate(LocalDateTime date) {
        if (date == null) {
            return "";
        }
        String formatted = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH'h'mm", Locale.FRENCH));
        return Character.toUpperCase(formatted.charAt(0)) + formatted.substring(1);
    }

    private String formatLocation(String venue, String city) {
        if (venue == null) {
            return city;
        }
        return city == null ? venue : venue + ", " + city;
    }

    private String formatAmount(BigDecimal amount, String currency) {
        if (amount == null) {
            return null;
        }
        return NumberFormat.getIntegerInstance(Locale.FRENCH).format(amount) + " " + (currency != null ? currency : "XAF");
    }

    // Les 8 premiers caractères de l'orderId suffisent à retrouver la commande côté support
    private String shortReference(String orderId) {
        if (orderId == null) {
            return "";
        }
        return orderId.substring(0, Math.min(8, orderId.length())).toUpperCase(Locale.ROOT);
    }

    // Envoyer un email de rappel d'événement
    public void sendEventReminderEmail(String to, String firstName,
                                       String eventName, String eventDate,
                                       String eventLocation, Long userId) {

        log.info("Envoi de rappel d'événement à: {}", to);

        Map<String, Object> variables = Map.of(
                "firstName", firstName,
                "eventName", eventName,
                "eventDate", eventDate,
                "eventLocation", eventLocation
        );

        sendTemplateEmail(
                to,
                "Rappel : " + eventName + " commence bientôt !",
                "email/event-reminder",
                variables,
                userId,
                NotificationType.EVENT_REMINDER_24H
        );
    }

    // Sauvegarder l'historique de notification
    private void saveNotificationHistory(Long userId, String recipient, String subject,
                                         String body, NotificationType notificationType,
                                         NotificationStatus status, String errorMessage) {

        if (userId == null) {
            return;
        }

        try {
            NotificationHistory history = NotificationHistory.builder()
                    .userId(userId)
                    .notificationType(notificationType)
                    .channel(NotificationChannel.EMAIL)
                    .recipient(recipient)
                    .title(subject)
                    .body(body != null && body.length() > 500 ? body.substring(0, 500) : body)
                    .status(status)
                    .errorMessage(errorMessage)
                    .sentAt(LocalDateTime.now())
                    .build();

            // Transaction séparée : l'historique d'un échec doit survivre au rollback de l'appelant
            // (ex: génération d'OTP annulée quand l'email ne part pas)
            historyTransactionTemplate.executeWithoutResult(tx -> notificationHistoryRepository.save(history));
            log.debug("Historique de notification sauvegardé");

        } catch (Exception e) {
            log.error("Erreur lors de la sauvegarde de l'historique: {}", e.getMessage());
            // Ne pas throw, c'est secondaire
        }
    }
}
