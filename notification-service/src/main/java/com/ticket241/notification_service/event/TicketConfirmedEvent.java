package com.ticket241.notification_service.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Miroir de TicketConfirmedEvent côté ticket-service (topic ticket-confirmed) : mêmes noms de champs pour le JSON
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketConfirmedEvent {

    private String orderId;
    private String paymentId;
    private String userId;

    private String recipientEmail;
    private String recipientFirstName;
    private String recipientLastName;

    private String eventName;
    private LocalDateTime eventStartDate;
    private String eventVenueName;
    private String eventVenueCityName;

    private Integer ticketCount;
    private BigDecimal totalAmount;
    private String currency;
    private List<TicketLine> tickets;

    private LocalDateTime confirmedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketLine {
        private String ticketNumber;
        private String categoryName;
        private String holderName;
        private BigDecimal finalPrice;
    }
}
