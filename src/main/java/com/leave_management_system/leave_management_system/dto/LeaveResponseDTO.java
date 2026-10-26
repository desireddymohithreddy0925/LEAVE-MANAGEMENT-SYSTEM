package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.LeaveRequest;
import com.leave_management_system.leave_management_system.entity.LeaveStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class LeaveResponseDTO {
    private Long id;
    private EmployeeResponseDTO employee;
    private LeaveTypeResponseDTO leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private LeaveStatus status;
    private String rejectionReason;
    private LocalDateTime appliedAt;
    private Long requestedDays;
    private boolean requiresDualApproval;
    private String managerApprovalStatus;
    private String managerApprovedByEmail;
    private LocalDateTime managerApprovedAt;
    private String hrApprovalStatus;
    private String hrApprovedByEmail;
    private LocalDateTime hrApprovedAt;
    private java.util.List<LeaveSignoffResponseDTO> signoffs = new java.util.ArrayList<>();

    public static LeaveResponseDTO fromEntity(LeaveRequest request) {
        if (request == null) return null;
        LeaveResponseDTO dto = new LeaveResponseDTO();
        dto.setId(request.getId());
        dto.setEmployee(EmployeeResponseDTO.fromEntity(request.getEmployee()));
        dto.setLeaveType(LeaveTypeResponseDTO.fromEntity(request.getLeaveType()));
        dto.setStartDate(request.getStartDate());
        dto.setEndDate(request.getEndDate());
        dto.setReason(request.getReason());
        dto.setStatus(request.getStatus());
        dto.setRejectionReason(request.getRejectionReason());
        dto.setAppliedAt(request.getAppliedAt());
        dto.setRequestedDays(calculateWorkingDays(request.getStartDate(), request.getEndDate()));
        dto.setRequiresDualApproval(request.isRequiresDualApproval());
        dto.setManagerApprovalStatus(request.getManagerApprovalStatus());
        if (request.getManagerApprovedBy() != null) {
            dto.setManagerApprovedByEmail(request.getManagerApprovedBy().getEmail());
        }
        dto.setManagerApprovedAt(request.getManagerApprovedAt());
        dto.setHrApprovalStatus(request.getHrApprovalStatus());
        if (request.getHrApprovedBy() != null) {
            dto.setHrApprovedByEmail(request.getHrApprovedBy().getEmail());
        }
        dto.setHrApprovedAt(request.getHrApprovedAt());
        if (request.getSignoffs() != null) {
            dto.setSignoffs(request.getSignoffs().stream()
                    .map(LeaveSignoffResponseDTO::fromEntity)
                    .collect(java.util.stream.Collectors.toList()));
        }
        return dto;
    }

    private static long calculateWorkingDays(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) return 0;
        long workingDays = 0;
        LocalDate date = startDate;
        while (!date.isAfter(endDate)) {
            java.time.DayOfWeek day = date.getDayOfWeek();
            if (day != java.time.DayOfWeek.SATURDAY && day != java.time.DayOfWeek.SUNDAY) {
                workingDays++;
            }
            date = date.plusDays(1);
        }
        return workingDays;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public EmployeeResponseDTO getEmployee() { return employee; }
    public void setEmployee(EmployeeResponseDTO employee) { this.employee = employee; }
    public LeaveTypeResponseDTO getLeaveType() { return leaveType; }
    public void setLeaveType(LeaveTypeResponseDTO leaveType) { this.leaveType = leaveType; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LeaveStatus getStatus() { return status; }
    public void setStatus(LeaveStatus status) { this.status = status; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
    public Long getRequestedDays() { return requestedDays; }
    public void setRequestedDays(Long requestedDays) { this.requestedDays = requestedDays; }
    public boolean isRequiresDualApproval() { return requiresDualApproval; }
    public void setRequiresDualApproval(boolean requiresDualApproval) { this.requiresDualApproval = requiresDualApproval; }
    public String getManagerApprovalStatus() { return managerApprovalStatus; }
    public void setManagerApprovalStatus(String managerApprovalStatus) { this.managerApprovalStatus = managerApprovalStatus; }
    public String getManagerApprovedByEmail() { return managerApprovedByEmail; }
    public void setManagerApprovedByEmail(String managerApprovedByEmail) { this.managerApprovedByEmail = managerApprovedByEmail; }
    public LocalDateTime getManagerApprovedAt() { return managerApprovedAt; }
    public void setManagerApprovedAt(LocalDateTime managerApprovedAt) { this.managerApprovedAt = managerApprovedAt; }
    public String getHrApprovalStatus() { return hrApprovalStatus; }
    public void setHrApprovalStatus(String hrApprovalStatus) { this.hrApprovalStatus = hrApprovalStatus; }
    public String getHrApprovedByEmail() { return hrApprovedByEmail; }
    public void setHrApprovedByEmail(String hrApprovedByEmail) { this.hrApprovedByEmail = hrApprovedByEmail; }
    public LocalDateTime getHrApprovedAt() { return hrApprovedAt; }
    public void setHrApprovedAt(LocalDateTime hrApprovedAt) { this.hrApprovedAt = hrApprovedAt; }
    public java.util.List<LeaveSignoffResponseDTO> getSignoffs() { return signoffs; }
    public void setSignoffs(java.util.List<LeaveSignoffResponseDTO> signoffs) { this.signoffs = signoffs; }
}
