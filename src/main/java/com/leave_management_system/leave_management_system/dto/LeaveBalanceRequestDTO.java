package com.leave_management_system.leave_management_system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public class LeaveBalanceRequestDTO {

    @Positive(message = "Employee ID must be positive")
    private Long employeeId;

    @Positive(message = "Leave Type ID must be positive")
    private Long leaveTypeId;

    /**
     * Available leave days.
     * On balance creation, this must match total days if both are provided.
     * On update, used + available cannot exceed total days.
     */
    @Min(value = 0, message = "Available days cannot be negative")
    @Max(value = 365, message = "Available days cannot exceed 365")
    private Integer available;

    @Min(value = 1900, message = "Year must be valid")
    @Max(value = 2100, message = "Year must be valid")
    private Integer year;

    /**
     * Total leave quota for the year.
     * If omitted on creation, defaults to the leave type's default days.
     */
    @Min(value = 0, message = "Total days cannot be negative")
    @Max(value = 365, message = "Total days cannot exceed 365")
    private Integer total;

    public LeaveBalanceRequestDTO() {
    }

    public LeaveBalanceRequestDTO(Long employeeId, Long leaveTypeId, Integer available) {
        this.employeeId = employeeId;
        this.leaveTypeId = leaveTypeId;
        this.available = available;
    }

    public LeaveBalanceRequestDTO(Long employeeId, Long leaveTypeId, Integer available, Integer year, Integer total) {
        this.employeeId = employeeId;
        this.leaveTypeId = leaveTypeId;
        this.available = available;
        this.year = year;
        this.total = total;
    }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public Long getLeaveTypeId() { return leaveTypeId; }
    public void setLeaveTypeId(Long leaveTypeId) { this.leaveTypeId = leaveTypeId; }

    public Integer getAvailable() { return available; }
    public void setAvailable(Integer available) { this.available = available; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }
}
