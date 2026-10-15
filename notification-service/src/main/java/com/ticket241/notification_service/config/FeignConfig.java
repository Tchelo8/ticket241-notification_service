package com.ticket241.notification_service.config;



import feign.Logger;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.openfeign.EnableFeignClients;

@Configuration
@EnableFeignClients(basePackages = "com.ticket241.notification_service.client")
public class FeignConfig {

    // Niveau de logging pour Feign (utile pour debug)
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    // Clé partagée service-à-service : la même que celle exigée sur nos propres endpoints internes,
    // et attendue par ticket-service sur /internal/** (ticket.internal.api-key)
    @Value("${notification.internal.api-key}")
    private String internalApiKey;

    // Intercepteur pour ajouter des headers à toutes les requêtes Feign
    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            // Ajouter un header pour identifier les requêtes inter-services
            requestTemplate.header("X-Internal-Request", "notification-service-ticket241");
            requestTemplate.header("X-Internal-Api-Key", internalApiKey);
        };
    }
}
