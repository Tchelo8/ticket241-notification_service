package com.ticket241.notification_service.service;

import com.ticket241.notification_service.event.TicketConfirmedEvent;
import com.ticket241.notification_service.repository.NotificationHistoryRepository;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Rend le vrai template ticket-confirmation.html et vérifie le message MIME produit
@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private JavaMailSender javaMailSender;
    @Mock
    private NotificationHistoryRepository notificationHistoryRepository;
    @Mock
    private PlatformTransactionManager transactionManager;

    private EmailNotificationService emailService;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        emailService = new EmailNotificationService(javaMailSender, templateEngine,
                notificationHistoryRepository, transactionManager);
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@ticket241.test");
        ReflectionTestUtils.setField(emailService, "fromName", "Ticket241");

        when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));
    }

    @Test
    void confirmationEmailListsTicketsAndAttachesThePdf() throws Exception {
        TicketConfirmedEvent event = TicketConfirmedEvent.builder()
                .orderId("a1b2c3d4-0000-0000-0000-000000000000")
                .recipientEmail("awa@test.com")
                .recipientFirstName("Awa")
                .eventName("Festival des Musiques Urbaines")
                .eventStartDate(LocalDateTime.of(2026, 12, 12, 20, 0))
                .eventVenueName("Stade de l'Amitié")
                .eventVenueCityName("Libreville")
                .ticketCount(2)
                .totalAmount(new BigDecimal("25000"))
                .currency("XAF")
                .tickets(List.of(
                        TicketConfirmedEvent.TicketLine.builder()
                                .ticketNumber("TKT-2026-AAAAAAAAAA").categoryName("VIP").holderName("Awa Mba").build(),
                        TicketConfirmedEvent.TicketLine.builder()
                                .ticketNumber("TKT-2026-BBBBBBBBBB").categoryName("VIP").build()))
                .build();

        emailService.sendTicketConfirmationEmail(event, "%PDF-fake".getBytes());

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(sent.capture());
        MimeMessage message = sent.getValue();
        message.saveChanges();

        assertThat(message.getSubject()).isEqualTo("Vos 2 billets pour Festival des Musiques Urbaines");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("awa@test.com");

        List<Part> parts = new ArrayList<>();
        collectParts(message, parts);

        Part attachment = parts.stream()
                .filter(part -> fileName(part) != null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Pas de pièce jointe"));
        assertThat(attachment.getFileName()).isEqualTo("billets-ticket241-A1B2C3D4.pdf");
        assertThat(attachment.getContentType()).startsWith("application/pdf");

        String html = parts.stream()
                .filter(part -> isHtml(part))
                .map(EmailNotificationServiceTest::content)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Pas de corps HTML"));
        assertThat(html)
                .contains("Bonjour <span>Awa</span>")
                .contains("Billets confirmés !")
                .contains("Samedi 12 décembre 2026 à 20h00")
                .contains("Stade de l&#39;Amitié, Libreville")
                .contains("TKT-2026-AAAAAAAAAA")
                .contains("TKT-2026-BBBBBBBBBB")
                .contains("A1B2C3D4");
    }

    private static String fileName(Part part) {
        try {
            return part.getFileName();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isHtml(Part part) {
        try {
            return part.isMimeType("text/html");
        } catch (Exception e) {
            return false;
        }
    }

    private static String content(Part part) {
        try {
            return (String) part.getContent();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void collectParts(Part part, List<Part> parts) throws Exception {
        parts.add(part);
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                collectParts(child, parts);
            }
        }
    }
}
