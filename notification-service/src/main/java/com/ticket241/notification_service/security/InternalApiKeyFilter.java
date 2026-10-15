package com.ticket241.notification_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// Exige la clé partagée service-à-service sur les endpoints internes (ex: OTP appelés par auth-service).
// Ne remplace pas une isolation réseau : ces endpoints ne devraient de toute façon pas être exposés
// au-delà de la gateway / du réseau interne.
@Slf4j
public class InternalApiKeyFilter extends HttpFilter {

    public static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private final String expectedApiKey;

    public InternalApiKeyFilter(String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        String providedKey = request.getHeader(API_KEY_HEADER);

        if (providedKey == null || !constantTimeEquals(providedKey, expectedApiKey)) {
            log.warn("[Security] Appel refusé sur {} : clé interne absente ou invalide", request.getRequestURI());
            // 403 et non 401 : le 401 est réservé à "code OTP invalide" sur /otp/verify, l'appelant
            // doit pouvoir distinguer une erreur de configuration d'une erreur de saisie utilisateur
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"status\":403,\"error\":\"INTERNAL_API_KEY_REJECTED\","
                            + "\"message\":\"Clé API interne invalide ou manquante\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    // Comparaison en temps constant pour éviter une fuite d'information par timing
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}