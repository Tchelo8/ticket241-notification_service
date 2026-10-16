package com.ticket241.notification_service.config;

import com.ticket241.notification_service.security.InternalApiKeyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InternalSecurityConfig {

    @Value("${notification.internal.api-key}")
    private String internalApiKey;

    // Filtre appliqué uniquement aux endpoints internes (OTP, alertes admin), pas à l'ensemble de l'API
    @Bean
    public FilterRegistrationBean<InternalApiKeyFilter> internalApiKeyFilterRegistration() {
        FilterRegistrationBean<InternalApiKeyFilter> registration =
                new FilterRegistrationBean<>(new InternalApiKeyFilter(internalApiKey));

        registration.addUrlPatterns("/api/notifications/otp/*", "/api/notifications/internal/*");
        registration.setOrder(1);
        registration.setName("internalApiKeyFilter");

        return registration;
    }
}