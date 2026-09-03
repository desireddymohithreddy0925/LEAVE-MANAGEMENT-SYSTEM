package com.leave_management_system.leave_management_system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class LeaveBalanceRequestDTO {

    @NotNull(message = "Employee ID is required")
    @Positive(message = "Employee ID must be positive")
    private Long employeeId;

    @NotNull(message = "Leave Type ID is required")
    @Positive(message = "Leave Type ID must be positive")
    private Long leaveTypeId;

    @NotNull(message = "Available days is required")
    @Min(value = 0, message = "Available days cannot be negative")
    @Max(value = 365, message = "Available days cannot exceed 365")
    private Integer availableDays;

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public Long getLeaveTypeId() { return leaveTypeId; }
    public void setLeaveTypeId(Long leaveTypeId) { this.leaveTypeId = leaveTypeId; }

    public Integer getAvailableDays() { return availableDays; }
    public void setAvailableDays(Integer availableDays) { this.availableDays = availableDays; }
}
