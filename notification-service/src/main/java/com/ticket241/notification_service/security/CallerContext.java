package com.ticket241.notification_service.security;

import com.ticket241.notification_service.exception.NotificationAccessDeniedException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

// Appelant d'un endpoint public (/api/notifications/**), tel que la gateway l'a extrait du JWT :
// X-User-Account-Id (identifiant numérique du compte auth-service) et X-User-Roles.
// Les endpoints internes (OTP) n'en ont pas besoin : ils sont protégés par la clé interne.
public record CallerContext(Long accountId, Set<String> roles) {

    public static CallerContext of(String accountIdHeader, String rolesHeader) {
        Set<String> roles = rolesHeader == null ? Set.of() : Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        Long accountId = null;
        if (accountIdHeader != null && !accountIdHeader.isBlank()) {
            try {
                accountId = Long.valueOf(accountIdHeader.trim());
            } catch (NumberFormatException ignored) {
                // header malformé : traité comme absent
            }
        }
        return new CallerContext(accountId, roles);
    }

    public boolean isAdmin() {
        return roles.contains("ROLE_ADMIN");
    }

    // Envoi libre d'emails, SMS, push ou notifications de test : admin uniquement
    public void requireAdmin() {
        if (!isAdmin()) {
            throw new NotificationAccessDeniedException("Action réservée aux administrateurs Ticket241");
        }
    }

    // Données d'un compte (préférences, historique, jetons push) : son propriétaire ou un admin
    public void requireAccessTo(Long userId) {
        if (isAdmin()) {
            return;
        }
        if (accountId == null) {
            throw new NotificationAccessDeniedException(
                    "Session incomplète : reconnectez-vous pour accéder à vos notifications");
        }
        if (!accountId.equals(userId)) {
            throw new NotificationAccessDeniedException("Vous ne pouvez accéder qu'à vos propres notifications");
        }
    }
}
