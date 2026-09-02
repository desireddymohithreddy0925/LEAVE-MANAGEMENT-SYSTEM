package com.leave_management_system.leave_management_system.dto;

import com.leave_management_system.leave_management_system.entity.LeaveBalance;

public class LeaveBalanceResponseDTO {
    private Long id;
    private EmployeeResponseDTO employee;
    private LeaveTypeResponseDTO leaveType;
    private Integer availableDays;

    public static LeaveBalanceResponseDTO fromEntity(LeaveBalance balance) {
        if (balance == null) return null;
        LeaveBalanceResponseDTO dto = new LeaveBalanceResponseDTO();
        dto.setId(balance.getId());
        dto.setEmployee(EmployeeResponseDTO.fromEntity(balance.getEmployee()));
        dto.setLeaveType(LeaveTypeResponseDTO.fromEntity(balance.getLeaveType()));
        dto.setAvailableDays(balance.getAvailableDays());
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EmployeeResponseDTO getEmployee() { return employee; }
    public void setEmployee(EmployeeResponseDTO employee) { this.employee = employee; }

    public LeaveTypeResponseDTO getLeaveType() { return leaveType; }
    public void setLeaveType(LeaveTypeResponseDTO leaveType) { this.leaveType = leaveType; }

    public Integer getAvailableDays() { return availableDays; }
    public void setAvailableDays(Integer availableDays) { this.availableDays = availableDays; }
}
