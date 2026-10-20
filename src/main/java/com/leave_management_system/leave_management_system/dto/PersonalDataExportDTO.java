package com.leave_management_system.leave_management_system.dto;

import java.time.LocalDateTime;
import java.util.List;

public class PersonalDataExportDTO {

    private LocalDateTime exportTimestamp;
    private EmployeeResponseDTO employee;
    private UserResponseDTO user;
    private List<LeaveResponseDTO> leaveRequests;
    private List<LeaveBalanceResponseDTO> leaveBalances;
    private List<LoginHistoryResponseDTO> recentLoginHistory;
    private List<AuthorizationAuditResponseDTO> recentAuthorizationAudits;

    public PersonalDataExportDTO() {}

    public LocalDateTime getExportTimestamp() {
        return exportTimestamp;
    }

    public void setExportTimestamp(LocalDateTime exportTimestamp) {
        this.exportTimestamp = exportTimestamp;
    }

    public EmployeeResponseDTO getEmployee() {
        return employee;
    }

    public void setEmployee(EmployeeResponseDTO employee) {
        this.employee = employee;
    }

    public UserResponseDTO getUser() {
        return user;
    }

    public void setUser(UserResponseDTO user) {
        this.user = user;
    }

    public List<LeaveResponseDTO> getLeaveRequests() {
        return leaveRequests;
    }

    public void setLeaveRequests(List<LeaveResponseDTO> leaveRequests) {
        this.leaveRequests = leaveRequests;
    }

    public List<LeaveBalanceResponseDTO> getLeaveBalances() {
        return leaveBalances;
    }

    public void setLeaveBalances(List<LeaveBalanceResponseDTO> leaveBalances) {
        this.leaveBalances = leaveBalances;
    }

    public List<LoginHistoryResponseDTO> getRecentLoginHistory() {
        return recentLoginHistory;
    }

    public void setRecentLoginHistory(List<LoginHistoryResponseDTO> recentLoginHistory) {
        this.recentLoginHistory = recentLoginHistory;
    }

    public List<AuthorizationAuditResponseDTO> getRecentAuthorizationAudits() {
        return recentAuthorizationAudits;
    }

    public void setRecentAuthorizationAudits(List<AuthorizationAuditResponseDTO> recentAuthorizationAudits) {
        this.recentAuthorizationAudits = recentAuthorizationAudits;
    }
}
