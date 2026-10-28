package com.leave_management_system.leave_management_system.repository;

import com.leave_management_system.leave_management_system.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByIdempotencyKeyAndUserEmail(String idempotencyKey, String userEmail);

    void deleteByExpiresAtBefore(LocalDateTime dateTime);

    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.expiresAt <= :dateTime")
    int deleteExpiredKeys(@Param("dateTime") LocalDateTime dateTime);
}
