package com.ticket241.notification_service.consumer;

import com.ticket241.notification_service.client.TicketServiceClient;
import com.ticket241.notification_service.event.TicketConfirmedEvent;
import com.ticket241.notification_service.exception.NotificationException;
import com.ticket241.notification_service.service.EmailNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketConfirmedEventConsumerTest {

    @Mock
    private TicketServiceClient ticketServiceClient;
    @Mock
    private EmailNotificationService emailNotificationService;
    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private TicketConfirmedEventConsumer consumer;

    private TicketConfirmedEvent event(String email) {
        return TicketConfirmedEvent.builder()
                .orderId("order-1")
                .recipientEmail(email)
                .eventName("Concert")
                .ticketCount(2)
                .build();
    }

    @Test
    void sendsConfirmationWithTheTicketsPdf() {
        byte[] pdf = "%PDF-fake".getBytes();
        TicketConfirmedEvent event = event("awa@test.com");
        when(ticketServiceClient.getOrderTicketsPdf("order-1")).thenReturn(pdf);

        consumer.handleTicketConfirmed(event, 0, 1L, acknowledgment);

        verify(emailNotificationService).sendTicketConfirmationEmail(event, pdf);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void orderWithoutEmailIsAcknowledgedWithoutSending() {
        consumer.handleTicketConfirmed(event(null), 0, 1L, acknowledgment);

        verify(ticketServiceClient, never()).getOrderTicketsPdf(anyString());
        verify(emailNotificationService, never()).sendTicketConfirmationEmail(any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void emailFailureIsRethrownForKafkaRetry() {
        TicketConfirmedEvent event = event("awa@test.com");
        when(ticketServiceClient.getOrderTicketsPdf("order-1")).thenReturn(new byte[]{1});
        doThrow(new NotificationException("SMTP down"))
                .when(emailNotificationService).sendTicketConfirmationEmail(any(), any());

        assertThatThrownBy(() -> consumer.handleTicketConfirmed(event, 0, 1L, acknowledgment))
                .isInstanceOf(NotificationException.class);
        verify(acknowledgment, never()).acknowledge();
    }
}
