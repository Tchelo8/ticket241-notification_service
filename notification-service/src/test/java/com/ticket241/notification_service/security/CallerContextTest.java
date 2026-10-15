package com.ticket241.notification_service.security;

import com.ticket241.notification_service.exception.NotificationAccessDeniedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallerContextTest {

    @Test
    void onlyAdminsCanSendFreeMessages() {
        assertThatThrownBy(() -> CallerContext.of("12", "ROLE_USER").requireAdmin())
                .isInstanceOf(NotificationAccessDeniedException.class);
        assertThatThrownBy(() -> CallerContext.of("12", "ROLE_STRUCTURE_ADMIN").requireAdmin())
                .isInstanceOf(NotificationAccessDeniedException.class);
        assertThatCode(() -> CallerContext.of("1", "ROLE_USER,ROLE_ADMIN").requireAdmin()).doesNotThrowAnyException();
    }

    @Test
    void aUserOnlyAccessesItsOwnData() {
        CallerContext user12 = CallerContext.of("12", "ROLE_USER");

        assertThatCode(() -> user12.requireAccessTo(12L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> user12.requireAccessTo(13L)).isInstanceOf(NotificationAccessDeniedException.class);
        assertThatCode(() -> CallerContext.of("1", "ROLE_ADMIN").requireAccessTo(13L)).doesNotThrowAnyException();
    }

    @Test
    void oldTokenWithoutAccountIdMustReconnect() {
        assertThatThrownBy(() -> CallerContext.of(null, "ROLE_USER").requireAccessTo(12L))
                .isInstanceOf(NotificationAccessDeniedException.class)
                .hasMessageContaining("reconnectez-vous");
    }
}
