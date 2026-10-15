package com.ticket241.notification_service.repository;


import com.ticket241.notification_service.entity.EmailTemplate;
import com.ticket241.notification_service.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    Optional<EmailTemplate> findByNotificationType(NotificationType notificationType);

    Optional<EmailTemplate> findByNotificationTypeAndIsActiveTrue(NotificationType notificationType);
}
