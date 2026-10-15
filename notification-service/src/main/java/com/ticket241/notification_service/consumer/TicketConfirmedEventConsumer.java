package com.ticket241.notification_service.consumer;

import com.ticket241.notification_service.client.TicketServiceClient;
import com.ticket241.notification_service.event.TicketConfirmedEvent;
import com.ticket241.notification_service.service.EmailNotificationService;
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
public class TicketConfirmedEventConsumer {

    private final TicketServiceClient ticketServiceClient;
    private final EmailNotificationService emailNotificationService;

    @KafkaListener(
            topics = "ticket-confirmed",
            groupId = "notification-service-group",
            containerFactory = "ticketConfirmedListenerContainerFactory"
    )
    public void handleTicketConfirmed(
            @Payload TicketConfirmedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        log.info("[Kafka] ticket-confirmed reçu — partition={}, offset={}, commande={}",
                partition, offset, event.getOrderId());

        // Sans adresse, rejouer n'y changerait rien : le client récupère ses billets dans l'application
        if (event.getRecipientEmail() == null || event.getRecipientEmail().isBlank()) {
            log.warn("[Kafka] Commande {} sans email destinataire : confirmation non envoyée", event.getOrderId());
            acknowledgment.acknowledge();
            return;
        }

        // PDF puis email : une exception (ticket-service injoignable, SMTP en panne…) remonte au
        // conteneur, qui applique le retry borné puis le DLT (KafkaConsumerConfig.kafkaErrorHandler)
        byte[] ticketsPdf = ticketServiceClient.getOrderTicketsPdf(event.getOrderId());
        emailNotificationService.sendTicketConfirmationEmail(event, ticketsPdf);

        log.info("[Kafka] Confirmation de la commande {} envoyée à {} ({} billet(s))",
                event.getOrderId(), event.getRecipientEmail(), event.getTicketCount());

        acknowledgment.acknowledge();
    }
}
