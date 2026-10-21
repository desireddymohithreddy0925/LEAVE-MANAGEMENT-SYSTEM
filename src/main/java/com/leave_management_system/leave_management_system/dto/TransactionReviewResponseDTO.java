package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.ReviewStatus;
import com.leave_management_system.leave_management_system.entity.RiskLevel;
import com.leave_management_system.leave_management_system.entity.TransactionReview;
import com.leave_management_system.leave_management_system.entity.TransactionType;

import java.time.LocalDateTime;

public class TransactionReviewResponseDTO {

    private Long id;
    private TransactionType transactionType;
    private String entityType;
    private Long entityId;
    private Long employeeId;
    private String employeeName;
    private String departmentName;
    private Long actorId;
    private String actorEmail;
    private RiskLevel riskLevel;
    private Integer riskScore;
    private String riskReason;
    private ReviewStatus reviewStatus;
    private Long reviewerId;
    private String reviewerEmail;
    private String reviewNotes;
    private String resolution;
    private String detailsJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime reviewedAt;

    public TransactionReviewResponseDTO() {
    }

    public static TransactionReviewResponseDTO fromEntity(TransactionReview entity) {
        if (entity == null) {
            return null;
        }

        TransactionReviewResponseDTO dto = new TransactionReviewResponseDTO();
        dto.setId(entity.getId());
        dto.setTransactionType(entity.getTransactionType());
        dto.setEntityType(entity.getEntityType());
        dto.setEntityId(entity.getEntityId());

        if (entity.getEmployee() != null) {
            dto.setEmployeeId(entity.getEmployee().getId());
            dto.setEmployeeName(entity.getEmployee().getFirstName() + " " + entity.getEmployee().getLastName());
            if (entity.getEmployee().getDepartment() != null) {
                dto.setDepartmentName(entity.getEmployee().getDepartment().getName());
            }
        }

        if (entity.getActor() != null) {
            dto.setActorId(entity.getActor().getId());
        }
        dto.setActorEmail(entity.getActorEmail());

        dto.setRiskLevel(entity.getRiskLevel());
        dto.setRiskScore(entity.getRiskScore());
        dto.setRiskReason(entity.getRiskReason());
        dto.setReviewStatus(entity.getReviewStatus());

        if (entity.getReviewer() != null) {
            dto.setReviewerId(entity.getReviewer().getId());
        }
        dto.setReviewerEmail(entity.getReviewerEmail());
        dto.setReviewNotes(entity.getReviewNotes());
        dto.setResolution(entity.getResolution());
        dto.setDetailsJson(entity.getDetailsJson());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setReviewedAt(entity.getReviewedAt());

        return dto;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public void setActorEmail(String actorEmail) {
        this.actorEmail = actorEmail;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(Integer riskScore) {
        this.riskScore = riskScore;
    }

    public String getRiskReason() {
        return riskReason;
    }

    public void setRiskReason(String riskReason) {
        this.riskReason = riskReason;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(ReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewerEmail() {
        return reviewerEmail;
    }

    public void setReviewerEmail(String reviewerEmail) {
        this.reviewerEmail = reviewerEmail;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
