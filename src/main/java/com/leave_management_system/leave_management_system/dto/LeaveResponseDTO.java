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
        return dto;
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
}
