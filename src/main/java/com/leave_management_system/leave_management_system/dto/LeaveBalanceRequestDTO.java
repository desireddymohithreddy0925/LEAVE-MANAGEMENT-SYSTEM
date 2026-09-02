package com.leave_management_system.leave_management_system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class LeaveBalanceRequestDTO {

    @NotNull(message = "Employee ID is required")
    private Long employeeId;

    @NotNull(message = "Leave Type ID is required")
    private Long leaveTypeId;

    @NotNull(message = "Available days is required")
    @Min(value = 0, message = "Available days cannot be negative")
    private Integer availableDays;

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public Long getLeaveTypeId() { return leaveTypeId; }
    public void setLeaveTypeId(Long leaveTypeId) { this.leaveTypeId = leaveTypeId; }

    public Integer getAvailableDays() { return availableDays; }
    public void setAvailableDays(Integer availableDays) { this.availableDays = availableDays; }
}
