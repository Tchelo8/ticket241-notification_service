package com.ticket241.notification_service.repository;



import com.ticket241.notification_service.entity.NotificationHistory;
import com.ticket241.notification_service.enums.NotificationChannel;
import com.ticket241.notification_service.enums.NotificationStatus;
import com.ticket241.notification_service.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    Page<NotificationHistory> findByUserId(Long userId, Pageable pageable);

    List<NotificationHistory> findByUserIdAndNotificationType(Long userId, NotificationType type);

    List<NotificationHistory> findByStatusAndRetryCountLessThan(NotificationStatus status, Integer maxRetries);

    @Query("SELECT COUNT(n) FROM NotificationHistory n WHERE n.userId = :userId AND n.status = :status")
    Long countByUserIdAndStatus(Long userId, NotificationStatus status);

    @Query("SELECT n FROM NotificationHistory n WHERE n.status = 'FAILED' AND n.retryCount < :maxRetries AND n.sentAt > :after")
    List<NotificationHistory> findFailedNotificationsForRetry(Integer maxRetries, LocalDateTime after);

    List<NotificationHistory> findByChannelAndSentAtBetween(NotificationChannel channel, LocalDateTime start, LocalDateTime end);
}
