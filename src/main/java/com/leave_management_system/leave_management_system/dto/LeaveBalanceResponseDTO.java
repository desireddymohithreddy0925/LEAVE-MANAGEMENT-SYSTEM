package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.LeaveBalance;

public class LeaveBalanceResponseDTO {
    private Long id;
    private EmployeeResponseDTO employee;
    private LeaveTypeResponseDTO leaveType;
    private Integer year;
    private Integer total;
    private Integer used;
    private Integer available;

    public static LeaveBalanceResponseDTO fromEntity(LeaveBalance balance) {
        if (balance == null) return null;
        LeaveBalanceResponseDTO dto = new LeaveBalanceResponseDTO();
        dto.setId(balance.getId());
        dto.setEmployee(EmployeeResponseDTO.fromEntity(balance.getEmployee()));
        dto.setLeaveType(LeaveTypeResponseDTO.fromEntity(balance.getLeaveType()));
        dto.setYear(balance.getYear());
        dto.setTotal(balance.getTotal());
        dto.setUsed(balance.getUsed());
        dto.setAvailable(balance.getAvailable());
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EmployeeResponseDTO getEmployee() { return employee; }
    public void setEmployee(EmployeeResponseDTO employee) { this.employee = employee; }

    public LeaveTypeResponseDTO getLeaveType() { return leaveType; }
    public void setLeaveType(LeaveTypeResponseDTO leaveType) { this.leaveType = leaveType; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }
    public Integer getUsed() { return used; }
    public void setUsed(Integer used) { this.used = used; }
    public Integer getAvailable() { return available; }
    public void setAvailable(Integer available) { this.available = available; }
}
