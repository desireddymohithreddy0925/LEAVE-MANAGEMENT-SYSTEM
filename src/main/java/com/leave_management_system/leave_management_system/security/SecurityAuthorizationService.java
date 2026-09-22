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

    private Optional<Employee> getAuthenticatedEmployee(Authentication authentication) {
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
                .map(manager -> {
                    return employeeRepository.findById(targetEmployeeId)
                            .map(targetEmp -> {
                                Department dept = targetEmp.getDepartment();
                                return dept != null && dept.getManager() != null && dept.getManager().getId().equals(manager.getId());
                            })
                            .orElse(false);
                })
                .orElse(false);
    }

    public boolean canViewEmployee(Authentication authentication, Long targetEmployeeId) {
        boolean isHRorAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));
        if (isHRorAdmin) return true;

        return isSelf(authentication, targetEmployeeId) || isManagerOfEmployee(authentication, targetEmployeeId);
    }

    public boolean canManageLeaveBalance(Authentication authentication, Long targetEmployeeId) {
        boolean isHRorAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));
        if (isHRorAdmin) return true;

        return isManagerOfEmployee(authentication, targetEmployeeId);
    }

    public boolean canManageLeaveRequest(Authentication authentication, Long leaveRequestId) {
        boolean isHRorAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));
        if (isHRorAdmin) return true;

        Optional<LeaveRequest> leaveRequest = leaveRequestRepository.findById(leaveRequestId);
        if (leaveRequest.isEmpty()) {
            return false;
        }
        
        Long targetEmployeeId = leaveRequest.get().getEmployee().getId();
        return isManagerOfEmployee(authentication, targetEmployeeId);
    }

    public boolean canViewLeaveRequest(Authentication authentication, Long leaveRequestId) {
        boolean isHRorAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_HR"));
        if (isHRorAdmin) return true;

        Optional<LeaveRequest> leaveRequest = leaveRequestRepository.findById(leaveRequestId);
        if (leaveRequest.isEmpty()) {
            return false;
        }

        Long targetEmployeeId = leaveRequest.get().getEmployee().getId();
        return isSelf(authentication, targetEmployeeId) || isManagerOfEmployee(authentication, targetEmployeeId);
    }
}
