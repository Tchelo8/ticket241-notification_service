package com.ticket241.notification_service.repository;

import com.ticket241.notification_service.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    List<FcmToken> findByUserIdAndIsActiveTrue(Long userId);

    Optional<FcmToken> findByFcmToken(String fcmToken);

    @Modifying
    @Query("UPDATE FcmToken f SET f.isActive = false WHERE f.userId = :userId")
    void deactivateAllTokensForUser(Long userId);

    @Modifying
    @Query("UPDATE FcmToken f SET f.isActive = false WHERE f.fcmToken = :fcmToken")
    void deactivateToken(String fcmToken);

    boolean existsByUserIdAndFcmToken(Long userId, String fcmToken);
}
