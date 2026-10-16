package com.leave_management_system.leave_management_system.controller;

import com.leave_management_system.leave_management_system.dto.EmployeeResponseDTO;
import com.leave_management_system.leave_management_system.dto.LeaveResponseDTO;
import com.leave_management_system.leave_management_system.dto.TeamHierarchyDTO;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/managers")
@Tag(name = "Manager API", description = "Endpoints for manager specific operations")
@org.springframework.validation.annotation.Validated
public class ManagerController {

    private final EmployeeService employeeService;
    private final LeaveRequestService leaveRequestService;
    private final SecurityAuthorizationService securityService;

    public ManagerController(EmployeeService employeeService, LeaveRequestService leaveRequestService, SecurityAuthorizationService securityService) {
        this.employeeService = employeeService;
        this.leaveRequestService = leaveRequestService;
        this.securityService = securityService;
    }

    @PreAuthorize("hasAuthority('LEAVE_VIEW_TEAM')")
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

    @PreAuthorize("hasAuthority('LEAVE_VIEW_TEAM')")
    @GetMapping({"/me/team-hierarchy", "/me/hierarchy"})
    @Operation(summary = "Get manager's team hierarchy tree", description = "Returns the team hierarchy tree of the authenticated manager.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved team hierarchy")
    })
    public TeamHierarchyDTO getMyTeamHierarchy(Authentication authentication) {
        Employee manager = securityService.getAuthenticatedEmployee(authentication)
                .orElseThrow(() -> new IllegalStateException("Authenticated user is not linked to an employee record"));
        return employeeService.getTeamHierarchy(manager.getId());
    }

    @PreAuthorize("@securityService.canViewManagerTeam(authentication, #id)")
    @GetMapping("/{id}/team")
    @Operation(summary = "Get manager team by ID", description = "Returns a list of employees for the specified manager. Managers can only view their own team.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Cannot view another manager's team"),
            @ApiResponse(responseCode = "404", description = "Manager not found")
    })
    public List<EmployeeResponseDTO> getManagerTeamById(@PathVariable Long id) {
        return employeeService.getManagerTeam(id);
    }

    @PreAuthorize("@securityService.canViewManagerTeam(authentication, #id)")
    @GetMapping({"/{id}/team-hierarchy", "/{id}/hierarchy"})
    @Operation(summary = "Get manager team hierarchy by ID", description = "Returns the team hierarchy tree for the specified manager. Managers can only view their own team.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved team hierarchy"),
            @ApiResponse(responseCode = "403", description = "Forbidden: Cannot view another manager's team"),
            @ApiResponse(responseCode = "404", description = "Manager not found")
    })
    public TeamHierarchyDTO getManagerHierarchyById(@PathVariable Long id) {
        return employeeService.getTeamHierarchy(id);
    }

    @PreAuthorize("hasAuthority('LEAVE_VIEW_TEAM')")
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
