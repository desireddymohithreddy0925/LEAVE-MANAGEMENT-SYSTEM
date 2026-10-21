package com.leave_management_system.leave_management_system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leave_management_system.leave_management_system.dto.*;
import com.leave_management_system.leave_management_system.entity.*;
import com.leave_management_system.leave_management_system.exception.ResourceNotFoundException;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.TransactionReviewRepository;
import com.leave_management_system.leave_management_system.repository.UserRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class TransactionReviewService {

    private static final Logger log = LoggerFactory.getLogger(TransactionReviewService.class);

    private final TransactionReviewRepository transactionReviewRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final ObjectMapper objectMapper;

    public TransactionReviewService(TransactionReviewRepository transactionReviewRepository,
                                  UserRepository userRepository,
                                  EmployeeRepository employeeRepository) {
        this.transactionReviewRepository = transactionReviewRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Transactional
    public TransactionReview recordLeaveRequest(LeaveRequest leaveRequest, User actor, String actorEmail) {
        try {
            TransactionReview review = new TransactionReview();
            review.setTransactionType(TransactionType.LEAVE_REQUEST);
            review.setEntityType("LeaveRequest");
            review.setEntityId(leaveRequest.getId());
            review.setEmployee(leaveRequest.getEmployee());
            review.setActor(actor);
            review.setActorEmail(resolveActorEmail(actor, actorEmail));
            review.setCreatedAt(LocalDateTime.now());

            LocalDate startDate = leaveRequest.getStartDate();
            LocalDate endDate = leaveRequest.getEndDate();
            LocalDate today = LocalDate.now();
            long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

            RiskLevel riskLevel;
            int riskScore;
            String riskReason;
            ReviewStatus reviewStatus;
            String resolution = null;

            if (startDate.isBefore(today)) {
                long daysPast = ChronoUnit.DAYS.between(startDate, today);
                if (daysPast > 7) {
                    riskLevel = RiskLevel.CRITICAL;
                    riskScore = 85;
                    riskReason = "Retroactive leave application submitted " + daysPast + " days in the past";
                    reviewStatus = ReviewStatus.FLAGGED;
                } else {
                    riskLevel = RiskLevel.HIGH;
                    riskScore = 65;
                    riskReason = "Retroactive leave application submitted past start date (" + daysPast + " days ago)";
                    reviewStatus = ReviewStatus.FLAGGED;
                }
            } else if (totalDays > 10) {
                riskLevel = RiskLevel.HIGH;
                riskScore = 60;
                riskReason = "Extended leave request duration (" + totalDays + " days) exceeding 10 days";
                reviewStatus = ReviewStatus.FLAGGED;
            } else if (totalDays >= 5) {
                riskLevel = RiskLevel.MEDIUM;
                riskScore = 30;
                riskReason = "Standard multi-day leave request (" + totalDays + " days)";
                reviewStatus = ReviewStatus.PENDING_REVIEW;
            } else {
                riskLevel = RiskLevel.LOW;
                riskScore = 10;
                riskReason = "Routine short-term leave request (" + totalDays + " days)";
                reviewStatus = ReviewStatus.REVIEWED;
                resolution = "AUTO_CLEARED_ROUTINE";
                review.setReviewedAt(LocalDateTime.now());
            }

            review.setRiskLevel(riskLevel);
            review.setRiskScore(riskScore);
            review.setRiskReason(riskReason);
            review.setReviewStatus(reviewStatus);
            review.setResolution(resolution);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("startDate", startDate.toString());
            details.put("endDate", endDate.toString());
            details.put("totalDays", totalDays);
            if (leaveRequest.getLeaveType() != null) {
                details.put("leaveTypeId", leaveRequest.getLeaveType().getId());
                details.put("leaveTypeName", leaveRequest.getLeaveType().getName());
            }
            details.put("reason", leaveRequest.getReason());
            review.setDetailsJson(safeJson(details));

            return transactionReviewRepository.save(review);
        } catch (Exception e) {
            log.warn("Failed to record leave request transaction review: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    public TransactionReview recordLeaveApproval(LeaveRequest leaveRequest, User actor, String actorEmail) {
        try {
            TransactionReview review = new TransactionReview();
            review.setTransactionType(TransactionType.LEAVE_APPROVAL);
            review.setEntityType("LeaveRequest");
            review.setEntityId(leaveRequest.getId());
            review.setEmployee(leaveRequest.getEmployee());
            review.setActor(actor);
            review.setActorEmail(resolveActorEmail(actor, actorEmail));
            review.setCreatedAt(LocalDateTime.now());

            LocalDate startDate = leaveRequest.getStartDate();
            LocalDate endDate = leaveRequest.getEndDate();
            long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

            RiskLevel riskLevel;
            int riskScore;
            String riskReason;
            ReviewStatus reviewStatus;
            String resolution = null;

            if (totalDays > 10 || startDate.isBefore(LocalDate.now())) {
                riskLevel = RiskLevel.MEDIUM;
                riskScore = 40;
                riskReason = "Managerial approval of elevated-risk leave (" + totalDays + " days)";
                reviewStatus = ReviewStatus.PENDING_REVIEW;
            } else {
                riskLevel = RiskLevel.LOW;
                riskScore = 15;
                riskReason = "Standard managerial leave approval (" + totalDays + " days)";
                reviewStatus = ReviewStatus.REVIEWED;
                resolution = "AUTO_CLEARED_ROUTINE";
                review.setReviewedAt(LocalDateTime.now());
            }

            review.setRiskLevel(riskLevel);
            review.setRiskScore(riskScore);
            review.setRiskReason(riskReason);
            review.setReviewStatus(reviewStatus);
            review.setResolution(resolution);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("startDate", startDate.toString());
            details.put("endDate", endDate.toString());
            details.put("totalDays", totalDays);
            details.put("status", "APPROVED");
            review.setDetailsJson(safeJson(details));

            return transactionReviewRepository.save(review);
        } catch (Exception e) {
            log.warn("Failed to record leave approval transaction review: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    public TransactionReview recordLeaveRejection(LeaveRequest leaveRequest, String reason, User actor, String actorEmail) {
        try {
            TransactionReview review = new TransactionReview();
            review.setTransactionType(TransactionType.LEAVE_REJECTION);
            review.setEntityType("LeaveRequest");
            review.setEntityId(leaveRequest.getId());
            review.setEmployee(leaveRequest.getEmployee());
            review.setActor(actor);
            review.setActorEmail(resolveActorEmail(actor, actorEmail));
            review.setCreatedAt(LocalDateTime.now());

            review.setRiskLevel(RiskLevel.LOW);
            review.setRiskScore(15);
            review.setRiskReason("Managerial rejection: " + (reason != null ? reason : "No reason provided"));
            review.setReviewStatus(ReviewStatus.REVIEWED);
            review.setResolution("AUTO_CLEARED_ROUTINE");
            review.setReviewedAt(LocalDateTime.now());

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("status", "REJECTED");
            details.put("rejectionReason", reason);
            review.setDetailsJson(safeJson(details));

            return transactionReviewRepository.save(review);
        } catch (Exception e) {
            log.warn("Failed to record leave rejection transaction review: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    public TransactionReview recordLeaveCancellation(LeaveRequest leaveRequest, User actor, String actorEmail) {
        return recordLeaveCancellation(leaveRequest, leaveRequest != null && leaveRequest.getStatus() == LeaveStatus.APPROVED, actor, actorEmail);
    }

    @Transactional
    public TransactionReview recordLeaveCancellation(LeaveRequest leaveRequest, boolean wasApproved, User actor, String actorEmail) {
        try {
            TransactionReview review = new TransactionReview();
            review.setTransactionType(TransactionType.LEAVE_CANCELLATION);
            review.setEntityType("LeaveRequest");
            review.setEntityId(leaveRequest.getId());
            review.setEmployee(leaveRequest.getEmployee());
            review.setActor(actor);
            review.setActorEmail(resolveActorEmail(actor, actorEmail));
            review.setCreatedAt(LocalDateTime.now());

            RiskLevel riskLevel;
            int riskScore;
            String riskReason;
            ReviewStatus reviewStatus;
            String resolution = null;

            if (wasApproved) {
                riskLevel = RiskLevel.MEDIUM;
                riskScore = 45;
                riskReason = "Post-approval leave cancellation with quota balance restoration";
                reviewStatus = ReviewStatus.PENDING_REVIEW;
            } else {
                riskLevel = RiskLevel.LOW;
                riskScore = 10;
                riskReason = "Cancellation of pending leave request";
                reviewStatus = ReviewStatus.REVIEWED;
                resolution = "AUTO_CLEARED_ROUTINE";
                review.setReviewedAt(LocalDateTime.now());
            }

            review.setRiskLevel(riskLevel);
            review.setRiskScore(riskScore);
            review.setRiskReason(riskReason);
            review.setReviewStatus(reviewStatus);
            review.setResolution(resolution);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("priorStatus", wasApproved ? "APPROVED" : "PENDING");
            details.put("status", "CANCELLED");
            review.setDetailsJson(safeJson(details));

            return transactionReviewRepository.save(review);
        } catch (Exception e) {
            log.warn("Failed to record leave cancellation transaction review: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    public TransactionReview recordBalanceCreation(LeaveBalance balance, User actor, String actorEmail) {
        try {
            TransactionReview review = new TransactionReview();
            review.setTransactionType(TransactionType.BALANCE_CREATION);
            review.setEntityType("LeaveBalance");
            review.setEntityId(balance.getId());
            review.setEmployee(balance.getEmployee());
            review.setActor(actor);
            review.setActorEmail(resolveActorEmail(actor, actorEmail));
            review.setCreatedAt(LocalDateTime.now());

            review.setRiskLevel(RiskLevel.LOW);
            review.setRiskScore(10);
            review.setRiskReason("Standard leave balance creation (" + balance.getTotal() + " days)");
            review.setReviewStatus(ReviewStatus.REVIEWED);
            review.setResolution("AUTO_CLEARED_ROUTINE");
            review.setReviewedAt(LocalDateTime.now());

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("year", balance.getYear());
            details.put("total", balance.getTotal());
            details.put("available", balance.getAvailable());
            if (balance.getLeaveType() != null) {
                details.put("leaveTypeName", balance.getLeaveType().getName());
            }
            review.setDetailsJson(safeJson(details));

            return transactionReviewRepository.save(review);
        } catch (Exception e) {
            log.warn("Failed to record balance creation transaction review: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    public TransactionReview recordBalanceAdjustment(LeaveBalance balance, int oldTotal, int oldAvailable, User actor, String actorEmail) {
        try {
            TransactionReview review = new TransactionReview();
            review.setTransactionType(TransactionType.BALANCE_ADJUSTMENT);
            review.setEntityType("LeaveBalance");
            review.setEntityId(balance.getId());
            review.setEmployee(balance.getEmployee());
            review.setActor(actor);
            review.setActorEmail(resolveActorEmail(actor, actorEmail));
            review.setCreatedAt(LocalDateTime.now());

            int deltaTotal = Math.abs(balance.getTotal() - oldTotal);
            int deltaAvailable = Math.abs(balance.getAvailable() - oldAvailable);
            int maxDelta = Math.max(deltaTotal, deltaAvailable);

            RiskLevel riskLevel;
            int riskScore;
            String riskReason;
            ReviewStatus reviewStatus;

            if (maxDelta > 15 || (balance.getAvailable() == 0 && oldAvailable > 0)) {
                riskLevel = RiskLevel.CRITICAL;
                riskScore = 85;
                riskReason = "Major manual balance override (delta: " + maxDelta + " days, new available: " + balance.getAvailable() + ")";
                reviewStatus = ReviewStatus.FLAGGED;
            } else if (maxDelta > 5) {
                riskLevel = RiskLevel.HIGH;
                riskScore = 65;
                riskReason = "Significant manual balance adjustment (> 5 days, delta: " + maxDelta + ")";
                reviewStatus = ReviewStatus.FLAGGED;
            } else {
                riskLevel = RiskLevel.MEDIUM;
                riskScore = 35;
                riskReason = "Routine manual balance adjustment (delta: " + maxDelta + " days)";
                reviewStatus = ReviewStatus.PENDING_REVIEW;
            }

            review.setRiskLevel(riskLevel);
            review.setRiskScore(riskScore);
            review.setRiskReason(riskReason);
            review.setReviewStatus(reviewStatus);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("year", balance.getYear());
            details.put("oldTotal", oldTotal);
            details.put("newTotal", balance.getTotal());
            details.put("oldAvailable", oldAvailable);
            details.put("newAvailable", balance.getAvailable());
            details.put("deltaDays", maxDelta);
            if (balance.getLeaveType() != null) {
                details.put("leaveTypeName", balance.getLeaveType().getName());
            }
            review.setDetailsJson(safeJson(details));

            return transactionReviewRepository.save(review);
        } catch (Exception e) {
            log.warn("Failed to record balance adjustment transaction review: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    public TransactionReview recordGenericTransaction(TransactionType type, String entityType, Long entityId,
                                                      Employee employee, User actor, String actorEmail,
                                                      RiskLevel riskLevel, int riskScore, String riskReason,
                                                      ReviewStatus reviewStatus, Map<String, Object> details) {
        try {
            TransactionReview review = new TransactionReview();
            review.setTransactionType(type != null ? type : TransactionType.OTHER);
            review.setEntityType(entityType != null ? entityType : "Generic");
            review.setEntityId(entityId != null ? entityId : 0L);
            review.setEmployee(employee);
            review.setActor(actor);
            review.setActorEmail(resolveActorEmail(actor, actorEmail));
            review.setRiskLevel(riskLevel != null ? riskLevel : RiskLevel.LOW);
            review.setRiskScore(riskScore);
            review.setRiskReason(riskReason);
            review.setReviewStatus(reviewStatus != null ? reviewStatus : ReviewStatus.PENDING_REVIEW);
            review.setCreatedAt(LocalDateTime.now());
            if (details != null) {
                review.setDetailsJson(safeJson(details));
            }
            return transactionReviewRepository.save(review);
        } catch (Exception e) {
            log.warn("Failed to record generic transaction review: {}", e.getMessage());
            return null;
        }
    }

    @Transactional(readOnly = true)
    public Page<TransactionReviewResponseDTO> searchTransactionReviews(
            TransactionType transactionType,
            ReviewStatus reviewStatus,
            RiskLevel riskLevel,
            Long employeeId,
            String actorEmail,
            Long reviewerId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {

        Specification<TransactionReview> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (transactionType != null) {
                predicates.add(cb.equal(root.get("transactionType"), transactionType));
            }
            if (reviewStatus != null) {
                predicates.add(cb.equal(root.get("reviewStatus"), reviewStatus));
            }
            if (riskLevel != null) {
                predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            }
            if (employeeId != null) {
                predicates.add(cb.equal(root.get("employee").get("id"), employeeId));
            }
            if (actorEmail != null && !actorEmail.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("actorEmail")), "%" + actorEmail.trim().toLowerCase() + "%"));
            }
            if (reviewerId != null) {
                predicates.add(cb.equal(root.get("reviewer").get("id"), reviewerId));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return transactionReviewRepository.findAll(spec, pageable).map(TransactionReviewResponseDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    public TransactionReviewResponseDTO getTransactionReviewById(Long id) {
        TransactionReview review = transactionReviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction review not found with id: " + id));
        return TransactionReviewResponseDTO.fromEntity(review);
    }

    @Transactional(readOnly = true)
    public TransactionReviewSummaryDTO getSummaryMetrics() {
        TransactionReviewSummaryDTO summary = new TransactionReviewSummaryDTO();
        long total = transactionReviewRepository.count();
        summary.setTotalTransactions(total);

        long pending = transactionReviewRepository.countByReviewStatus(ReviewStatus.PENDING_REVIEW);
        long underReview = transactionReviewRepository.countByReviewStatus(ReviewStatus.UNDER_REVIEW);
        long reviewed = transactionReviewRepository.countByReviewStatus(ReviewStatus.REVIEWED);
        long flagged = transactionReviewRepository.countByReviewStatus(ReviewStatus.FLAGGED);
        long resolved = transactionReviewRepository.countByReviewStatus(ReviewStatus.RESOLVED);

        summary.setTotalPendingReview(pending);
        summary.setTotalUnderReview(underReview);
        summary.setTotalReviewed(reviewed);
        summary.setTotalFlagged(flagged);
        summary.setTotalResolved(resolved);

        summary.setTotalLowRisk(transactionReviewRepository.countByRiskLevel(RiskLevel.LOW));
        summary.setTotalMediumRisk(transactionReviewRepository.countByRiskLevel(RiskLevel.MEDIUM));
        summary.setTotalHighRisk(transactionReviewRepository.countByRiskLevel(RiskLevel.HIGH));
        summary.setTotalCriticalRisk(transactionReviewRepository.countByRiskLevel(RiskLevel.CRITICAL));

        long pendingHigh = transactionReviewRepository.countByRiskLevelAndReviewStatus(RiskLevel.HIGH, ReviewStatus.PENDING_REVIEW)
                + transactionReviewRepository.countByRiskLevelAndReviewStatus(RiskLevel.HIGH, ReviewStatus.FLAGGED)
                + transactionReviewRepository.countByRiskLevelAndReviewStatus(RiskLevel.CRITICAL, ReviewStatus.PENDING_REVIEW)
                + transactionReviewRepository.countByRiskLevelAndReviewStatus(RiskLevel.CRITICAL, ReviewStatus.FLAGGED);
        summary.setPendingHighRiskCount(pendingHigh);

        if (total > 0) {
            double rate = ((double) (reviewed + resolved) / total) * 100.0;
            summary.setReviewCompletionRate(Math.round(rate * 100.0) / 100.0);
        } else {
            summary.setReviewCompletionRate(0.0);
        }

        return summary;
    }

    @Transactional(readOnly = true)
    public Page<TransactionReviewResponseDTO> getMyTransactionReviews(String email, Pageable pageable) {
        Specification<TransactionReview> spec = (root, query, cb) -> {
            Predicate actorPredicate = cb.equal(cb.lower(root.get("actorEmail")), email.toLowerCase());
            Join<TransactionReview, Employee> employeeJoin = root.join("employee", JoinType.LEFT);
            Join<Employee, User> userJoin = employeeJoin.join("user", JoinType.LEFT);
            Predicate employeeUserPredicate = cb.equal(cb.lower(userJoin.get("email")), email.toLowerCase());
            return cb.or(actorPredicate, employeeUserPredicate);
        };
        return transactionReviewRepository.findAll(spec, pageable).map(TransactionReviewResponseDTO::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<TransactionReviewResponseDTO> getEmployeeTransactionReviews(Long employeeId, Pageable pageable) {
        return transactionReviewRepository.findByEmployeeId(employeeId, pageable).map(TransactionReviewResponseDTO::fromEntity);
    }

    @Transactional
    public TransactionReviewResponseDTO assignReviewer(Long reviewId, AssignReviewerDTO dto, String currentUsername) {
        TransactionReview review = transactionReviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction review not found with id: " + reviewId));

        User reviewer = null;
        if (dto != null && dto.getReviewerId() != null) {
            reviewer = userRepository.findById(dto.getReviewerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Reviewer user not found with id: " + dto.getReviewerId()));
        } else if (dto != null && dto.getReviewerEmail() != null && !dto.getReviewerEmail().trim().isEmpty()) {
            reviewer = userRepository.findByEmail(dto.getReviewerEmail().trim()).orElse(null);
        } else if (currentUsername != null) {
            reviewer = userRepository.findByEmail(currentUsername).orElse(null);
        }

        review.setReviewer(reviewer);
        review.setReviewerEmail(reviewer != null ? reviewer.getEmail() : (dto != null ? dto.getReviewerEmail() : currentUsername));
        if (review.getReviewStatus() == ReviewStatus.PENDING_REVIEW || review.getReviewStatus() == ReviewStatus.FLAGGED) {
            review.setReviewStatus(ReviewStatus.UNDER_REVIEW);
        }
        review.setUpdatedAt(LocalDateTime.now());
        return TransactionReviewResponseDTO.fromEntity(transactionReviewRepository.save(review));
    }

    @Transactional
    public TransactionReviewResponseDTO submitReview(Long reviewId, ReviewDecisionDTO dto, String currentUsername) {
        TransactionReview review = transactionReviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction review not found with id: " + reviewId));

        ReviewStatus targetStatus = dto.getDecision() != null ? dto.getDecision() : ReviewStatus.REVIEWED;
        review.setReviewStatus(targetStatus);
        review.setReviewNotes(dto.getNotes());
        if (dto.getResolution() != null && !dto.getResolution().trim().isEmpty()) {
            review.setResolution(dto.getResolution().trim());
        } else if (review.getResolution() == null) {
            review.setResolution("APPROVED_VALIDATED");
        }

        if (review.getReviewer() == null && currentUsername != null) {
            userRepository.findByEmail(currentUsername).ifPresent(u -> {
                review.setReviewer(u);
                review.setReviewerEmail(u.getEmail());
            });
        }

        LocalDateTime now = LocalDateTime.now();
        review.setReviewedAt(now);
        review.setUpdatedAt(now);
        return TransactionReviewResponseDTO.fromEntity(transactionReviewRepository.save(review));
    }

    @Transactional
    public TransactionReviewResponseDTO flagTransaction(Long reviewId, FlagTransactionDTO dto, String currentUsername) {
        TransactionReview review = transactionReviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction review not found with id: " + reviewId));

        review.setReviewStatus(ReviewStatus.FLAGGED);
        String existingReason = review.getRiskReason() != null ? review.getRiskReason() + " | " : "";
        String newReason = existingReason + "Auditor Flag: " + dto.getReason();
        if (newReason.length() > 500) {
            newReason = newReason.substring(0, 497) + "...";
        }
        review.setRiskReason(newReason);

        if (dto.getNotes() != null && !dto.getNotes().trim().isEmpty()) {
            review.setReviewNotes(dto.getNotes().trim());
        }
        review.setUpdatedAt(LocalDateTime.now());
        return TransactionReviewResponseDTO.fromEntity(transactionReviewRepository.save(review));
    }

    @Transactional
    public TransactionReviewResponseDTO resolveReview(Long reviewId, ResolveReviewDTO dto, String currentUsername) {
        TransactionReview review = transactionReviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction review not found with id: " + reviewId));

        review.setReviewStatus(ReviewStatus.RESOLVED);
        review.setResolution(dto.getResolution());
        review.setReviewNotes(dto.getNotes());

        if (review.getReviewer() == null && currentUsername != null) {
            userRepository.findByEmail(currentUsername).ifPresent(u -> {
                review.setReviewer(u);
                review.setReviewerEmail(u.getEmail());
            });
        }

        LocalDateTime now = LocalDateTime.now();
        review.setReviewedAt(now);
        review.setUpdatedAt(now);
        return TransactionReviewResponseDTO.fromEntity(transactionReviewRepository.save(review));
    }

    private String resolveActorEmail(User actor, String actorEmail) {
        if (actor != null && actor.getEmail() != null) {
            return actor.getEmail();
        }
        return actorEmail != null ? actorEmail : "system";
    }

    private String safeJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            return "{}";
        }
    }
}
