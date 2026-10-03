package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.EmployeeResponseDTO;
import com.leave_management_system.leave_management_system.dto.LeaveResponseDTO;
import com.leave_management_system.leave_management_system.entity.Employee;
import com.leave_management_system.leave_management_system.security.SecurityAuthorizationService;
import com.leave_management_system.leave_management_system.service.EmployeeService;
import com.leave_management_system.leave_management_system.service.LeaveRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/managers")
@Tag(name = "Manager API", description = "Endpoints for manager specific operations")
public class ManagerController {

    private final EmployeeService employeeService;
    private final LeaveRequestService leaveRequestService;
    private final SecurityAuthorizationService securityService;

    public ManagerController(EmployeeService employeeService, LeaveRequestService leaveRequestService, SecurityAuthorizationService securityService) {
        this.employeeService = employeeService;
        this.leaveRequestService = leaveRequestService;
        this.securityService = securityService;
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'HR', 'ADMIN')")
    @GetMapping("/me/team")
    @Operation(summary = "Get manager's team", description = "Returns a list of all employees in the authenticated manager's team.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list")
    })
    public List<EmployeeResponseDTO> getMyTeam(Authentication authentication) {
        Employee manager = securityService.getAuthenticatedEmployee(authentication)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is not linked to an employee record"));
        return employeeService.getManagerTeam(manager.getId());
    }

    @PreAuthorize("hasAnyRole('MANAGER', 'HR', 'ADMIN')")
    @GetMapping("/me/leave-requests")
    @Operation(summary = "Get manager's team leave requests", description = "Returns a list of all leave requests from employees in the authenticated manager's team.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list")
    })
    public List<LeaveResponseDTO> getMyTeamLeaveRequests(Authentication authentication) {
        Employee manager = securityService.getAuthenticatedEmployee(authentication)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is not linked to an employee record"));
        return leaveRequestService.getTeamLeaveRequests(manager.getId());
    }
}
