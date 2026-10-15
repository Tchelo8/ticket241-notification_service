package com.ticket241.notification_service.client;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

// Client Feign pour communiquer avec Auth Service
@FeignClient(name = "auth-service", path = "/api/users")
public interface UserServiceClient {

    @GetMapping("/{userId}")
    Map<String, Object> getUserById(@PathVariable Long userId);

    // Récupérer l'email d'un utilisateur
    @GetMapping("/{userId}/email")
    String getUserEmail(@PathVariable Long userId);

    //récup le  num
    @GetMapping("/{userId}/phone")
    String getUserPhone(@PathVariable Long userId);
}
