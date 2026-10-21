package com.leave_management_system.leave_management_system.repository;

import com.leave_management_system.leave_management_system.entity.ReviewStatus;
import com.leave_management_system.leave_management_system.entity.RiskLevel;
import com.leave_management_system.leave_management_system.entity.TransactionReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionReviewRepository extends JpaRepository<TransactionReview, Long>, JpaSpecificationExecutor<TransactionReview> {

    long countByReviewStatus(ReviewStatus reviewStatus);

    long countByRiskLevel(RiskLevel riskLevel);

    long countByRiskLevelAndReviewStatus(RiskLevel riskLevel, ReviewStatus reviewStatus);

    Page<TransactionReview> findByEmployeeId(Long employeeId, Pageable pageable);

    Page<TransactionReview> findByActorId(Long actorId, Pageable pageable);

    Page<TransactionReview> findByActorEmail(String actorEmail, Pageable pageable);
}
