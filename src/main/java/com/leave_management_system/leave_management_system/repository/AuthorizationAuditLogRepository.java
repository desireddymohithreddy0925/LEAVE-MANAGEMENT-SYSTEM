package com.leave_management_system.leave_management_system.repository;

import com.leave_management_system.leave_management_system.entity.AuthorizationAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthorizationAuditLogRepository extends JpaRepository<AuthorizationAuditLog, Long>, JpaSpecificationExecutor<AuthorizationAuditLog> {

    Page<AuthorizationAuditLog> findByUserIdOrderByTimestampDesc(Long userId, Pageable pageable);

    Page<AuthorizationAuditLog> findByEmailOrderByTimestampDesc(String email, Pageable pageable);

    long countByDecision(String decision);

    long countByEmailAndDecision(String email, String decision);
}
