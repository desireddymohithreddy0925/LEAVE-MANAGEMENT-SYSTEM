package com.leave_management_system.leave_management_system.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_requests", indexes = {
    @Index(name = "idx_leave_requests_employee_id", columnList = "employee_id"),
    @Index(name = "idx_leave_requests_status", columnList = "status")
})
public class LeaveRequest {

    private static final String TABLE_NAME = "leave_requests";
    private static final String EMPLOYEE_ID_COLUMN = "employee_id";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = EMPLOYEE_ID_COLUMN, nullable = false)
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "requires_dual_approval", nullable = false)
    private boolean requiresDualApproval = false;

    @Column(name = "manager_approval_status", length = 30)
    private String managerApprovalStatus = "PENDING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_approved_by")
    private User managerApprovedBy;

    @Column(name = "manager_approved_at")
    private LocalDateTime managerApprovedAt;

    @Column(name = "hr_approval_status", length = 30)
    private String hrApprovalStatus = "PENDING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hr_approved_by")
    private User hrApprovedBy;

    @Column(name = "hr_approved_at")
    private LocalDateTime hrApprovedAt;

    @OneToMany(mappedBy = "leaveRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<LeaveRequestSignoff> signoffs = new java.util.ArrayList<>();

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        this.appliedAt = LocalDateTime.now();
    }

    public LeaveRequest() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LeaveStatus getStatus() {
        return status;
    }

    public void setStatus(LeaveStatus status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public boolean isRequiresDualApproval() {
        return requiresDualApproval;
    }

    public void setRequiresDualApproval(boolean requiresDualApproval) {
        this.requiresDualApproval = requiresDualApproval;
    }

    public String getManagerApprovalStatus() {
        return managerApprovalStatus;
    }

    public void setManagerApprovalStatus(String managerApprovalStatus) {
        this.managerApprovalStatus = managerApprovalStatus;
    }

    public User getManagerApprovedBy() {
        return managerApprovedBy;
    }

    public void setManagerApprovedBy(User managerApprovedBy) {
        this.managerApprovedBy = managerApprovedBy;
    }

    public LocalDateTime getManagerApprovedAt() {
        return managerApprovedAt;
    }

    public void setManagerApprovedAt(LocalDateTime managerApprovedAt) {
        this.managerApprovedAt = managerApprovedAt;
    }

    public String getHrApprovalStatus() {
        return hrApprovalStatus;
    }

    public void setHrApprovalStatus(String hrApprovalStatus) {
        this.hrApprovalStatus = hrApprovalStatus;
    }

    public User getHrApprovedBy() {
        return hrApprovedBy;
    }

    public void setHrApprovedBy(User hrApprovedBy) {
        this.hrApprovedBy = hrApprovedBy;
    }

    public LocalDateTime getHrApprovedAt() {
        return hrApprovedAt;
    }

    public void setHrApprovedAt(LocalDateTime hrApprovedAt) {
        this.hrApprovedAt = hrApprovedAt;
    }

    public java.util.List<LeaveRequestSignoff> getSignoffs() {
        return signoffs;
    }

    public void setSignoffs(java.util.List<LeaveRequestSignoff> signoffs) {
        this.signoffs = signoffs;
    }
}