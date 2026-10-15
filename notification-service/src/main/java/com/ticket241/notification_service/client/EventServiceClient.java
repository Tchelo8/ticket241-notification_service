package com.ticket241.notification_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// Client Feign pour communiquer avec Event Service


@FeignClient(name = "event-service-ticket241", path = "/api/events")
public interface EventServiceClient {

    // Récupérer un événement par son ID
    @GetMapping("/{eventId}")
    Map<String, Object> getEventById(@PathVariable Long eventId);

    // Récupérer les événements qui commencent dans les prochaines 24h
    @GetMapping("/upcoming")
    List<Map<String, Object>> getUpcomingEvents(@RequestParam LocalDateTime startTime);
}
