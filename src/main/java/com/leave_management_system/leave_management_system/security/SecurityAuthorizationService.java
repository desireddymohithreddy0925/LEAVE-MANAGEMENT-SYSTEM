package com.leave_management_system.leave_management_system.security;

import com.leave_management_system.leave_management_system.entity.Department;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.entity.LeaveRequest;
import com.leave_management_system.leave_management_system.repository.EmployeeRepository;
import com.leave_management_system.leave_management_system.repository.LeaveRequestRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service("securityService")
public class SecurityAuthorizationService {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public SecurityAuthorizationService(EmployeeRepository employeeRepository, LeaveRequestRepository leaveRequestRepository) {
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    public Optional<Employee> getAuthenticatedEmployee(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String email = authentication.getName();
        return employeeRepository.findByUserEmail(email);
    }

    public boolean isSelf(Authentication authentication, Long targetEmployeeId) {
        return getAuthenticatedEmployee(authentication)
                .map(emp -> emp.getId().equals(targetEmployeeId))
                .orElse(false);
    }

    public boolean isManagerOfEmployee(Authentication authentication, Long targetEmployeeId) {
        return getAuthenticatedEmployee(authentication)
                .map(manager -> isManagerOfEmployee(manager.getId(), targetEmployeeId))
                .orElse(false);
    }

    public boolean isManagerOfEmployee(Long managerId, Long targetEmployeeId) {
        if (managerId == null || targetEmployeeId == null || managerId.equals(targetEmployeeId)) {
            return false;
        }

        return employeeRepository.findById(targetEmployeeId)
                .map(targetEmp -> {
                    // 1. Direct manager check
                    if (targetEmp.getManager() != null && targetEmp.getManager().getId().equals(managerId)) {
                        return true;
                    }

                    // 2. Department manager check
                    Department dept = targetEmp.getDepartment();
                    if (dept != null && dept.getManager() != null && dept.getManager().getId().equals(managerId)) {
                        return true;
                    }

                    // 3. Multi-level reporting hierarchy traversal
                    Employee current = targetEmp.getManager();
                    java.util.Set<Long> visited = new java.util.HashSet<>();
                    while (current != null && visited.add(current.getId())) {
                        if (current.getId().equals(managerId)) {
                            return true;
                        }
                        current = current.getManager();
                    }

                    return false;
                })
                .orElse(false);
    }

    public boolean canViewManagerTeam(Authentication authentication, Long targetManagerId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        boolean isGlobalAdminOrHr = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));
        boolean hasEmployeeView = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("EMPLOYEE_VIEW"));
        if (isGlobalAdminOrHr && hasEmployeeView) {
            return true;
        }

        boolean hasTeamView = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("LEAVE_VIEW_TEAM"));
        if (hasTeamView) {
            return getAuthenticatedEmployee(authentication)
                    .map(emp -> emp.getId().equals(targetManagerId))
                    .orElse(false);
        }

        return false;
    }

    public boolean canViewEmployee(Authentication authentication, Long targetEmployeeId) {
        boolean isGlobalAdminOrHr = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));
        if (isGlobalAdminOrHr) return true;

        return isSelf(authentication, targetEmployeeId) || isManagerOfEmployee(authentication, targetEmployeeId);
    }

    public boolean canManageLeaveBalance(Authentication authentication, Long targetEmployeeId) {
        boolean hasGlobalManage = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("LEAVE_BALANCE_MANAGE"));
        if (hasGlobalManage) return true;

        return isManagerOfEmployee(authentication, targetEmployeeId);
    }

    public boolean canApproveLeaveRequest(Authentication authentication, Long leaveRequestId) {
        boolean hasPermission = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("LEAVE_APPROVE"));
        if (!hasPermission) return false;

        boolean isGlobalScope = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));
        if (isGlobalScope) return true;

        Optional<LeaveRequest> leaveRequest = leaveRequestRepository.findById(leaveRequestId);
        if (leaveRequest.isEmpty()) {
            return false;
        }
        
        Long targetEmployeeId = leaveRequest.get().getEmployee().getId();
        return isManagerOfEmployee(authentication, targetEmployeeId);
    }

    public boolean canRejectLeaveRequest(Authentication authentication, Long leaveRequestId) {
        boolean hasPermission = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("LEAVE_REJECT"));
        if (!hasPermission) return false;

        boolean isGlobalScope = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));
        if (isGlobalScope) return true;

        Optional<LeaveRequest> leaveRequest = leaveRequestRepository.findById(leaveRequestId);
        if (leaveRequest.isEmpty()) {
            return false;
        }
        
        Long targetEmployeeId = leaveRequest.get().getEmployee().getId();
        return isManagerOfEmployee(authentication, targetEmployeeId);
    }

    public boolean canViewLeaveRequest(Authentication authentication, Long leaveRequestId) {
        boolean hasGlobalView = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("LEAVE_VIEW_ALL"));
        if (hasGlobalView) return true;

        Optional<LeaveRequest> leaveRequest = leaveRequestRepository.findById(leaveRequestId);
        if (leaveRequest.isEmpty()) {
            return false;
        }

        Long targetEmployeeId = leaveRequest.get().getEmployee().getId();
        return isSelf(authentication, targetEmployeeId) || isManagerOfEmployee(authentication, targetEmployeeId);
    }

    public boolean canCancelLeaveRequest(Authentication authentication, Long leaveRequestId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        boolean isGlobalScope = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));
        if (isGlobalScope) return true;

        boolean hasCancelPermission = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("LEAVE_CANCEL"));
        if (!hasCancelPermission) return false;

        Optional<LeaveRequest> leaveRequest = leaveRequestRepository.findById(leaveRequestId);
        if (leaveRequest.isEmpty()) {
            return false;
        }

        Long targetEmployeeId = leaveRequest.get().getEmployee().getId();
        return isSelf(authentication, targetEmployeeId);
    }
}
