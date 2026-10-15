package com.ticket241.notification_service.repository;



import com.ticket241.notification_service.entity.OtpCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    // Code actif le plus récent, verrouillé le temps de la vérification pour que le compteur
    // de tentatives ne puisse pas être contourné par des appels concurrents
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpCode> findFirstByUserIdAndIsUsedFalseAndIsExpiredFalseOrderByCreatedAtDesc(Long userId);

    Optional<OtpCode> findByEmailAndCodeAndIsUsedFalseAndIsExpiredFalse(String email, String code);

    @Modifying
    @Query("UPDATE OtpCode o SET o.isExpired = true WHERE o.userId = :userId AND o.isUsed = false AND o.isExpired = false")
    void expireAllUnusedCodesForUser(Long userId);

    @Modifying
    @Query("UPDATE OtpCode o SET o.isExpired = true WHERE o.expiresAt < :now AND o.isUsed = false AND o.isExpired = false")
    int expireOldCodes(LocalDateTime now);

    List<OtpCode> findByUserIdOrderByCreatedAtDesc(Long userId);
}
