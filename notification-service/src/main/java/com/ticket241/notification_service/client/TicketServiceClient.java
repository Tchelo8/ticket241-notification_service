package com.ticket241.notification_service.client;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// Client Feign pour communiquer avec Ticket Service (résolu via Eureka).
// Endpoints internes : la clé X-Internal-Api-Key est ajoutée par FeignConfig.

@FeignClient(name = "ticket-service-ticket241", path = "/internal/tickets")
public interface TicketServiceClient {

    // PDF des billets payés d'une commande, joint à l'email de confirmation
    @GetMapping("/orders/{orderId}/pdf")
    byte[] getOrderTicketsPdf(@PathVariable("orderId") String orderId);
}
